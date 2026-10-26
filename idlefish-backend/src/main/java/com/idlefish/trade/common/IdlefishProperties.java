package com.idlefish.trade.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取 application.yml 中 idlefish.* 业务配置。
 * 外部组件一律使用真实实现：微信支付/分账、微信登录、阿里云 OSS、Elasticsearch、
 * RocketMQ、物流查询、内容审核、短信。缓存统一为 Redis 单一实现
 * （由 RedisCacheAutoConfig 始终装配，idlefish.redis 配置连接）。
 */
@Configuration
@ConfigurationProperties(prefix = "idlefish")
@Data
public class IdlefishProperties {

    private Jwt jwt = new Jwt();
    private Oss oss = new Oss();
    private Search search = new Search();
    private Logistics logistics = new Logistics();
    private Mq mq = new Mq();
    private Pay pay = new Pay();
    private Risk risk = new Risk();
    private Login login = new Login();
    private Cookie cookie = new Cookie();
    private RateLimit ratelimit = new RateLimit();
    private Redis redis = new Redis();
    private Notify notify = new Notify();
    private Credit credit = new Credit();
    private Observability observability = new Observability();
    private Point point = new Point();
    private Invite invite = new Invite();
    private Pricing pricing = new Pricing();
    private Local local = new Local();
    private SearchService searchService = new SearchService();
    /** 数据范围（ITER-T05）：声明带 org_id 列的表，启用 SQL 级机构隔离。 */
    private DataScope dataScope = new DataScope();
    /** 运营数据导出中心（ITER-T06 / REQ-07）：范围受限导出的安全边界与落盘配置。 */
    private Export export = new Export();
    /** 事件驱动架构（ITER-T06 / PRD-P2-T06）：Outbox 分发重试、死信、回放限流与持久化保留期。 */
    private Event event = new Event();
    /** 会员积分成长深化（ITER-F07 / PRD-P2-F07）：成长值（不可消耗）多维累计、防刷、衰减与权益兑换。 */
    private Growth growth = new Growth();
    /** IM 智能助手（ITER-F06 / PRD-P2-F06）：意图路由、知识库话术、置信度分流、纠纷预警与 LLM 增强。 */
    private ImAssistant imAssistant = new ImAssistant();

    @Data
    public static class Jwt {
        private String secret;
        private long expireSeconds = 86400L;
        private long refreshSeconds = 2592000L;
    }

    @Data
    public static class Oss {
        /** 访问域名（含 bucket 与 endpoint），例如 https://idlefish.oss-cn-hangzhou.aliyuncs.com */
        private String endpoint;
        /** 存储桶 */
        private String bucket;
        /** 地域 */
        private String region = "cn-hangzhou";
        /** RAM 访问密钥 */
        private String accessKey;
        private String secretKey;
        /** 对外访问基址（CDN / 自定义域名）；留空则回退到 https://<bucket>.<endpoint>/ 默认域名 */
        private String baseUrl = "";
    }

    @Data
    public static class Search {
        /** ES 节点地址列表，例如 http://127.0.0.1:9200 */
        private List<String> hosts = new ArrayList<>();
        /** 索引名 */
        private String index = "idlefish_item";
        private String username;
        private String password;
        /** 文档类型（ES7+ 固定 _doc） */
        private String type = "_doc";
    }

    @Data
    public static class Logistics {
        /** 物流服务商：kuaidi100 / cainiao */
        private String provider = "kuaidi100";
        /** 授权账号（快递100 的 customer） */
        private String customer;
        /** 授权 key */
        private String key;
        /** 查询接口地址 */
        private String url = "https://poll.kuaidi100.com/poll/query.do";
    }

    @Data
    public static class Mq {
        /** 阿里云 RocketMQ HTTP 接入点 */
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String instanceId;
        private String topic;
        private String group;
        private String region = "cn-hangzhou";
    }

