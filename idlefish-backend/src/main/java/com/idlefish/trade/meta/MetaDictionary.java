package com.idlefish.trade.meta;

import com.idlefish.trade.meta.vo.DesignTokenVO;
import com.idlefish.trade.meta.vo.StatusGroupVO;
import com.idlefish.trade.meta.vo.StatusMetaVO;
import com.idlefish.trade.meta.vo.TermGlossaryVO;

import java.util.List;
import java.util.Map;

/**
 * 多端体验对齐（ITER-U07）元数据字典：状态枚举字典 / 业务术语表 / 设计令牌映射的唯一数据源。
 * <p>纯静态、无 DB / 无 Spring 依赖，离线可读、可单测；由 {@link StatusMetaResolver} 与
 * {@code AdminMetaController} 暴露给三端引用，避免各端重复维护导致命名 / 色值漂移。</p>
 */
public final class MetaDictionary {

    private MetaDictionary() {
    }

    // ===== 语义色（Element Plus 默认调色板，pc-admin 已落地）=====
    private static final String NEUTRAL = "#909399";
    private static final String INFO = "#409EFF";
    private static final String SUCCESS = "#67C23A";
    private static final String WARNING = "#E6A23C";
    private static final String DANGER = "#F56C6C";

    // ===== 状态枚举字典 =====
    private static final List<StatusGroupVO> STATUS_DICTIONARY = List.of(
            new StatusGroupVO("order", "订单状态", List.of(
                    new StatusMetaVO("pending_pay", "待支付", INFO, "买家已下单，等待支付", false, true),
                    new StatusMetaVO("paid", "已支付(托管)", INFO, "支付成功，资金由平台托管", false, true),
                    new StatusMetaVO("pending_ship", "待发货", INFO, "卖家待发货", false, true),
                    new StatusMetaVO("shipping", "运输中/待收货", INFO, "已发货，运输途中", false, true),
                    new StatusMetaVO("completed", "已完成(已结算)", SUCCESS, "确认收货，资金已结算卖家", true, false),
                    new StatusMetaVO("closed", "已关闭", NEUTRAL, "取消/退款完成/超时关单", true, false)
            )),
            new StatusGroupVO("item", "商品状态", List.of(
                    new StatusMetaVO("draft", "草稿", NEUTRAL, "尚未提交审核", false, true),
                    new StatusMetaVO("pending_review", "待审核", INFO, "已提交，等待内容审核", false, true),
                    new StatusMetaVO("onsale", "在售", SUCCESS, "审核通过，对外可见可买", false, false),
                    new StatusMetaVO("locked", "已锁定(下单锁库存)", WARNING, "有进行中订单锁定库存", false, true),
                    new StatusMetaVO("sold", "已售出", SUCCESS, "全部卖出，下架", true, false),
                    new StatusMetaVO("rejected", "审核驳回", DANGER, "内容未通过审核", true, false),
                    new StatusMetaVO("off_shelf", "已下架/删除", NEUTRAL, "主动下架或删除", true, false)
            )),
            new StatusGroupVO("pay", "支付单状态", List.of(
                    new StatusMetaVO("wait", "待支付", INFO, "支付单已创建", false, true),
                    new StatusMetaVO("success", "支付成功(已托管)", SUCCESS, "渠道回执成功，资金托管", false, false),
                    new StatusMetaVO("closed", "已关闭", NEUTRAL, "超时或未支付关闭", true, false),
                    new StatusMetaVO("refunded", "已退款", WARNING, "已全额/部分退款", true, false)
            )),
            new StatusGroupVO("refund", "退款状态", List.of(
                    new StatusMetaVO("apply", "退款申请中", INFO, "买家发起退款", false, true),
                    new StatusMetaVO("wait_seller", "待卖家处理", INFO, "等待卖家响应", false, true),
                    new StatusMetaVO("platform", "平台介入中", WARNING, "上升至平台仲裁", false, true),
                    new StatusMetaVO("refunding", "退款处理中", WARNING, "渠道退款执行中", false, true),
                    new StatusMetaVO("refunded", "已退款", SUCCESS, "退款完成", true, false),
                    new StatusMetaVO("rejected", "已拒绝", DANGER, "卖家/平台驳回", true, false),
                    new StatusMetaVO("canceled", "已撤销", NEUTRAL, "买家主动撤销", true, false)
            )),
            new StatusGroupVO("dispute", "维权工单状态", List.of(
                    new StatusMetaVO("PENDING", "待卖家处理", INFO, "工单创建，等待卖家举证", false, true),
                    new StatusMetaVO("SELLER_REPLIED", "卖家已举证", INFO, "卖家已提交举证材料", false, true),
                    new StatusMetaVO("PLATFORM", "平台介入中", WARNING, "上升至平台裁决", false, true),
                    new StatusMetaVO("RESOLVED", "已裁决", SUCCESS, "平台作出裁决", true, false),
                    new StatusMetaVO("CLOSED", "已归档", NEUTRAL, "工单结束并归档", true, false),
                    new StatusMetaVO("CANCELED", "已撤销", NEUTRAL, "买家主动撤销", true, false)
            )),
            new StatusGroupVO("audit", "内容/审核状态", List.of(
                    new StatusMetaVO("pending", "待审核", INFO, "提交待机审核", false, true),
                    new StatusMetaVO("pass", "审核通过", SUCCESS, "内容合规，对外可见", false, false),
                    new StatusMetaVO("reject", "审核驳回", DANGER, "内容不合规，驳回", false, false)
            ))
    );

