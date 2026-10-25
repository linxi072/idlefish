package com.idlefish.trade.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.util.DateTimeUtil;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.member.GrowthValueCalculator;
import com.idlefish.trade.member.entity.Benefit;
import com.idlefish.trade.member.entity.BenefitExchange;
import com.idlefish.trade.member.entity.UserGrowth;
import com.idlefish.trade.member.mapper.BenefitExchangeMapper;
import com.idlefish.trade.member.mapper.BenefitMapper;
import com.idlefish.trade.member.vo.BenefitExchangeVO;
import com.idlefish.trade.member.vo.BenefitVO;
import com.idlefish.trade.marketing.entity.Point;
import com.idlefish.trade.marketing.service.CouponService;
import com.idlefish.trade.marketing.service.PointService;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 权益兑换服务（ITER-F07 / REQ-07/08/12/13）。
 * <p>
 * <b>币种口径（REQ-01）</b>：兑换消耗的是<b>积分</b>（可消耗），<b>成长值仅作等级门槛</b>（不可消耗）。
 * 两者严格分离，绝不互相折算。
 * </p>
 * <p><b>原子性与防超发（REQ-07/08）</b>：单事务内依次完成
 * 「校验（等级 / 时间窗 / 每日上限 / 账户状态）→ 库存原子扣减（{@code stock > 0} CAS）
 * → 积分原子扣减（余额 CAS）→ 权益发放（券走 F-10 链路）」；
 * 任一步失败整体回滚，杜绝「扣了积分没发券」或「发了券没扣库存」。
 * 库存与积分均采用条件更新 + affected 行数判定，配合
 * {@code t_user_coupon} 唯一索引形成双保险。</p>
 */
@Service
public class BenefitExchangeService {

    private final BenefitMapper benefitMapper;
    private final BenefitExchangeMapper benefitExchangeMapper;
    private final PointService pointService;
    private final CouponService couponService;
    private final MemberLevelService memberLevelService;
    private final GrowthValueService growthValueService;
    private final NotificationService notificationService;
    private final MetricsRegistry metrics;

    public BenefitExchangeService(BenefitMapper benefitMapper,
                                  BenefitExchangeMapper benefitExchangeMapper,
                                  PointService pointService,
                                  CouponService couponService,
                                  MemberLevelService memberLevelService,
                                  GrowthValueService growthValueService,
                                  NotificationService notificationService,
                                  MetricsRegistry metrics) {
        this.benefitMapper = benefitMapper;
        this.benefitExchangeMapper = benefitExchangeMapper;
        this.pointService = pointService;
        this.couponService = couponService;
        this.memberLevelService = memberLevelService;
        this.growthValueService = growthValueService;
        this.notificationService = notificationService;
        this.metrics = metrics;
    }

    // ===== 用户端 =====

    /** 兑换中心：上架中的权益（含时间窗过滤）+ 当前用户的可兑换判定。 */
    public IPage<BenefitVO> listAvailable(Long userId, int page, int size) {
        Page<Benefit> p = new Page<>(Math.max(page, 1), Math.max(size, 1));
        IPage<Benefit> r = benefitMapper.selectPage(p, new LambdaQueryWrapper<Benefit>()
                .eq(Benefit::getStatus, Benefit.STATUS_ON_SHELF)
                .orderByAsc(Benefit::getSortOrder)
                .orderByDesc(Benefit::getCreatedAt));
        List<BenefitVO> list = r.getRecords().stream().map(b -> toVO(b, userId)).toList();
        IPage<BenefitVO> out = new Page<>(r.getCurrent(), r.getSize(), r.getTotal());
        out.setRecords(list);
        return out;
    }

    /** 权益详情（含可兑换判定）。 */
    public BenefitVO detail(Long userId, Long benefitId) {
        Benefit b = benefitMapper.selectById(benefitId);
        if (b == null) {
            throw new BizException(Code.BENEFIT_NOT_FOUND, "权益不存在");
        }
        return toVO(b, userId);
    }

