package com.idlefish.trade.local.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.util.MoneyUtil;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.local.LocalFulfillmentRules;
import com.idlefish.trade.local.RecycleQuoteCalculator;
import com.idlefish.trade.local.RecycleStateMachine;
import com.idlefish.trade.local.dto.RecycleApplyRequest;
import com.idlefish.trade.local.entity.RecycleOrder;
import com.idlefish.trade.local.mapper.RecycleOrderMapper;
import com.idlefish.trade.local.vo.RecycleEstimateVO;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.publish.ReferencePriceProvider;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.FundFlowBuilder;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 上门回收服务（ITER-F05）：预估 / 申请 / 取消 / 揽收 / 质检调价 / 用户确认 / 放款。
 * 金额恒「分」，状态机见 {@link RecycleStateMachine}。
 *
 * <p>关键合规与资金纪律：
 * <ul>
 *   <li>REQ-03：仅待上门阶段可取消，且不产生任何费用。</li>
 *   <li>REQ-05：用户拒绝报价即 {@code REJECTED}，系统不做自动成交、不扣任何费用。</li>
 *   <li>REQ-07：放款统一走 {@link FundFlowBuilder}（direction=IN, type=RECYCLE），便于
 *       ITER-T02 对账按 bizNo 维度追平；金额上限由服务端二次封顶。</li>
 *   <li>REQ-08：{@code orgId} 用于区域机构数据隔离，后台列表须传入调用方的机构范围。</li>
 *   <li>REQ-09：城市/品类不可用一律显式失败，不静默降级。</li>
 *   <li>PRD §九：地址与联系方式在落库前脱敏。</li>
 * </ul>
 */
@Service
public class RecycleService {

    private final RecycleOrderMapper recycleOrderMapper;
    private final ItemMapper itemMapper;
    private final FundFlowMapper fundFlowMapper;
    private final IdlefishProperties props;
    private final MetricsRegistry metrics;
    private final NotificationService notificationService;
    private final Optional<ReferencePriceProvider> refPrice;

    public RecycleService(RecycleOrderMapper recycleOrderMapper, ItemMapper itemMapper,
                          FundFlowMapper fundFlowMapper, IdlefishProperties props,
                          MetricsRegistry metrics, NotificationService notificationService,
                          Optional<ReferencePriceProvider> refPrice) {
        this.recycleOrderMapper = recycleOrderMapper;
        this.itemMapper = itemMapper;
        this.fundFlowMapper = fundFlowMapper;
        this.props = props;
        this.metrics = metrics;
        this.notificationService = notificationService;
        this.refPrice = refPrice;
    }

    // ===== REQ-02 预估回收价 =====

    /**
     * 预估回收价（不下单）。
     * 参考价优先级：显式入参 → ITER-F03 估价引擎基准价；均无则 hasData=false 明确无数据。
     */
    public RecycleEstimateVO estimate(Long categoryId, Integer conditionLevel, Long referencePriceFen,
                                      String city, Long orgId) {
        IdlefishProperties.Local cfg = props.getLocal();
        if (!cfg.isEnabled()) {
            return RecycleEstimateVO.noData("同城回收服务暂未开通");
        }
        if (!LocalFulfillmentRules.isCategoryRecyclable(categoryId, cfg.getRecycleCategories())) {
            return RecycleEstimateVO.noData("该品类暂不支持回收");
        }
        if (city != null && !city.isBlank() && !LocalFulfillmentRules.isCitySupported(city, cfg.getCities())) {
            return RecycleEstimateVO.noData("所在城市暂未开通上门回收");
        }
        int level = (conditionLevel == null || conditionLevel < 1 || conditionLevel > 5) ? 3 : conditionLevel;
        long reference = 0L;
        String source = null;
        if (referencePriceFen != null && referencePriceFen > 0) {
            reference = referencePriceFen;
            source = "input";
        } else if (refPrice.isPresent()) {
            Optional<Long> base = refPrice.get().basePriceFen(categoryId, level);
            if (base.isPresent()) {
                reference = base.get();
                source = "pricing";
            }
        }
        if (reference <= 0) {
            metrics.increment("recycle.estimate.nodata");
            return RecycleEstimateVO.noData("该类目暂无可参考的成交价，暂不支持回收预估");
        }
        long estimate = RecycleQuoteCalculator.estimate(reference, level, cfg.getConditionRatio(),
                cfg.getDefaultEstimateRatio(), cfg.getMinEstimateFen());
        if (estimate <= 0) {
            return RecycleEstimateVO.noData("预估回收价过低，暂不支持上门回收");
        }
        RecycleEstimateVO v = new RecycleEstimateVO();
        v.setHasData(true);
        v.setSource(source);
        v.setReferenceFen(reference);
        v.setEstimateFen(estimate);
        v.setConditionLevel(level);
        v.setRecyclable(true);
        metrics.increment("recycle.estimate.ok");
        return v;
    }