    // ===== 业务术语表（跨端统一命名，≥40 条）=====
    private static final List<TermGlossaryVO> TERMS = List.of(
            new TermGlossaryVO("闲置/二手", "用户转让的自用过的物品，非全新商品", "二手、转转、闲置物品", "item"),
            new TermGlossaryVO("一口价", "固定价格，不议价的交易方式", "定价、直购价", "trade"),
            new TermGlossaryVO("担保交易", "资金由平台托管，确认收货后结算卖家", "托管交易、担保支付", "trade"),
            new TermGlossaryVO("验货", "平台对高价值商品进行实物鉴定后再发货", "鉴定、质检", "item"),
            new TermGlossaryVO("议价", "买卖双方协商价格的会话过程", "砍价（通用）、谈价", "trade"),
            new TermGlossaryVO("砍价", "营销玩法：邀请好友助力降低商品底价", "助力砍价、砍价活动", "marketing"),
            new TermGlossaryVO("拼团", "多人成团享折扣的营销玩法", "团购、成团", "marketing"),
            new TermGlossaryVO("秒杀", "限时低价抢购的营销玩法", "限时抢购", "marketing"),
            new TermGlossaryVO("抽奖", "消耗机会抽取奖品的营销玩法", "大转盘、抽奖活动", "marketing"),
            new TermGlossaryVO("同城回收", "平台在用户所在城市上门回收闲置", "回收、以旧换新", "item"),
            new TermGlossaryVO("同城配送", "同一城市内的快速专人配送", "跑腿、即时配送", "trade"),
            new TermGlossaryVO("信用分", "基于履约行为的用户信用评分", "信誉分、信用等级分", "risk"),
            new TermGlossaryVO("成长值", "用户活跃与贡献累计的等级成长度量", "成长积分、经验值", "member"),
            new TermGlossaryVO("会员等级", "由成长值映射的用户分层（普通/银/金/钻石）", "VIP 等级", "member"),
            new TermGlossaryVO("优惠券", "抵扣订单金额的凭证券", "代金券、折扣券", "marketing"),
            new TermGlossaryVO("积分", "可兑换权益的虚拟货币", "金币、豆", "member"),
            new TermGlossaryVO("提现", "将钱包余额提至银行卡", "取现、提款", "finance"),
            new TermGlossaryVO("结算", "确认收货后平台将托管资金打给卖家", "放款、打款", "finance"),
            new TermGlossaryVO("对账", "核对交易流水与账户余额的一致性", "轧差、清结算核对", "finance"),
            new TermGlossaryVO("维权", "交易纠纷时买家发起的申诉工单", "投诉、纠纷、工单", "trade"),
            new TermGlossaryVO("风控", "识别与拦截欺诈/异常交易的风控系统", "反欺诈、安全", "risk"),
            new TermGlossaryVO("内容审核", "对商品/评价/头像等进行机审与人工复核", "机审、审核", "platform"),
            new TermGlossaryVO("数据看板", "经营数据的可视化仪表盘", "驾驶舱、BI 大屏", "platform"),
            new TermGlossaryVO("GMV", "一定周期内的成交总额（金额）", "成交额", "platform"),
            new TermGlossaryVO("转化率", "完成目标动作的用户占比（如支付转化率）", "转化、CVR", "platform"),
            new TermGlossaryVO("UV", "独立访客数（按设备/用户去重）", "独立用户数", "platform"),
            new TermGlossaryVO("PV", "页面/接口访问次数（不去重）", "浏览量", "platform"),
            new TermGlossaryVO("客单价", "平均每笔订单的金额", "ATV", "platform"),
            new TermGlossaryVO("复购率", "周期内再次购买的用户占比", "回头率", "platform"),
            new TermGlossaryVO("核销", "到店/使用后验证消费凭证完成履约", "消费验证、码核销", "trade"),
            new TermGlossaryVO("邀请码", "老用户邀请新用户的专属标识码", "邀请ID、推广码", "marketing"),
            new TermGlossaryVO("分销", "推广他人商品赚取佣金的机制", "推广、返利", "marketing"),
            new TermGlossaryVO("履约", "从下单到交付的完整服务过程", "交付、履约服务", "trade"),
            new TermGlossaryVO("在售", "商品审核通过、对外可见可买的状态", "上架中", "item"),
            new TermGlossaryVO("下架", "商品停止对外展示的操作", "撤架", "item"),
            new TermGlossaryVO("草稿", "尚未提交审核的商品暂存态", "未发布", "item"),
            new TermGlossaryVO("托管", "资金暂存平台、满足条件再结算的模式", "担保、存管", "finance"),
            new TermGlossaryVO("乐观锁", "以版本号/状态字段防止并发更新冲突", "version 锁、CAS", "platform"),
            new TermGlossaryVO("幂等", "同一请求重复提交只生效一次", "去重、防重", "platform"),
            new TermGlossaryVO("灰度", "按比例/白名单逐步放量新功能", "金丝雀、分批发布", "platform"),
            new TermGlossaryVO("限流", "限制单位时间请求量保护系统", "流控、rate limit", "platform"),
            new TermGlossaryVO("熔断", "依赖异常时快速失败避免雪崩", "断路器、circuit breaker", "platform"),
            new TermGlossaryVO("埋点", "采集用户行为与系统指标的数据点", "打点、track", "platform"),
            new TermGlossaryVO("漏斗", "用户完成多步目标的逐级转化视图", "转化漏斗", "platform"),
            new TermGlossaryVO("留存", "用户在一定时间后仍然活跃的比例", "retention", "platform"),
            new TermGlossaryVO("LTV", "用户生命周期内的总价值（收入）", "用户终身价值", "platform"),
            new TermGlossaryVO("CAC", "获取一个用户的综合成本", "获客成本", "platform"),
            new TermGlossaryVO("ROI", "投入产出比（收益/成本）", "投资回报率", "platform"),
            new TermGlossaryVO("事件驱动", "以领域事件解耦服务、异步驱动下游", "EDA、领域事件", "platform"),
            new TermGlossaryVO("死信", "多次消费失败进入补偿队列的消息", "dead letter", "platform"),
            new TermGlossaryVO("回放", "重投历史事件以修复或追平状态", "重放、replay", "platform"),
            new TermGlossaryVO("数据范围", "按机构/角色隔离可见数据边界", "数据权限、datascope", "platform"),
            new TermGlossaryVO("设计令牌", "品牌/语义/间距等设计决策的统一变量", "design token", "platform"),
            new TermGlossaryVO("语义色", "表达状态含义的颜色（成功/警告/危险等）", "状态色", "platform")
    );