    @Data
    public static class Pay {
        /** 微信支付商户号 */
        private String mchid;
        /** 小程序 AppID */
        private String appid;
        /** 商户 API 证书序列号 */
        private String serialNo;
        /** 商户 API 私钥（PEM / PKCS#8），生产通过环境变量注入，切勿明文提交 */
        private String privateKey;
        /** APIv3 密钥（回调报文 AES-256-GCM 解密） */
        private String apiV3Key;
        /** 微信支付平台证书公钥 PEM（用于 v3 回调节点签名验证；未配置则验签安全失败） */
        private String platformCert;
        /** 支付结果异步通知地址 */
        private String notifyUrl;
        /** 微信支付网关 */
        private String gateway = "https://api.mch.weixin.qq.com";
        /** 商家转账到零钱场景 ID（微信固定枚举，默认 1000 现金营销）。 */
        private String transferSceneId = "1000";
    }

    @Data
    public static class Risk {
        /** 是否启用设备指纹采集与设备维度风控规则 */
        private boolean deviceEnabled = true;
        /** 高风险金额阈值（分），新设备 + 超该金额下单触发 R3 */
        private long highAmountThresholdFen = 5000000L; // 5 万元
        /** 同设备绑定账号数阈值，超过触发 R4（疑似接码/群控） */
        private int deviceAccountThreshold = 3;
        /** 设备维度下单频次阈值（单位时间分钟内），超过触发 R5 */
        private int deviceOrderRate = 10;
        private int deviceOrderWindowMin = 1;
        /** 设备黑名单（命中直接冻结） */
        private List<String> blacklistDeviceIds = new ArrayList<>();
    }

    @Data
    public static class Login {
        /** 微信小程序 AppID */
        private String appid;
        /** 微信小程序 AppSecret（生产通过环境变量注入，切勿明文提交） */
        private String secret;
        /** 微信 jscode2session 接口地址 */
        private String jscode2sessionUrl = "https://api.weixin.qq.com/sns/jscode2session";
    }

    @Data
    public static class Cookie {
        /** 是否启用 Secure 属性（HTTPS 环境置 true，经 IDLEFISH_COOKIE_SECURE 注入）。 */
        private boolean secure = false;
    }

    @Data
    public static class RateLimit {
        /** 是否启用请求限流；默认关闭，生产经 IDLEFISH_RATELIMIT_ENABLED=true 开启。 */
        private boolean enabled = false;
        /** 单 (客户端IP, 接口路径) 每分钟允许的最大请求数。 */
        private long permitsPerMinute = 200L;
    }

    @Data
    public static class Redis {
        private String host = "127.0.0.1";
        private int port = 6379;
        private int database = 0;
        private String password;
        /** 连接/读取超时（毫秒） */
        private long timeoutMs = 3000L;
    }

    @Data
    public static class Notify {
        /** 实时 WebSocket 推送是否启用（本地 WS 即真实通道，无需外部凭据）。 */
        private boolean pushEnabled = true;
        /** 系统告警接收者（管理员账号 userId）；资金对账差异等告警发往此处。 */
        private Long alertAdminUserId = 1L;
        /** 真实短信网关配置（smsMode=real 时生效）。 */
        private Sms sms = new Sms();
        /** 微信订阅消息配置（F-14.4）：开启后按 NotificationType→templateId 在关键节点真实触达。 */
        private Subscribe subscribe = new Subscribe();

        @Data
        public static class Sms {
            /** 是否真正外发（smsMode=real 且 enabled=true 才外发；否则回落日志）。 */
            private boolean enabled = false;
            private String signName;
            private String templateCode;
            private String accessKey;
            private String secretKey;
            private String endpoint;
        }

        @Data
        public static class Subscribe {
            /** 是否启用微信订阅消息真实推送（默认关闭，配置 templateId 后开启）。 */
            private boolean enabled = false;
            /**
             * 事件类型 code → 微信订阅消息模板 ID 映射（如 order_paid → 模板A）。
             * 仅命中映射的事件才会真实推送；未配置 templateId 的事件自动跳过，避免报错。
             */
            private java.util.Map<String, String> templates = new java.util.HashMap<>();
        }
    }

