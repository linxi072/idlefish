package com.idlefish.trade.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

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
        /** 同类告警冷却时间（分钟），冷却期内不重复发送。 */
        private long cooldownMinutes = 30;
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
        /** 抵现：多少积分抵 1 元（100 积分 = 1 元 = 100 分） */
        private int redeemPointsPerYuan = 100;
        /** 抵现：积分抵扣金额不超过订单应付的比例（0~1） */
        private double maxRedeemRatio = 0.5;
        /** 赚取：交易每 1 元（100 分）得多少积分 */
        private int earnPointsPerYuan = 1;
        /** 赚取：单笔交易得积分封顶 */
        private long tradeMaxPerOrder = 1000;
        /** 签到：基础积分 */
        private int signinBase = 5;
        /** 签到：连续天数额外加成封顶（每天 +1，封顶该值） */
        private int signinMaxBonus = 10;
        /** 评价：每笔评价固定积分 */
        private int reviewFixed = 10;
    }
}