    // ===== 设计令牌映射 =====
    private static final List<DesignTokenVO> DESIGN_TOKENS = List.of(
            new DesignTokenVO("color-primary", "#FF6A00",
                    Map.of("web", "var(--el-color-primary)", "mini", "#FF6A00"), "品牌主色：按钮/关键操作/Logo"),
            new DesignTokenVO("color-success", SUCCESS,
                    Map.of("web", "var(--el-color-success)", "mini", "#67C23A"), "成功/已完成/通过态"),
            new DesignTokenVO("color-warning", WARNING,
                    Map.of("web", "var(--el-color-warning)", "mini", "#E6A23C"), "进行中/待处理/介入态"),
            new DesignTokenVO("color-danger", DANGER,
                    Map.of("web", "var(--el-color-danger)", "mini", "#F56C6C"), "驳回/失败/高危态"),
            new DesignTokenVO("color-info", INFO,
                    Map.of("web", "var(--el-color-info)", "mini", "#409EFF"), "信息/中性/待办态"),
            new DesignTokenVO("color-text-primary", "#303133",
                    Map.of("web", "var(--el-text-color-primary)", "mini", "#303133"), "主文本"),
            new DesignTokenVO("color-text-regular", "#606266",
                    Map.of("web", "var(--el-text-color-regular)", "mini", "#606266"), "常规文本"),
            new DesignTokenVO("color-text-secondary", NEUTRAL,
                    Map.of("web", "var(--el-text-color-secondary)", "mini", "#909399"), "次要/占位文本"),
            new DesignTokenVO("color-border", "#DCDFE6",
                    Map.of("web", "var(--el-border-color)", "mini", "#DCDFE6"), "分隔线/边框"),
            new DesignTokenVO("radius-small", "4px",
                    Map.of("web", "var(--el-border-radius-small)", "mini", "8rpx"), "标签/小卡片圆角"),
            new DesignTokenVO("radius-base", "8px",
                    Map.of("web", "var(--el-border-radius-base)", "mini", "16rpx"), "卡片/弹窗圆角"),
            new DesignTokenVO("spacing-xs", "4px",
                    Map.of("web", "4px", "mini", "8rpx"), "最小间距"),
            new DesignTokenVO("spacing-sm", "8px",
                    Map.of("web", "8px", "mini", "16rpx"), "紧凑间距/列表项内距"),
            new DesignTokenVO("spacing-md", "16px",
                    Map.of("web", "16px", "mini", "32rpx"), "标准间距/区块间距"),
            new DesignTokenVO("spacing-lg", "24px",
                    Map.of("web", "24px", "mini", "48rpx"), "页面级大间距"),
            new DesignTokenVO("font-size-sm", "12px",
                    Map.of("web", "var(--el-font-size-extra-small)", "mini", "24rpx"), "辅助文字"),
            new DesignTokenVO("font-size-base", "14px",
                    Map.of("web", "var(--el-font-size-base)", "mini", "28rpx"), "正文"),
            new DesignTokenVO("font-size-lg", "16px",
                    Map.of("web", "var(--el-font-size-medium)", "mini", "32rpx"), "标题/强调")
    );

    /** 全部状态分组。 */
    public static List<StatusGroupVO> statusGroups() {
        return STATUS_DICTIONARY;
    }

    /** 业务术语表。 */
    public static List<TermGlossaryVO> terms() {
        return TERMS;
    }

    /** 设计令牌映射。 */
    public static List<DesignTokenVO> designTokens() {
        return DESIGN_TOKENS;
    }
}