    @Data
    public static class Observability {
        /** 零依赖可观测性总开关：HTTP traceId + 请求指标 + 业务指标。 */
        private boolean enabled = true;
        /** 阈值告警总开关（F-12.3）。 */
        private boolean alertEnabled = true;
        /** 告警接收者（管理员账号 userId）。 */
        private Long alertAdminUserId = 1L;
        /** 慢请求阈值（毫秒，按路由均值评估）。 */
        private long slowRequestMs = 1000;
        /** 错误率阈值（5xx / 总请求，超过即告警）。 */
        private double errorRateThreshold = 0.05;
        /** 支付验签失败累计阈值（近周期，超过即告警）。 */
        private long verifyFailureThreshold = 5;
        /**
         * P99 延迟 SLO 阈值（毫秒）：任意 {@code *.latency} 计时器的 P99 超过即告警（REQ-08）。
         * 默认 2000ms。
         */
        private long p99ThresholdMs = 2000;
        /**
         * 资损级重复流水阈值（次）：{@code fund.flow.duplicate} 计数器超过即告警（REQ-07）。
         * 该指标代表被 CAS 拦截的可疑重复出款/入账尝试；默认 1（即超过 1 次，2 次及以上即告警，
         * 留出单次并发竞态的良性余量，避免误报）。
         */
        private long duplicateFlowThreshold = 1;
        /**
         * 支付回调超时/对账缺口阈值（次）：{@code pay.callback.timeout} 计数器超过即告警（REQ-06）。
         * 默认 3。
         */
        private long callbackTimeoutThreshold = 3;
        /** 同类告警冷却时间（分钟），冷却期内不重复发送。 */
        private long cooldownMinutes = 30;
        /**
         * 监控端点收敛令牌（T01 内网收敛，REQ-10 / §九）：内网之外访问
         * {@code /actuator/prometheus|metrics|env|...} 等敏感端点须携带
         * {@code X-Actuator-Token} 且与此值一致；留空则仅依赖内网 IP 收敛（不强制令牌）。
         * 生产建议经 {@code IDLEFISH_OBSERVABILITY_ACTUATOR_TOKEN} 注入高强度随机串，
         * 配合网关仅允许内网/运维网段抓取。
         */
        private String actuatorToken = "";
        /**
         * 卡单守卫窗口（分钟，T01 防御网）：{@code status='pending_pay' 且 created_at} 早于
         * 当前时间减去该值的订单视为异常卡单，转化为 {@code pay.callback.timeout} 计数（REQ-06）。
         * 默认 30 分钟，与 {@code TradeScheduler.closeExpired} 关单窗口对齐（关单失败则需告警介入）。
         */
        private long stuckOrderMinutes = 30;
    }

    @Data
    public static class Credit {
        /** 实名认证加分 */
        private int realName = 10;
        /** 每满 30 天注册时长加分 */
        private int agePer30d = 2;
        /** 注册时长加分封顶 */
        private int ageCap = 20;
        /** 履约率权重（完成订单 / 总订单） */
        private int fulfillment = 40;
        /** 好评率权重（好评数 / 总评价数） */
        private int review = 30;
        /** 无评价时的好评基线分 */
        private int reviewBaseline = 15;
        /** 封禁扣分 */
        private int banPenalty = 30;
        /** 单条高风险事件扣分 */
        private int riskPerEvent = 5;
        /** 风控扣分封顶 */
        private int riskCap = 30;
    }

    /**
     * 积分体系配置（F-13.1）：兑换比例、抵扣上限、各场景获得规则。
     * 金额单位：分。兑换比例 redeemPointsPerYuan 表示「多少积分抵 1 元（=100 分）」。
     */
    @Data
    public static class Point {
        /** 积分功能总开关（默认开启） */
        private boolean enabled = true;
        /**
         * 抵现：多少积分抵 1 元（100 积分 = 1 元 = 100 分）。
         * 锚定基准，维持不动以防范积分通胀；运维可调区间 50~200（值越小越保值）。
         */
        private int redeemPointsPerYuan = 100;
        /** 抵现：积分抵扣金额不超过订单应付的比例（0~1）；0.6 保留 ≥40% 现金支付护毛利 */
        private double maxRedeemRatio = 0.6;
        /** 赚取：交易每 1 元（100 分）得多少积分；翻倍至 2 提升获取速率（有效返现 ≈2%） */
        private int earnPointsPerYuan = 2;
        /** 赚取：单笔交易得积分封顶；匹配 2× 速率，单笔最多抵 20 元 */
        private long tradeMaxPerOrder = 2000;
        /** 签到：基础积分；提升至 10 增强日活吸引力 */
        private int signinBase = 10;
        /** 签到：连续天数额外加成封顶（每天 +1，封顶该值）；提升至 15 延长激励曲线 */
        private int signinMaxBonus = 15;
        /** 评价：每笔评价固定积分；提升至 15 激励 UGC 评价 */
        private int reviewFixed = 15;
    }