    /** 城市 + 品类可用性（REQ-01/REQ-09/REQ-11）。 */
    public boolean recyclable(Long categoryId, String city) {
        IdlefishProperties.Local cfg = props.getLocal();
        if (!cfg.isEnabled()) {
            return false;
        }
        if (!LocalFulfillmentRules.isCategoryRecyclable(categoryId, cfg.getRecycleCategories())) {
            return false;
        }
        return LocalFulfillmentRules.isCitySupported(city, cfg.getCities());
    }

    // ===== REQ-02 提交回收申请 =====

    /**
     * 提交回收申请：生成预估价并进入「待上门取件」。
     * 无参考价不发单（明确失败），避免产生无法定价的黑盒单据。
     */
    public Long apply(Long userId, RecycleApplyRequest req) {
        IdlefishProperties.Local cfg = props.getLocal();
        if (!cfg.isEnabled()) {
            throw new BizException(Code.LOCAL_REGION_UNSUPPORTED, "同城回收服务暂未开通");
        }
        Long categoryId = req.getCategoryId();
        Integer conditionLevel = req.getConditionLevel();
        Long itemId = req.getItemId();
        String title = req.getTitle();

        // REQ-11 一键回收：以商品为准补齐类目/成色/标题快照
        if (itemId != null) {
            Item item = itemMapper.selectById(itemId);
            if (item == null) {
                throw new BizException(Code.ITEM_NOT_FOUND);
            }
            categoryId = item.getCategoryId();
            if (conditionLevel == null) {
                conditionLevel = item.getConditionLevel();
            }
            if (title == null || title.isBlank()) {
                title = item.getTitle();
            }
        }
        if (categoryId == null) {
            throw new BizException(Code.PARAM_MISSING, "缺少类目信息");
        }
        if (!LocalFulfillmentRules.isCategoryRecyclable(categoryId, cfg.getRecycleCategories())) {
            throw new BizException(Code.RECYCLE_CATEGORY_NOT_ALLOWED);
        }
        // 上门取件须校验城市可用性；自送到店同样受城市约束（门店所在城市）
        String city = req.getCity();
        if (!LocalFulfillmentRules.isCitySupported(city, cfg.getCities())) {
            throw new BizException(Code.LOCAL_REGION_UNSUPPORTED);
        }
        int level = (conditionLevel == null || conditionLevel < 1 || conditionLevel > 5) ? 3 : conditionLevel;
        // 参考价：入参 > 估价引擎
        long reference = req.getReferencePriceFen() == null ? 0L : req.getReferencePriceFen();
        if (reference <= 0 && refPrice.isPresent()) {
            Optional<Long> base = refPrice.get().basePriceFen(categoryId, level);
            if (base.isPresent()) {
                reference = base.get();
            }
        }
        if (reference <= 0) {
            throw new BizException(Code.BIZ_ERROR,
                    "该类目暂无可参考成交价，无法给出预估回收价，请稍后再试或联系客服");
        }
        long estimate = RecycleQuoteCalculator.estimate(reference, level, cfg.getConditionRatio(),
                cfg.getDefaultEstimateRatio(), cfg.getMinEstimateFen());
        if (estimate <= 0) {
            throw new BizException(Code.BIZ_ERROR, "预估回收价过低，暂不支持上门回收");
        }

        RecycleOrder r = new RecycleOrder();
        r.setRecycleNo(IdGenerator.recycleNo());
        r.setUserId(userId);
        r.setOrgId(resolveOrgId(city));
        r.setItemId(itemId);
        r.setCategoryId(categoryId);
        r.setTitle(title);
        r.setConditionLevel(level);
        r.setPickupType(LocalFulfillmentRules.PICKUP_HOME.equals(req.getPickupType())
                ? LocalFulfillmentRules.PICKUP_HOME : LocalFulfillmentRules.PICKUP_SELF);
        r.setEstimateAmount(estimate);
        r.setFinalAmount(0L);
        r.setStatus(RecycleStateMachine.PENDING_PICKUP);
        r.setProvince(req.getProvince());
        r.setCity(city);
        r.setDistrict(req.getDistrict());
        // PRD §九 脱敏后再落库
        r.setAddress(LocalFulfillmentRules.maskAddress(req.getAddress()));
        r.setContactName(req.getContactName());
        r.setContactPhone(LocalFulfillmentRules.maskPhone(req.getContactPhone()));
        r.setAppointmentAt(parseTime(req.getAppointmentAt()));
        recycleOrderMapper.insert(r);

        metrics.increment("recycle.apply");
        notifyBestEffort(userId, NotificationType.RECYCLE_APPLIED, r.getRecycleNo(),
                "回收申请已受理", "回收单 " + r.getRecycleNo() + " 已受理，预估回收价 "
                        + MoneyUtil.fenToYuan(estimate) + " 元，请等待上门取件");
        return r.getId();
    }

