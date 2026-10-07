package com.idlefish.trade.trust.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.enums.CreditLevel;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.dispute.entity.Dispute;
import com.idlefish.trade.dispute.mapper.DisputeMapper;
import com.idlefish.trade.inspection.entity.InspectionReport;
import com.idlefish.trade.inspection.mapper.InspectionReportMapper;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.service.ItemService;
import com.idlefish.trade.risk.entity.RiskEvent;
import com.idlefish.trade.risk.mapper.RiskEventMapper;
import com.idlefish.trade.trade.entity.Logistics;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.mapper.LogisticsMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.service.LogisticService;
import com.idlefish.trade.trade.service.LogisticsTimelineBuilder;
import com.idlefish.trade.trade.vo.OrderLogisticsVO;
import com.idlefish.trade.trust.TrustRules;
import com.idlefish.trade.trust.vo.GuaranteeTerms;
import com.idlefish.trade.trust.vo.TrustItemView;
import com.idlefish.trade.trust.vo.TrustOrderView;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.service.CreditService;
import com.idlefish.trade.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 信任体系可视化聚合服务（U02）：把分散的信任信息聚合为商品详情页 / 订单详情页视图。
 *
 * <p>数据来源（只读聚合，不持有业务状态）：
 * <ul>
 *   <li>卖家信用（F-06）：{@link UserService} + {@link CreditService} + {@link OrderMapper} 成交数；</li>
 *   <li>验货标识（ITER-F02）：{@link Item#getInspectionStatus()} + {@link InspectionReportMapper} 报告快照；</li>
 *   <li>售后进度（F-17）：{@link DisputeMapper} + {@link TrustRules#buildDisputeProgress}；</li>
 *   <li>物流时间轴（物流）：{@link LogisticsMapper} + {@link LogisticService} + {@link LogisticsTimelineBuilder}；</li>
 *   <li>风控/封禁（风控/用户）：{@link RiskEventMapper} high 级事件 + {@link User#getStatus()}。</li>
 * </ul>
 *
 * <p>安全与降级约定：
 * <ul>
 *   <li>REQ-08 新卖家/零信用不显高分：信用缺失时返回 newSeller=true，由前端展示「暂无信用」；</li>
 *   <li>REQ-09 封禁/高风险：返回 riskFlag + orderRestricted=true，前端据此限制下单；</li>
 *   <li>REQ-02 验货标与报告强绑定：仅 status=PASSED 且存在报告时才带 reportNo；</li>
 *   <li>REQ-11 保障边界：virtual/forbidden 不适用担保交易，前端按 guaranteeBoundary 明示。</li>
 * </ul>
 */
@Service
public class TrustViewService {

    private static final String GUARANTEE_SUMMARY =
            "交易资金由平台担保托管：买家付款后进托管，确认收货后放款卖家";
    private static final String GUARANTEE_BOUNDARY =
            "虚拟商品、违禁品不适用担保交易；保障以平台规则与交易事实为准，不承诺 100% 无风险";

    private final ItemService itemService;
    private final UserService userService;
    private final CreditService creditService;
    private final OrderMapper orderMapper;
    private final RiskEventMapper riskEventMapper;
    private final InspectionReportMapper inspectionReportMapper;
    private final DisputeMapper disputeMapper;
    private final LogisticsMapper logisticsMapper;
    private final LogisticService logisticService;
    private final MetricsRegistry metrics;

    public TrustViewService(ItemService itemService, UserService userService, CreditService creditService,
                           OrderMapper orderMapper, RiskEventMapper riskEventMapper,
                           InspectionReportMapper inspectionReportMapper, DisputeMapper disputeMapper,
                           LogisticsMapper logisticsMapper, LogisticService logisticService,
                           MetricsRegistry metrics) {
        this.itemService = itemService;
        this.userService = userService;
        this.creditService = creditService;
        this.orderMapper = orderMapper;
        this.riskEventMapper = riskEventMapper;
        this.inspectionReportMapper = inspectionReportMapper;
        this.disputeMapper = disputeMapper;
        this.logisticsMapper = logisticsMapper;
        this.logisticService = logisticService;
        this.metrics = metrics;
    }

    /**
     * REQ-01/02/03/08/09/10/11 商品详情信任条聚合。
     *
     * @param itemId 商品 ID（不存在抛 ITEM_NOT_FOUND）
     */
    public TrustItemView itemView(Long itemId) {
        Item item = itemService.view(itemId); // 不存在抛 ITEM_NOT_FOUND
        TrustItemView view = new TrustItemView();
        view.setSellerCredit(buildSellerCredit(item.getSellerId()));
        view.setInspectionBadge(buildInspectionBadge(item));
        view.setGuaranteeSummary(GUARANTEE_SUMMARY);
        view.setGuaranteeBoundary(GUARANTEE_BOUNDARY);
        TrustItemView.RiskFlag rf = buildRiskFlag(item.getSellerId());
        view.setRiskFlag(rf);
        view.setOrderRestricted(rf.isBlocked() || rf.isUnderRiskControl());
        view.setSuggestInspection(buildSuggestInspection(item));
        metrics.increment("trust.item.view");
        return view;
    }

    /**
     * REQ-05/07 + 保障摘要：订单详情信任视图（物流时间轴 + 售后进度）。
     *
     * @param orderNo 订单号
     */
    public TrustOrderView orderView(String orderNo) {
        TrustOrderView view = new TrustOrderView();
        view.setGuaranteeSummary(GUARANTEE_SUMMARY);
        view.setGuaranteeBoundary(GUARANTEE_BOUNDARY);
        view.setLogistics(buildLogistics(orderNo));
        view.setAftersale(buildAftersale(orderNo));
        metrics.increment("trust.order.view");
        return view;
    }

    /** REQ-04 完整保障条款（可展开）。 */
    public GuaranteeTerms guaranteeTerms() {
        GuaranteeTerms t = new GuaranteeTerms();
        t.setSummary(GUARANTEE_SUMMARY);
        t.setSteps(List.of(
                "买家付款后资金由平台担保托管，不直接打给卖家",
                "卖家发货，物流全程可追踪",
                "买家确认收货后，托管资金放款给卖家",
                "如出现货不对板等问题，可在确认收货前发起维权"));
        t.setApplicableScope("适用于平台实物二手商品交易（含验货商品）");
        t.setExclusions(List.of(
                "虚拟商品（如账号、卡密）不适用担保交易",
                "违禁品 / 平台禁售品类不适用"));
        t.setNote("保障以实际交易事实与平台规则为准，平台不承诺 100% 无风险");
        return t;
    }

    // ---------- 内部聚合 ----------

    private TrustItemView.SellerCredit buildSellerCredit(Long sellerId) {
        TrustItemView.SellerCredit sc = new TrustItemView.SellerCredit();
        int score = 0;
        try {
            User u = userService.getById(sellerId);
            score = u.getCreditScore() == null ? 0 : u.getCreditScore();
        } catch (BizException ignored) {
            // 卖家不存在时不阻断，按新卖家处理
        }
        CreditLevel level = creditService.levelOf(sellerId); // 内部对 null 安全
        int deals = countSellerDeals(sellerId);
        sc.setCreditScore(score);
        sc.setCreditLevel(level == null ? "POOR" : level.name());
        sc.setCreditLevelLabel(level == null ? "较差" : level.getDesc());
        sc.setDealCount(deals);
        // REQ-08：无信用分且零成交 → 新卖家，前端展示「暂无信用」而非误导高分
        sc.setNewSeller(score <= 0 && deals == 0);
        return sc;
    }

    private TrustItemView.InspectionBadge buildInspectionBadge(Item item) {
        TrustItemView.InspectionBadge b = new TrustItemView.InspectionBadge();
        String status = item.getInspectionStatus();
        b.setStatus(status == null ? "NONE" : status);
        boolean passed = "PASSED".equals(status);
        b.setPassed(passed);
        // REQ-02 报告入口与报告快照强绑定：仅真实验货通过且存在报告时才带 reportNo
        if (passed) {
            InspectionReport r = inspectionReportMapper.selectOne(new LambdaQueryWrapper<InspectionReport>()
                    .eq(InspectionReport::getItemId, item.getId())
                    .orderByDesc(InspectionReport::getCreatedAt).last("LIMIT 1"));
            if (r != null) {
                b.setReportNo(r.getReportNo());
                b.setGrade(r.getGrade());
            }
        }
        return b;
    }

    private TrustItemView.RiskFlag buildRiskFlag(Long sellerId) {
        TrustItemView.RiskFlag rf = new TrustItemView.RiskFlag();
        boolean blocked = false;
        try {
            User u = userService.getById(sellerId);
            blocked = u.getStatus() != null && u.getStatus() == 1;
        } catch (BizException ignored) {
        }
        long risk = riskEventMapper.selectCount(new LambdaQueryWrapper<RiskEvent>()
                .eq(RiskEvent::getUserId, sellerId)
                .eq(RiskEvent::getLevel, "high"));
        rf.setBlocked(blocked);
        rf.setUnderRiskControl(risk > 0);
        if (blocked) {
            rf.setTip("该卖家账号已被封禁，交易存在风险");
        } else if (rf.isUnderRiskControl()) {
            rf.setTip("该卖家存在风控处置记录，请谨慎交易");
        }
        return rf;
    }

    private TrustItemView.SuggestInspection buildSuggestInspection(Item item) {
        TrustItemView.SuggestInspection s = new TrustItemView.SuggestInspection();
        boolean highValue = TrustRules.isHighValue(item.getPrice());
        boolean passed = "PASSED".equals(item.getInspectionStatus());
        // REQ-10 高客单且未验货 → 建议验货
        s.setSuggested(highValue && !passed);
        if (s.isSuggested()) {
            s.setReason("该商品为高客单价商品，建议验货以保障交易安全");
            s.setEntry("去验货");
        }
        return s;
    }

    private OrderLogisticsVO buildLogistics(String orderNo) {
        Logistics l = logisticsMapper.selectOne(new LambdaQueryWrapper<Logistics>().eq(Logistics::getOrderNo, orderNo));
        if (l == null || l.getLogisticsNo() == null) {
            return null; // 未发货，无时间轴
        }
        List<Map<String, String>> nodes = logisticService.track(l.getLogisticsNo());
        return LogisticsTimelineBuilder.build(nodes, l.getCompany(), l.getStatus());
    }

    private TrustOrderView.AftersaleProgress buildAftersale(String orderNo) {
        TrustOrderView.AftersaleProgress ap = new TrustOrderView.AftersaleProgress();
        List<Dispute> disputes = disputeMapper.selectList(new LambdaQueryWrapper<Dispute>()
                .eq(Dispute::getOrderNo, orderNo).orderByDesc(Dispute::getCreatedAt));
        if (disputes.isEmpty()) {
            ap.setHasDispute(false);
            return ap;
        }
        Dispute d = disputes.get(0); // 取最新工单
        ap.setHasDispute(true);
        ap.setCurrentStatus(d.getStatus());
        ap.setCurrentStatusLabel(statusLabel(d.getStatus()));
        ap.setResult(d.getResult());
        ap.setNodes(TrustRules.buildDisputeProgress(d.getStatus()));
        return ap;
    }

    private int countSellerDeals(Long sellerId) {
        long count = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getSellerId, sellerId)
                .in(Order::getStatus, List.of("COMPLETED", "CLOSED")));
        return (int) count;
    }

    private String statusLabel(String s) {
        if (s == null) {
            return "未知";
        }
        return switch (s) {
            case "PENDING" -> "待处理";
            case "SELLER_REPLIED" -> "卖家已举证";
            case "PLATFORM" -> "平台介入中";
            case "RESOLVED" -> "已裁决";
            case "CLOSED" -> "已归档";
            case "CANCELED" -> "已撤销";
            default -> s;
        };
    }
}