    /** 我的兑换记录（分页，倒序）。 */
    public IPage<BenefitExchangeVO> myExchanges(Long userId, int page, int size) {
        Page<BenefitExchange> p = new Page<>(Math.max(page, 1), Math.max(size, 1));
        IPage<BenefitExchange> r = benefitExchangeMapper.selectPage(p, new LambdaQueryWrapper<BenefitExchange>()
                .eq(BenefitExchange::getUserId, userId)
                .orderByDesc(BenefitExchange::getCreatedAt));
        return r.convert(this::toExchangeVO);
    }

    /**
     * 权益兑换（REQ-07 全链路闭环）。
     * <p>校验失败抛出带明确原因的 {@link BizException}（REQ-08），事务整体回滚；
     * 成功返回兑换单号（BX 前缀），该单号同时作为积分流水 bizId 便于对账。</p>
     */
    @Transactional
    public String exchange(Long userId, Long benefitId) {
        if (userId == null) {
            throw new BizException(Code.UNAUTHORIZED, "请先登录");
        }
        // 1) 权益存在 / 上架 / 时间窗
        Benefit b = benefitMapper.selectById(benefitId);
        if (b == null) {
            throw new BizException(Code.BENEFIT_NOT_FOUND, "权益不存在");
        }
        if (!Benefit.STATUS_ON_SHELF.equals(b.getStatus())) {
            throw new BizException(Code.BENEFIT_OFF_SHELF, "权益已下架");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!GrowthValueCalculator.withinWindow(now, b.getOnlineAt(), b.getOfflineAt())) {
            throw new BizException(Code.BENEFIT_EXPIRED, "权益未开始或已过期");
        }
        // 2) 账户状态：刷分冻结 / 封禁期间不可兑换（REQ-03）
        UserGrowth acc = growthValueService.account(userId);
        if (acc != null && acc.isFrozen()) {
            throw new BizException(Code.GROWTH_FROZEN, "账户成长值通道已冻结，暂不可兑换权益");
        }
        // 3) 等级门槛（REQ-07；成长值决定等级，此处只读不消耗）
        String userLevel = memberLevelService.getLevel(userId).getLevelCode();
        if (!GrowthValueCalculator.levelSatisfied(memberLevelService.listTiers(), userLevel, b.getMinLevelCode())) {
            throw new BizException(Code.BENEFIT_LEVEL_NOT_ENOUGH, "会员等级不足，无法兑换该权益");
        }
        // 4) 每日兑换上限（REQ-08）
        int daily = b.getDailyLimit() == null ? 0 : b.getDailyLimit();
        int todayExchanged = todayExchanged(userId, benefitId);
        if (daily > 0 && todayExchanged >= daily) {
            throw new BizException(Code.BENEFIT_EXCHANGE_LIMIT, "已达该权益每日兑换上限（每日限兑 " + daily + " 次）");
        }
        // 5) 积分充足预检：给出明确提示，避免走到原子扣减才失败
        long cost = b.getCostPoints() == null ? 0 : b.getCostPoints();
        Point pt = pointService.balance(userId);
        long available = pt.getBalance() == null ? 0 : pt.getBalance();
        if (available < cost) {
            throw new BizException(Code.POINT_NOT_ENOUGH, "积分不足（需 " + cost + "，当前 " + available + "）");
        }
        // 6) 库存原子扣减（REQ-08 双保险之一：stock > 0 条件更新）
        int stockRows = benefitMapper.update(null, new LambdaUpdateWrapper<Benefit>()
                .eq(Benefit::getId, benefitId)
                .gt(Benefit::getStock, 0)
                .setSql("stock = stock - 1"));
        if (stockRows == 0) {
            throw new BizException(Code.BENEFIT_STOCK_NOT_ENOUGH, "权益库存不足");
        }
        // 7) 积分原子扣减（REQ-07；余额 CAS，不足抛错整体回滚）
        String exchangeNo = IdGenerator.benefitExchangeNo();
        pointService.consumeForBenefit(userId, cost, exchangeNo, b.getBenefitName());
        // 8) 发放权益（券走 F-10 成熟链路；发放失败整体回滚）
        Long couponId = null;
        if (Benefit.TYPE_COUPON.equals(b.getBenefitType())) {
            if (b.getCouponId() == null) {
                throw new BizException(Code.BIZ_ERROR, "权益未绑定券模板，请联系运营");
            }
            couponId = b.getCouponId();
            couponService.grantCoupon(userId, b.getCouponId());
        }
        // 9) 兑换记录
        BenefitExchange ex = new BenefitExchange();
        ex.setExchangeNo(exchangeNo);
        ex.setBenefitId(benefitId);
        ex.setBenefitCode(b.getBenefitCode());
        ex.setUserId(userId);
        ex.setCostPoints(cost);
        ex.setBenefitType(b.getBenefitType());
        ex.setCouponId(couponId);
        ex.setStatus(BenefitExchange.STATUS_SUCCESS);
        ex.setRemark("权益兑换成功");
        benefitExchangeMapper.insert(ex);

        metrics.increment("growth.benefit.exchange");
        notify(userId, "权益兑换成功", "您已兑换「" + b.getBenefitName() + "」，消耗 " + cost + " 积分");
        return exchangeNo;
    }