    // ===== REQ-03 取消 =====

    /**
     * 用户取消：仅待上门阶段允许，且此阶段不产生任何费用（REQ-03）。
     */
    public void cancel(Long userId, Long id, String reason) {
        RecycleOrder r = mustGet(id);
        assertOwner(r, userId);
        if (!RecycleStateMachine.canUserCancel(r.getStatus())) {
            throw new BizException(Code.RECYCLE_STATE_NOT_ALLOWED,
                    "物品已揽收，暂不支持直接取消，可在质检报价后选择不接受");
        }
        RecycleOrder upd = new RecycleOrder();
        upd.setId(id);
        upd.setStatus(RecycleStateMachine.CANCELED);
        upd.setCancelReason(reason == null ? "" : reason.trim());
        upd.setFinishedAt(LocalDateTime.now());
        recycleOrderMapper.updateById(upd);
        metrics.increment("recycle.cancel.nofee");
        notifyBestEffort(userId, NotificationType.RECYCLE_REJECTED, r.getRecycleNo(),
                "回收单已取消", "回收单 " + r.getRecycleNo() + " 已取消，本次取消不产生任何费用");
    }

    // ===== 运营/服务商侧推进 =====

    /** 揽收（PENDING_PICKUP → PICKED_UP）。 */
    public void markPickedUp(Long id, String operator) {
        RecycleOrder r = mustGet(id);
        transit(r, RecycleStateMachine.PICKED_UP, operator);
        RecycleOrder upd = new RecycleOrder();
        upd.setId(id);
        upd.setPickedUpAt(LocalDateTime.now());
        recycleOrderMapper.updateById(upd);
        metrics.increment("recycle.picked_up");
    }

    /** 进入质检（PICKED_UP → INSPECTING）。 */
    public void markInspecting(Long id, String operator) {
        RecycleOrder r = mustGet(id);
        transit(r, RecycleStateMachine.INSPECTING, operator);
    }

    /**
     * REQ-04 质检调价：给出最终报价，服务端二次封顶后进入待用户确认。
     * 跌幅超过阈值时需人工复核，此处打点 + 告警管理员，但不阻断流程，
     * 决策权始终留给用户（REQ-05 不得强制成交）。
     */
    public void quote(Long id, Long finalAmount, String quoteRemark, String operator) {
        RecycleOrder r = mustGet(id);
        if (!RecycleStateMachine.canQuote(r.getStatus())) {
            throw new BizException(Code.RECYCLE_STATE_NOT_ALLOWED, "当前状态不可录入质检结果");
        }
        long estimate = r.getEstimateAmount() == null ? 0L : r.getEstimateAmount();
        // 显式录入的非正数报价视为录入错误，直接拒绝；null 时回落预估价交由人工兜底
        if (finalAmount != null && finalAmount <= 0) {
            throw new BizException(Code.PARAM_INVALID, "质检报价必须大于 0");
        }
        long capped = RecycleQuoteCalculator.capFinalAmount(finalAmount, estimate);
        if (capped <= 0) {
            throw new BizException(Code.PARAM_INVALID, "质检报价无效");
        }
        if (RecycleQuoteCalculator.abnormalDrop(estimate, capped,
                props.getLocal().getMaxQuoteDropRatio())) {
            metrics.increment("recycle.quote.abnormal");
            notifyBestEffort(alertAdmin(), NotificationType.SYSTEM_ALERT, r.getRecycleNo(),
                    "回收质检异常调价待复核", "回收单 " + r.getRecycleNo() + " 质检报价跌幅超过阈值，请人工复核后再放款");
        }
        RecycleOrder upd = new RecycleOrder();
        upd.setId(id);
        upd.setFinalAmount(capped);
        upd.setQuoteRemark(quoteRemark);
        upd.setOperator(operator);
        upd.setQuotedAt(LocalDateTime.now());
        upd.setStatus(RecycleStateMachine.QUOTED);
        recycleOrderMapper.updateById(upd);
        metrics.increment("recycle.quoted");
        notifyBestEffort(r.getUserId(), NotificationType.RECYCLE_QUOTED, r.getRecycleNo(),
                "质检完成，请确认回收价", "回收单 " + r.getRecycleNo() + " 质检完成，最终回收价 "
                        + MoneyUtil.fenToYuan(capped) + " 元，请确认是否接受");
    }