    /**
     * 邀请拉新 / 分销配置（F-13.4）：邀请码、绑定关系、首单返券奖励。
     */
    @Data
    public static class Invite {
        /** 邀请拉新功能总开关（默认关闭，配置 rewardCouponId 后开启）。 */
        private boolean enabled = false;
        /** 首单返券的券模板 ID（Coupon.id）；需运营预先在后台创建。 */
        private Long rewardCouponId;
        /** 是否仅首单返券（1=仅首单，防刷；0=每单返，慎用）。 */
        private int firstOrderOnly = 1;
    }

    /**
     * 估价引擎配置（ITER-F03）：异常低价送审告警接收者、样本清洗与兜底开关。
     */
    @Data
    public static class Pricing {
        /** 异常低价送审告警接收者（管理员账号 userId）；为空回退到 notify.alertAdminUserId。 */
        private Long alertAdminUserId;
        /** 估价功能总开关（默认开启）。关闭后 hasData=false，由前端隐藏入口不阻塞（REQ-06）。 */
        private boolean enabled = true;
        /** 样本清洗 IQR 倍数（异常值剔除，默认 1.5）。 */
        private double iqrFactor = 1.5;
        /** 同成色样本不足该数量时回落规则兜底（默认 5，REQ-03）。 */
        private int minSampleCount = 5;
        /** 偏离判定阈值：低于下限比例（默认 0.5=50%）、高于上限比例（默认 1.0=100%，REQ-09）。 */
        private double lowDeviationRatio = 0.5;
        private double highDeviationRatio = 1.0;
    }

    /**
     * 同城回收与本地履约配置（ITER-F05）。
     * 金额单位一律「分」；第三方服务商为真实 HTTP 调用，配置缺失即判定不可用并明确提示（REQ-09），
     * 不做静默降级、不使用 Mock 数据冒充成功。
     */
    @Data
    public static class Local {
        /** 同城履约总开关；关闭后所有同城入口返回明确提示（REQ-09）。 */
        private boolean enabled = true;
        /** 开通同城服务的城市清单（REQ-01 / REQ-09）；为空表示不限制，生产建议显式配置。 */
        private List<String> cities = new ArrayList<>();
        /** 一键回收白名单类目 ID（REQ-11）；为空表示不限制。 */
        private List<Long> recycleCategories = new ArrayList<>();
        /**
         * 回收估价：成色档位 → 基准价折扣系数（0~1）。键为 conditionLevel
         * （1 全新 / 2 95新 / 3 9成新 / 4 8成新 / 5 功能完好）；回收价低于二手成交价故整体折让。
         * 注意：YAML 只覆盖出现的键，未配置档位沿用此处默认值。
         */
        private Map<Integer, Double> conditionRatio = new HashMap<>(Map.of(
                1, 0.85, 2, 0.75, 3, 0.62, 4, 0.48, 5, 0.35));
        /** 回收估价兜底比例（相对 ITER-F03 参考价）；无成色命中时使用，默认 0.6。 */
        private double defaultEstimateRatio = 0.6;
        /** 回收估价下限（分）：低于该值不生成回收单，避免无意义上门（默认 100 分 = 1 元）。 */
        private long minEstimateFen = 100L;
        /** 质检调价最大跌幅比例（0~1）：超过该跌幅视为异常，需人工复核（REQ-04）。默认 0.5。 */
        private double maxQuoteDropRatio = 0.5;
        /** 自提订单自动确认收货天数（REQ-10）；快递单仍按既有 10 天。默认 3 天。 */
        private int selfPickupConfirmDays = 3;
        /** 同城配送计费：起步价（分）。 */
        private long deliveryBaseFeeFen = 600L;
        /** 同城配送计费：起步里程（公里）。 */
        private double deliveryBaseKm = 3.0;
        /** 同城配送计费：超出起步里程后每公里费用（分）。 */
        private long deliveryPerKmFen = 200L;
        /** 同城配送：最大承接距离（公里），超出直接判定不可用（REQ-09）。 */
        private double deliveryMaxKm = 30.0;
        /** 回收 / 同城履约告警接收者（管理员 userId）；为空回退到 notify.alertAdminUserId。 */
        private Long alertAdminUserId;
        /** 第三方同城服务商（真实 HTTP）。 */
        private Provider provider = new Provider();

