package com.idlefish.trade.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 读取 application.yml 中 idlefish.* 业务配置。
 * 外部组件均遵循「接口 + Mock（默认）+ 真实实现（按需开启）」的可切换范式。
 */
@Configuration
@ConfigurationProperties(prefix = "idlefish")
@Data
public class IdlefishProperties {

    private Jwt jwt = new Jwt();
    private boolean payMock = true;
    private boolean searchMock = true;
    private boolean mqMock = true;
    private boolean auditMock = true;
    private boolean logisticsMock = true;
    private File file = new File();
    private Oss oss = new Oss();
    private Search search = new Search();
    private Logistics logistics = new Logistics();
    private Mq mq = new Mq();
    private Pay pay = new Pay();
    private Risk risk = new Risk();
    private Login login = new Login();

    @Data
    public static class Jwt {
        private String secret;
        private long expireSeconds = 86400L;
        private long refreshSeconds = 2592000L;
    }

    @Data
    public static class File {
        /** 是否使用伪造 URL（不落盘）。演示默认 false，真实落盘并由后端静态映射 /uploads 提供访问。 */
        private boolean mock = false;
        /** 访问基址，需与静态资源映射路径一致。 */
        private String baseUrl = "/uploads/";
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
        /** 对外访问基址（CDN / 自定义域名），返回给前端的 URL 前缀 */
        private String baseUrl = "/uploads/";
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
        /** 是否使用本地 Mock 登录（code 直接当 openid）；生产置 false 走真实 jscode2session */
        private boolean mock = true;
        /** 微信小程序 AppID */
        private String appid;
        /** 微信小程序 AppSecret（生产通过环境变量注入，切勿明文提交） */
        private String secret;
        /** 微信 jscode2session 接口地址 */
        private String jscode2sessionUrl = "https://api.weixin.qq.com/sns/jscode2session";
    }
}