    /**
     * REQ-04/REQ-05 用户对报价表态：接受 → 待放款；拒绝 → 取消退回。
     * 系统永不自动接受，也不因拒绝扣取任何费用。
     */
    public void respond(Long userId, Long id, boolean accept) {
        RecycleOrder r = mustGet(id);
        assertOwner(r, userId);
        if (!RecycleStateMachine.canRespondQuote(r.getStatus())) {
            throw new BizException(Code.RECYCLE_STATE_NOT_ALLOWED, "当前无可确认的报价");
        }
        RecycleOrder upd = new RecycleOrder();
        upd.setId(id);
        upd.setFinishedAt(LocalDateTime.now());
        if (accept) {
            upd.setStatus(RecycleStateMachine.CONFIRMED);
            recycleOrderMapper.updateById(upd);
            metrics.increment("recycle.confirmed");
            notifyBestEffort(userId, NotificationType.RECYCLE_QUOTED, r.getRecycleNo(),
                    "回收价已确认", "回收单 " + r.getRecycleNo() + " 已确认，回收款将在质检复核后到账");
        } else {
            upd.setStatus(RecycleStateMachine.REJECTED);
            upd.setCancelReason("用户拒绝质检报价，安排退回");
            recycleOrderMapper.updateById(upd);
            metrics.increment("recycle.rejected");
            notifyBestEffort(userId, NotificationType.RECYCLE_REJECTED, r.getRecycleNo(),
                    "回收单已取消并退回", "回收单 " + r.getRecycleNo() + " 已按您的选择取消，物品将退回，不收取任何费用");
        }
    }

    // ===== REQ-07 放款 =====

    /**
     * 放款：仅已确认报价的单可放款；金额以上一步封顶后的 {@code finalAmount} 为准，
     * 入 IN/RECYCLE 资金流水，供 ITER-T02 对账追溯。
     */
    public void pay(Long id, String operator) {
        RecycleOrder r = mustGet(id);
        if (!RecycleStateMachine.canPay(r.getStatus())) {
            throw new BizException(Code.RECYCLE_STATE_NOT_ALLOWED, "当前状态不可放款");
        }
        long amount = r.getFinalAmount() == null ? 0L : r.getFinalAmount();
        if (amount <= 0) {
            throw new BizException(Code.PARAM_INVALID, "回收金额无效，禁止放款");
        }
        // 幂等：已放款（存在 RECYCLE 流水）不再重复入账
        Long exist = fundFlowMapper.selectCount(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getBizNo, r.getRecycleNo()).eq(FundFlow::getType, RECYCLE_FLOW_TYPE));
        if (exist != null && exist > 0) {
            metrics.increment("recycle.pay.duplicate");
            throw new BizException(Code.RECYCLE_STATE_NOT_ALLOWED, "该回收单已放款，请勿重复操作");
        }
        FundFlow ff = FundFlowBuilder.of(r.getRecycleNo(), r.getUserId(), "IN", RECYCLE_FLOW_TYPE, amount).build();
        fundFlowMapper.insert(ff);

        RecycleOrder upd = new RecycleOrder();
        upd.setId(id);
        upd.setStatus(RecycleStateMachine.PAID);
        upd.setOperator(operator);
        upd.setPaidAt(LocalDateTime.now());
        upd.setFinishedAt(LocalDateTime.now());
        recycleOrderMapper.updateById(upd);