        @Data
        public static class Provider {
            /**
             * 是否启用真实第三方调度。false 或未配置凭据时创建配送统一返回「服务暂不可用」
             * 并抛出 LOCAL_DELIVERY_UNAVAILABLE（REQ-09 禁止静默失败）。
             */
            private boolean enabled = false;
            /** 服务商名称（日志与轨迹归因）。 */
            private String name = "local-provider";
            /** 服务商接口基址，例如 https://open.example.com/local */
            private String baseUrl = "";
            private String appKey = "";
            private String appSecret = "";
            /** HTTP 超时（毫秒）。 */
            private long timeoutMs = 3000L;
        }
    }

    /**
     * 搜推服务化配置（ITER-T04）：双模式开关 + 远程调用参数 + 熔断/隔离 + 同步滞后阈值。
     * <ul>
     *   <li>mode=inline（默认）：内联模式，检索/推荐在进程内执行，行为与拆分前一致；</li>
     *   <li>mode=remote：远程模式，经 HTTP + 服务令牌调用独立部署的搜推服务（/sr/api/**），
     *       失败由熔断器与降级阶梯兜底，不影响交易主链路（REQ-03/08/10）。</li>
     * </ul>
     */
    @Data
    public static class SearchService {
        /** 搜推服务化总开关；关闭后所有调用直接走内联（等价历史行为）。 */
        private boolean enabled = true;
        /** 路由模式：inline / remote。 */
        private Mode mode = Mode.INLINE;
        /** 远程模式：搜推服务基址（例如 http://search-svc.internal:8080）。 */
        private String baseUrl = "";
        /** 远程模式：服务间令牌（X-Service-Token）；服务端未配置时 fail-closed 拒绝（REQ-09）。 */
        private String serviceToken = "";
        /** 远程调用读取超时（毫秒）；隔离池任务总超时亦以此为准。 */
        private long timeoutMs = 2000L;
        /** 远程调用连接超时（毫秒）。 */
        private long connectTimeoutMs = 500L;
        /** 熔断阈值：连续失败次数达到即熔断（REQ-08）。 */
        private int failureThreshold = 5;
        /** 熔断持续时间（毫秒）：到期转半开放行单次探测。 */
        private long openDurationMs = 10_000L;
        /** 隔离线程池核心线程数（REQ-09）。 */
        private int corePoolSize = 8;
        /** 隔离线程池最大线程数（REQ-09）。 */
        private int maxPoolSize = 32;
        /** 隔离线程池有界队列容量；饱和即快速失败降级。 */
        private int queueCapacity = 256;
        /** 索引同步消息投递开关（REQ-02）；关闭后仅本地双写。 */
        private boolean syncEnabled = true;
        /** 索引同步滞后告警阈值（秒，REQ-07）：超过即告警并在响应标注数据时效。 */
        private long lagAlertSeconds = 60L;
        /** 搜推滞后/异常告警接收者（管理员 userId）；为空回退 notify.alertAdminUserId。 */
        private Long alertAdminUserId;
    }

    /** 搜推路由模式（ITER-T04 / REQ-10）。 */
    public enum Mode {
        INLINE, REMOTE
    }

    /**
     * 数据范围配置（ITER-T05 / REQ-02/09）。
     * 仅 {@code orgIdTables} 中列出的物理表会由 {@code DataPermissionInterceptor} 注入
     * {@code org_id IN (...)}；未配置则统一过滤层不生效（保持既有行为，零回归风险）。
     * 默认启用 {@code t_admin_user} 的管理员机构隔离；其余表（如 t_item）可按需在配置中追加。
     */
    @Data
    public static class DataScope {
        private List<String> orgIdTables = new ArrayList<>(List.of("t_admin_user"));
    }

