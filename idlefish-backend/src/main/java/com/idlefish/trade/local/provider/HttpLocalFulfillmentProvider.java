package com.idlefish.trade.local.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.util.SignUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 第三方同城调度真实 HTTP 实现（ITER-F05 / REQ-06、REQ-09）。
 *
 * <p>与 {@code RealLogisticsServiceImpl} 同范式：RestTemplate + 表单签名真实调用；
 * 不同点在于本实现**不做成功降级**——网络异常、解析失败、返回非零码一律
 * 收敛为 {@code DispatchResult.fail(明确原因)}，由上层抛 {@code LOCAL_DELIVERY_UNAVAILABLE}。
 * 这是 REQ-09 的硬性要求：第三方不可用必须给出明确提示，而不是伪装成「已下单」。
 *
 * <p>预期第三方契约（可按实际服务商调整字段映射）：
 * <ul>
 *   <li>POST {@code {baseUrl}/dispatch}：入参 deliveryNo/bizType/fromAddress/toAddress/...，
 *       返回 {@code {"code":0,"data":{"providerNo":"","courierName":"","courierPhone":""}}}</li>
 *   <li>GET/POST {@code {baseUrl}/track}：入参 providerNo，
 *       返回 {@code {"code":0,"data":[{"time":"","desc":""}]}}</li>
 *   <li>POST {@code {baseUrl}/cancel}：入参 providerNo，返回 {@code {"code":0}}</li>
 * </ul>
 */
@Component
public class HttpLocalFulfillmentProvider implements LocalFulfillmentProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpLocalFulfillmentProvider.class);

    private final IdlefishProperties props;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public HttpLocalFulfillmentProvider(IdlefishProperties props, ObjectMapper objectMapper,
                                        RestTemplate restTemplate) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    public String name() {
        IdlefishProperties.Local.Provider cfg = props.getLocal().getProvider();
        return cfg.getName() == null || cfg.getName().isBlank() ? "local-provider" : cfg.getName();
    }

    @Override
    public boolean configured() {
        IdlefishProperties.Local.Provider cfg = props.getLocal().getProvider();
        return cfg.isEnabled()
                && cfg.getBaseUrl() != null && !cfg.getBaseUrl().isBlank()
                && cfg.getAppKey() != null && !cfg.getAppKey().isBlank()
                && cfg.getAppSecret() != null && !cfg.getAppSecret().isBlank();
    }

    @Override
    public DispatchResult dispatch(DispatchCommand cmd) {
        if (!configured()) {
            // REQ-09：未配置服务商时明确不可用，不生成本地假运单
            return DispatchResult.fail("同城配送服务商未配置或已停用");
        }
        try {
            MultiValueMap<String, String> body = signedBody(cmd.getDeliveryNo());
            body.add("bizType", nz(cmd.getBizType()));
            body.add("bizNo", nz(cmd.getBizNo()));
            body.add("city", nz(cmd.getCity()));
            body.add("fromAddress", nz(cmd.getFromAddress()));
            body.add("toAddress", nz(cmd.getToAddress()));
            body.add("pickupContact", nz(cmd.getPickupContact()));
            body.add("receiverContact", nz(cmd.getReceiverContact()));
            body.add("distanceKm", String.valueOf(cmd.getDistanceKm() == null ? 0d : cmd.getDistanceKm()));
            body.add("remark", nz(cmd.getRemark()));

            JsonNode root = post("/dispatch", body);
            if (root == null || !isOk(root)) {
                String err = root == null ? "服务商无响应" : root.path("message").asText("服务商拒绝接单");
                log.warn("同城调度失败: {}", err);
                return DispatchResult.fail(err);
            }
            JsonNode data = root.path("data");
            String providerNo = data.path("providerNo").asText("");
            if (providerNo.isBlank()) {
                // 无运单号即视为失败：不允许产生「无法追踪」的黑盒配送单
                return DispatchResult.fail("服务商未返回有效运单号");
            }
            return DispatchResult.ok(providerNo, data.path("courierName").asText(""),
                    data.path("courierPhone").asText(""));
        } catch (Exception e) {
            log.warn("同城调度异常: {}", e.getMessage());
            return DispatchResult.fail("同城配送服务暂不可用：" + e.getMessage());
        }
    }

    @Override
    public List<TrackPoint> fetchTrack(String providerNo) {
        if (!configured()) {
            return List.of();
        }
        try {
            MultiValueMap<String, String> body = signedBody(providerNo);
            JsonNode root = post("/track", body);
            if (root == null || !isOk(root)) {
                return List.of();
            }
            JsonNode data = root.path("data");
            List<TrackPoint> points = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode n : data) {
                    TrackPoint p = new TrackPoint();
                    p.setTime(n.path("time").asText(""));
                    p.setDesc(n.path("desc").asText(""));
                    points.add(p);
                }
            }
            return points;
        } catch (Exception e) {
            log.warn("同城轨迹拉取异常: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean cancel(String providerNo) {
        if (!configured()) {
            return false;
        }
        try {
            JsonNode root = post("/cancel", signedBody(providerNo));
            return root != null && isOk(root);
        } catch (Exception e) {
            log.warn("同城运单取消异常: {}", e.getMessage());
            return false;
        }
    }

    // ===== 私有工具 =====

    /** 构造带 appKey 与签名的表单体（签名规则：md5(appKey + deliveryNo/providerNo + secret) 大写）。 */
    private MultiValueMap<String, String> signedBody(String keyNo) {
        IdlefishProperties.Local.Provider cfg = props.getLocal().getProvider();
        String sign = SignUtils.md5Hex(nz(cfg.getAppKey()) + nz(keyNo) + nz(cfg.getAppSecret())).toUpperCase();
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("appKey", nz(cfg.getAppKey()));
        body.add("outNo", nz(keyNo));
        body.add("sign", sign);
        return body;
    }

    private JsonNode post(String path, MultiValueMap<String, String> body) throws Exception {
        String url = props.getLocal().getProvider().getBaseUrl() + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String resp = restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
        if (resp == null || resp.isBlank()) {
            return null;
        }
        return objectMapper.readTree(resp);
    }

    private static boolean isOk(JsonNode root) {
        JsonNode code = root.get("code");
        if (code != null) {
            return "0".equals(code.asText()) || 0 == code.asInt(-1);
        }
        JsonNode errno = root.get("errno");
        return errno != null && (0 == errno.asInt(-1) || "0".equals(errno.asText()));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