        metrics.increment("recycle.paid");
        notifyBestEffort(r.getUserId(), NotificationType.RECYCLE_PAID, r.getRecycleNo(),
                "回收款已到账", "回收单 " + r.getRecycleNo() + " 回收款 " + MoneyUtil.fenToYuan(amount)
                        + " 元已到账，可在钱包查看");
    }

    /** REQ-09 服务不可用：城市未开通 / 服务商异常时由运营或系统明确置为不可用。 */
    public void markUnavailable(Long id, String reason) {
        RecycleOrder r = mustGet(id);
        if (RecycleStateMachine.isTerminal(r.getStatus())) {
            return;
        }
        RecycleOrder upd = new RecycleOrder();
        upd.setId(id);
        upd.setStatus(RecycleStateMachine.UNAVAILABLE);
        upd.setCancelReason(reason == null ? "同城服务暂不可用" : reason);
        upd.setFinishedAt(LocalDateTime.now());
        recycleOrderMapper.updateById(upd);
        metrics.increment("recycle.unavailable");
        notifyBestEffort(r.getUserId(), NotificationType.RECYCLE_REJECTED, r.getRecycleNo(),
                "回收单暂不可完成", "回收单 " + r.getRecycleNo() + "：" + upd.getCancelReason() + "，不收取任何费用");
    }

    // ===== 查询 =====

    /** 我的回收单分页。 */
    public IPage<RecycleOrder> myList(Long userId, int page, int size) {
        return recycleOrderMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<RecycleOrder>()
                .eq(RecycleOrder::getUserId, userId)
                .orderByDesc(RecycleOrder::getCreatedAt));
    }

    /** 详情（仅本人可见）。 */
    public RecycleOrder detail(Long userId, Long id) {
        RecycleOrder r = mustGet(id);
        assertOwner(r, userId);
        return r;
    }

    /**
     * 后台列表：{@code orgId} 为区域机构范围（REQ-08 数据隔离）。
     * 为空表示超管视角不做机构过滤（实际能否越权由 RBAC 层保障，详见 ITER-T05）。
     */
    public IPage<RecycleOrder> adminList(int page, int size, String status, String keyword, Long orgId) {
        LambdaQueryWrapper<RecycleOrder> q = new LambdaQueryWrapper<RecycleOrder>()
                .eq(status != null && !status.isBlank(), RecycleOrder::getStatus, status)
                .eq(orgId != null, RecycleOrder::getOrgId, orgId)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(RecycleOrder::getRecycleNo, keyword)
                        .or().like(RecycleOrder::getContactPhone, keyword))
                .orderByDesc(RecycleOrder::getCreatedAt);
        return recycleOrderMapper.selectPage(new Page<>(page, size), q);
    }

    /** 后台详情。 */
    public RecycleOrder adminDetail(Long id) {
        return mustGet(id);
    }

    // ===== 内部工具 =====

    /** 资金流水类型标识：回收放款（ITER-T02 对账可按 type 扩展 RECYCLE 维度）。 */
    public static final String RECYCLE_FLOW_TYPE = "RECYCLE";

    private RecycleOrder mustGet(Long id) {
        RecycleOrder r = recycleOrderMapper.selectById(id);
        if (r == null) {
            throw new BizException(Code.RECYCLE_NOT_FOUND);
        }
        return r;
    }

    private void assertOwner(RecycleOrder r, Long userId) {
        if (userId == null || !userId.equals(r.getUserId())) {
            throw new BizException(Code.FORBIDDEN, "无权操作该回收单");
        }
    }

    private void transit(RecycleOrder r, String to, String operator) {
        if (!RecycleStateMachine.canTransition(r.getStatus(), to)) {
            throw new BizException(Code.RECYCLE_STATE_NOT_ALLOWED);
        }
        RecycleOrder upd = new RecycleOrder();
        upd.setId(r.getId());
        upd.setStatus(to);
        upd.setOperator(operator);
        recycleOrderMapper.updateById(upd);
    }

    /**
     * 区域机构解析：当前以「城市 → 机构」的最小映射打样（REQ-08）。
     * 未接入 RBAC/组织架构绑定前返回 null（表示未归属，超管可见），
     * 完整的多租户隔离由 ITER-T05 承接。
     */
    private Long resolveOrgId(String city) {
        return null;
    }

    private Long alertAdmin() {
        Long id = props.getLocal().getAlertAdminUserId();
        if (id != null) {
            return id;
        }
        return props.getNotify().getAlertAdminUserId();
    }

    private void notifyBestEffort(Long userId, NotificationType type, String bizId, String title, String content) {
        try {
            notificationService.notify(userId, type, bizId, title, content);
        } catch (Exception ignored) {
            // best-effort：触达失败不影响主流程
        }
    }

    private LocalDateTime parseTime(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(iso.trim());
        } catch (Exception e) {
            throw new BizException(Code.PARAM_INVALID, "预约时间格式不正确");
        }
    }
}