    /**
     * 运营数据导出中心配置（ITER-T06 / REQ-07）。
     * 安全边界：带 org_id 的实体仅导出可见机构数据；无 org_id 实体（用户/订单）仅 ALL 管理员可导出。
     * 落盘：本地磁盘默认（生产可切换 OSS 实现）。
     */
    @Data
    public static class Export {
        /** 单次导出行数上限（默认 5 万行，防大查询拖垮主库）。 */
        private int maxRowsPerExport = 50000;
        /** 导出文件体积上限（MB，默认 50MB）。 */
        private long maxFileMb = 50L;
        /** 本地落盘目录（默认应用工作目录下 export-files/）。 */
        private String storageDir = "export-files";
    }

    /**
     * 事件驱动架构配置（ITER-T06 / PRD-P2-T06）。
     * Outbox 同事务落库（REQ-05）+ afterCommit 本地分发 + 定时补偿重试（REQ-06）
     * + 死信重投（REQ-08）+ 限流回放（REQ-13）。
     */
    @Data
    public static class Event {
        /** 事件驱动总开关；关闭后 publish 仅记录日志不落库（测试/降级场景）。 */
        private boolean enabled = true;
        /** 是否将本地消费成功的事件镜像至 RocketMQ（可选链路，默认关闭；凭据复用 idlefish.mq.*）。 */
        private boolean mqEnabled = false;
        /** 分发失败最大重试次数（达到即转死信 DEAD，REQ-08）。默认 8 次（累计约数小时覆盖）。 */
        private int maxRetry = 8;
        /** 重试基础退避（秒）：第 n 次重试延迟 = base * 2^n，封顶 maxBackoffSeconds。默认 10s。 */
        private long baseBackoffSeconds = 10L;
        /** 单次重试最大退避（秒）。默认 3600s（1 小时）。 */
        private long maxBackoffSeconds = 3600L;
        /** 单次扫描分发的最大事件数（防大积压时拖垮进程）。默认 200。 */
        private int scanBatchSize = 200;
        /** 事件存储保留天数（REQ-09 ≥90 天可回放）。默认 92 天（留 2 天余量）。 */
        private int retentionDays = 92;
        /** 回放限流（REQ-13）：单回放任务每次拉取事件数（节流批次）。默认 100。 */
        private int replayBatchSize = 100;
        /** 回放限流（REQ-13）：两批次之间的间隔毫秒。默认 200ms（≈500 事件/秒上限）。 */
        private long replayIntervalMs = 200L;
        /** 单个回放任务最大事件数（防误操作全量回放）。默认 10000。 */
        private int replayMaxEvents = 10000;
    }

    /**
     * 会员积分成长深化配置（ITER-F07 / PRD-P2-F07）。
     * <p>成长值不可消耗、仅决定等级（REQ-01）；与积分（{@link Point}）完全解耦。
     * 规则变更只对新行为生效，不追溯重算历史成长值（REQ-14）。</p>
     */
    @Data
    public static class Growth {
        /** 成长值功能总开关；关闭后所有来源一律不计成长值（best-effort，不阻断交易主流程）。 */
        private boolean enabled = true;

        // ===== REQ-02 多维累计 =====
        /** 交易：每 1 元（100 分）得多少成长值。默认 1（≈1% 返值，与等级阈值 100/500/2000/8000 匹配）。 */
        private int tradeGrowthPerYuanFen = 1;
        /** 交易：单笔成长值封顶（防大额订单一次性冲级）。默认 0 不封顶。 */
        private int tradeCapPerOrder = 0;
        /** 发布：每发布一个商品固定成长值。 */
        private int publishFixed = 10;
        /** 评价：评价通过固定成长值。 */
        private int reviewFixed = 20;
        /** 验货配合（F-02 验货单完成）固定成长值。 */
        private int inspectionFixed = 30;
        /** 纠纷和解（F-17 判责且双方和解）固定成长值。 */
        private int reconcileFixed = 15;

        // ===== REQ-03 刷分识别 =====
        /** 关联账户阈值：同设备绑定账号数超过即视为团伙刷分。默认 3。 */
        private int maxLinkedAccounts = 3;
        /** 频次阈值：窗口内成长值发放次数达到即拦截。默认 20。 */
        private int maxEarnInWindow = 20;
        /** 频次统计窗口（天）。默认 1。 */
        private int earnWindowDays = 1;
        /** 金额门槛（分）：交易金额低于该值不发成长值，防 1 分钱刷量。默认 100 分 = 1 元。 */
        private long minAmountFen = 100L;