    // ===== 运营后台（REQ-12 上下架与过期回收）=====

    /** 权益配置清单（全部，按排序）。 */
    public List<Benefit> adminList() {
        return benefitMapper.selectList(new LambdaQueryWrapper<Benefit>()
                .orderByAsc(Benefit::getSortOrder)
                .orderByDesc(Benefit::getCreatedAt));
    }

    /** 新增 / 更新权益配置（按 id 是否存在 upsert）。 */
    @Transactional
    public Long save(Benefit benefit) {
        if (benefit == null) {
            throw new BizException(Code.PARAM_INVALID, "权益配置不能为空");
        }
        if (benefit.getBenefitCode() == null || benefit.getBenefitCode().isBlank()) {
            throw new BizException(Code.PARAM_MISSING, "权益编码必填");
        }
        if (Benefit.TYPE_COUPON.equals(benefit.getBenefitType()) && benefit.getCouponId() == null) {
            throw new BizException(Code.PARAM_MISSING, "券类型权益必须绑定券模板");
        }
        if (benefit.getId() == null) {
            benefitMapper.insert(benefit);
        } else {
            benefitMapper.updateById(benefit);
        }
        return benefit.getId();
    }

    /** 删除权益配置。 */
    @Transactional
    public void delete(Long id) {
        benefitMapper.deleteById(id);
    }

    /** 上下架（REQ-12）。 */
    @Transactional
    public boolean updateStatus(Long id, String status) {
        if (id == null || status == null || status.isBlank()) {
            return false;
        }
        return benefitMapper.update(null, new LambdaUpdateWrapper<Benefit>()
                .eq(Benefit::getId, id)
                .set(Benefit::getStatus, status)) > 0;
    }

    /**
     * 过期回收（REQ-12）：把已越过下架时间仍为上架状态的权益统一下架，返回回收条数。
     * 由定时任务调用；回收仅改状态，不影响已发放的权益与历史兑换记录。
     */
    @Transactional
    public int recoverExpired() {
        return benefitMapper.update(null, new LambdaUpdateWrapper<Benefit>()
                .eq(Benefit::getStatus, Benefit.STATUS_ON_SHELF)
                .isNotNull(Benefit::getOfflineAt)
                .lt(Benefit::getOfflineAt, LocalDateTime.now())
                .set(Benefit::getStatus, Benefit.STATUS_OFF_SHELF));
    }

    /** 运营后台兑换记录（分页，可按用户过滤）——REQ-09 对账。 */
    public IPage<BenefitExchangeVO> adminExchanges(Long userId, int page, int size) {
        Page<BenefitExchange> p = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<BenefitExchange> q = new LambdaQueryWrapper<>();
        if (userId != null) {
            q.eq(BenefitExchange::getUserId, userId);
        }
        q.orderByDesc(BenefitExchange::getCreatedAt);
        IPage<BenefitExchange> r = benefitExchangeMapper.selectPage(p, q);
        return r.convert(this::toExchangeVO);
    }

    // ===== 内部 =====