        // ===== REQ-05 不活跃衰减 / 降级 =====
        /** 衰减开关。 */
        private boolean decayEnabled = true;
        /** 不活跃阈值（天）：超过该天数未活跃才进入衰减。默认 90 天。 */
        private int decayThresholdDays = 90;
        /** 每衰减日扣减成长值。默认 5。 */
        private int decayPerDay = 5;
        /** 衰减下限：成长值扣到该值不再继续扣（保底留在 L1）。默认 0。 */
        private int decayFloor = 0;
        /** 提前通知天数（REQ-05 提前 7 日）。默认 7。 */
        private int decayNotifyDays = 7;
        /** 衰减/降级通知与告警接收者（管理员 userId）；为空回退 notify.alertAdminUserId。 */
        private Long alertAdminUserId;

        // ===== REQ-10 判责扣减 =====
        /** F-17 判责扣减成长值：单次封顶（0=不封顶，按当前值全扣下限 0）。默认 500。 */
        private int disputePenaltyCap = 500;

        /** 规则版本号（REQ-14）：变更规则时递增，流水快照该值以便区分口径、支撑不追溯重算。 */
        private String ruleVersion = "v1";
    }

    /**
     * IM 智能助手配置（ITER-F06 / PRD-P2-F06）。
     * <p>金额口径：知识库模板中金额一律「分」存储，展示由 {@code AnswerComposer.formatFen} 格式化为元（REQ-05）。
     * LLM 默认关闭：零模型可用时知识库模板 + 通用回落保证链路不阻塞（REQ-12）。</p>
     */
    @Data
    public static class ImAssistant {
        /** 智能助手总开关；关闭后买家消息不触发自动应答（仅人工链路）。 */
        private boolean enabled = true;
        /** 新会话默认开启卖家自动代答（卖家可会话级关闭，反向保护 REQ-09）。 */
        private boolean sellerAutoReplyDefault = true;
        /** 置信度阈值：达到即模板化自动作答（REQ-03，默认 0.6）。 */
        private double confidenceThreshold = 0.6;
        /** 纠纷预警风险分阈值（REQ-06，默认 60）。 */
        private int disputeWarnThreshold = 60;
        /** 纠纷信号累计窗口（小时）：窗口内同会话风险分累加（REQ-06）。 */
        private int disputeSignalWindowHours = 24;
        /** 会话级刷量限流：单会话每日最大自动应答条数（边界场景 8）。 */
        private int maxAutoReplyPerConvPerDay = 20;
        /** 卖家超时未响应阈值（小时）：触发纠纷信号 SLOW_REPLY（REQ-06）。 */
        private int sellerSlowReplyHours = 24;
        /** LLM 增强配置（默认关闭；开启后对中等置信度问题试探生成，失败回落模板 REQ-12）。 */
        private Llm llm = new Llm();

        /**
         * LLM（OpenAI 兼容）真实 HTTP 接入配置。
         * <p>未配置 baseUrl/apiKey 或 enabled=false 时 {@code LlmClient.isAvailable()=false}，
         * 链路走模板回落，绝不静默失败（REQ-12 降级不阻塞）。</p>
         */
        @Data
        public static class Llm {
            /** 是否启用 LLM 增强（默认关闭）。 */
            private boolean enabled = false;
            /** OpenAI 兼容接口基址，例如 https://api.openai.com/v1。 */
            private String baseUrl = "";
            /** API Key（生产经环境变量注入，切勿明文提交）。 */
            private String apiKey = "";
            /** 模型名，例如 gpt-4o-mini / qwen-plus。 */
            private String model = "";
            /** 读取超时（毫秒，默认 3000）。 */
            private long timeoutMs = 3000L;
            /**
             * 系统提示词（幻觉约束）：只依据给定知识库条目与平台规则作答；
             * 不得代接受报价、不得对退款/责任/赔付作裁决、不确定时明确说明并建议转人工。
             */
            private String systemPrompt = "你是闲置交易平台的智能客服助手。只依据给定的知识库条目与平台规则回答买家问题；"
                    + "不得代替卖家接受议价或承诺价格，不得对退款、责任认定、赔付金额作出裁决；"
                    + "不确定时必须明确说明并建议转人工客服。回答简洁友好，200 字以内。";
        }
    }
}