    /** 今日已兑换次数（REQ-08 每日上限统计口径：自然日）。 */
    private int todayExchanged(Long userId, Long benefitId) {
        LocalDateTime from = LocalDate.now().atStartOfDay();
        Long cnt = benefitExchangeMapper.selectCount(new LambdaQueryWrapper<BenefitExchange>()
                .eq(BenefitExchange::getUserId, userId)
                .eq(BenefitExchange::getBenefitId, benefitId)
                .eq(BenefitExchange::getStatus, BenefitExchange.STATUS_SUCCESS)
                .ge(BenefitExchange::getCreatedAt, from));
        return cnt == null ? 0 : cnt.intValue();
    }

    private BenefitVO toVO(Benefit b, Long userId) {
        BenefitVO v = new BenefitVO();
        v.setId(b.getId());
        v.setBenefitCode(b.getBenefitCode());
        v.setBenefitName(b.getBenefitName());
        v.setBenefitType(b.getBenefitType());
        v.setDescription(b.getDescription());
        v.setCostPoints(b.getCostPoints());
        v.setMinLevelCode(b.getMinLevelCode());
        v.setVoucherAmountFen(b.getVoucherAmountFen());
        v.setStock(b.getStock());
        v.setDailyLimit(b.getDailyLimit());
        v.setOnlineAt(b.getOnlineAt() == null ? null : b.getOnlineAt().format(DateTimeUtil.FMT));
        v.setOfflineAt(b.getOfflineAt() == null ? null : b.getOfflineAt().format(DateTimeUtil.FMT));
        v.setSortOrder(b.getSortOrder());

        long cost = b.getCostPoints() == null ? 0 : b.getCostPoints();
        int stock = b.getStock() == null ? 0 : b.getStock();
        boolean inWindow = GrowthValueCalculator.withinWindow(LocalDateTime.now(), b.getOnlineAt(), b.getOfflineAt());
        boolean onShelf = Benefit.STATUS_ON_SHELF.equals(b.getStatus());
        boolean levelOk = false;
        long available = 0L;
        int today = 0;
        if (userId != null) {
            String userLevel = memberLevelService.getLevel(userId).getLevelCode();
            levelOk = GrowthValueCalculator.levelSatisfied(memberLevelService.listTiers(), userLevel, b.getMinLevelCode());
            Point pt = pointService.balance(userId);
            available = pt.getBalance() == null ? 0 : pt.getBalance();
            today = todayExchanged(userId, b.getId());
        }
        boolean dailyOk = b.getDailyLimit() == null || b.getDailyLimit() <= 0 || today < b.getDailyLimit();
        v.setLevelSatisfied(levelOk);
        v.setPointsEnough(available >= cost);
        v.setStockEnough(stock > 0);
        v.setTodayExchanged(today);
        v.setExchangeable(onShelf && inWindow && levelOk && available >= cost && stock > 0 && dailyOk);
        return v;
    }

    private BenefitExchangeVO toExchangeVO(BenefitExchange e) {
        BenefitExchangeVO v = new BenefitExchangeVO();
        v.setId(e.getId());
        v.setExchangeNo(e.getExchangeNo());
        v.setBenefitId(e.getBenefitId());
        v.setBenefitCode(e.getBenefitCode());
        Benefit b = benefitMapper.selectById(e.getBenefitId());
        v.setBenefitName(b == null ? e.getBenefitCode() : b.getBenefitName());
        v.setUserId(e.getUserId());
        v.setCostPoints(e.getCostPoints());
        v.setBenefitType(e.getBenefitType());
        v.setCouponId(e.getCouponId());
        v.setStatus(e.getStatus());
        v.setRemark(e.getRemark());
        v.setCreatedAt(e.getCreatedAt() == null ? null : e.getCreatedAt().format(DateTimeUtil.FMT));
        return v;
    }

    private void notify(Long userId, String title, String content) {
        try {
            notificationService.notify(userId, NotificationType.COUPON_CLAIMED, String.valueOf(userId), title, content);
        } catch (Exception ignored) {
            // 通知 best-effort
        }
    }
}
