package com.idlefish.trade.item.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.util.HttpUtils;
import com.idlefish.trade.common.util.SignUtils;
import com.idlefish.trade.item.dto.AuditResult;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/**
 * 真实内容安全实现（真实内容安全，默认启用）：
 * 对接阿里云内容安全（绿网）ROA 接口，对文本/图片做智能审核。
 * 采用标准 ROA HMAC-SHA1 签名（与 OSS 同源 RAM 账号），含超时/异常兜底。
 *
 * 注意：需配置 idlefish.oss.access-key / secret-key（绿网与 OSS 同源 RAM），
 * 以及 region（华东默认 cn-shanghai）。沙箱无外网，仅在生产环境开启并对真实凭证联调。
 */
@Service
public class RealContentAuditServiceImpl implements ContentAuditService {

    private static final String GREEN_HOST = "green.cn-shanghai.aliyuncs.com";
    private static final String GREEN_VERSION = "2018-05-09";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final IdlefishProperties props;

    public RealContentAuditServiceImpl(RestTemplate restTemplate, ObjectMapper objectMapper, IdlefishProperties props) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    public AuditResult audit(String title, String description, List<String> images) {
        try {
            AuditResult textResult = AuditResult.pass();
            if (title != null && !title.isBlank()) {
                textResult = scanText(title);
            }
            if (textResult.isPass() && description != null && !description.isBlank()) {
                textResult = scanText(description);
            }
            if (!textResult.isPass()) {
                return textResult;
            }
            if (images != null && !images.isEmpty()) {
                for (String url : images) {
                    AuditResult imgResult = scanImage(url);
                    if (!imgResult.isPass()) {
                        return imgResult;
                    }
                }
            }
            return AuditResult.pass();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // 真实审核链路异常：保守处置为转人工复核，避免误杀正常内容
            AuditResult r = new AuditResult();
            r.setPass(false);
            r.setSuggestion("review");
            r.setReason("内容安全服务调用异常，转人工复核");
            return r;
        }
    }

    private AuditResult scanText(String content) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "tasks", List.of(Map.of("dataId", UUID.randomUUID().toString(), "content", content)),
                "scenes", List.of("antispam")));
        JsonNode root = doScan("/green/text/scan", body);
        return parseResult(root, "antispam");
    }

    private AuditResult scanImage(String url) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "tasks", List.of(Map.of("dataId", UUID.randomUUID().toString(), "url", url)),
                "scenes", List.of("porn", "terrorism", "ad")));
        JsonNode root = doScan("/green/image/scan", body);
        return parseResult(root, "porn", "terrorism", "ad");
    }

    private JsonNode doScan(String resource, String body) throws Exception {
        String ak = props.getOss().getAccessKey();
        String sk = props.getOss().getSecretKey();
        String date = httpDate();
        String nonce = UUID.randomUUID().toString();
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String contentMd5 = SignUtils.base64(SignUtils.md5(bodyBytes));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.set("Content-Type", "application/json");
        headers.set("Content-MD5", contentMd5);
        headers.set("Date", date);
        headers.set("x-acs-version", GREEN_VERSION);
        headers.set("x-acs-action", resource.substring(resource.lastIndexOf('/') + 1));
        headers.set("x-acs-signature-nonce", nonce);
        headers.set("x-acs-signature-method", "HMAC-SHA1");
        headers.set("x-acs-date", date);
        headers.set("Host", GREEN_HOST);

        // 规范化头（仅 x-acs-*，按 key 升序）
        List<String> acsKeys = new ArrayList<>(headers.toSingleValueMap().keySet().stream()
                .filter(k -> k.toLowerCase().startsWith("x-acs-")).sorted().toList());
        StringBuilder signHeaders = new StringBuilder();
        for (String k : acsKeys) {
            signHeaders.append(k.toLowerCase()).append(":").append(headers.getFirst(k)).append("\n");
        }
        String stringToSign = "POST\napplication/json\n" + contentMd5 + "\napplication/json\n"
                + date + "\n" + signHeaders + resource;
        String signature = SignUtils.hmacSha1(stringToSign, sk + "&");
        headers.set("Authorization", "acs " + ak + ":" + signature);

        Map<String, String> headMap = headers.toSingleValueMap();
        String resp = HttpUtils.postJson(restTemplate, "https://" + GREEN_HOST + resource, headMap, body);
        return objectMapper.readTree(resp);
    }

    private AuditResult parseResult(JsonNode root, String... scenes) {
        if (root == null || !"ok".equals(root.path("code").asText(""))) {
            // 非明确通过：转人工复核
            AuditResult r = new AuditResult();
            r.setPass(false);
            r.setSuggestion("review");
            r.setReason("内容安全返回异常：" + root.path("msg").asText());
            return r;
        }
        JsonNode data = root.path("data");
        for (JsonNode task : data) {
            JsonNode results = task.path("results");
            for (JsonNode r : results) {
                String scene = r.path("scene").asText();
                boolean wanted = false;
                for (String s : scenes) {
                    if (s.equals(scene)) {
                        wanted = true;
                        break;
                    }
                }
                if (!wanted) {
                    continue;
                }
                String suggestion = r.path("suggestion").asText();
                if ("block".equals(suggestion)) {
                    AuditResult rej = new AuditResult();
                    rej.setPass(false);
                    rej.setSuggestion("block");
                    rej.setReason(r.path("label").asText("违规内容"));
                    return rej;
                }
                if ("review".equals(suggestion)) {
                    AuditResult rej = new AuditResult();
                    rej.setPass(false);
                    rej.setSuggestion("review");
                    rej.setReason("疑似违规，转人工复核");
                    return rej;
                }
            }
        }
        return AuditResult.pass();
    }

    private String httpDate() {
        SimpleDateFormat fmt = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        return fmt.format(new Date());
    }
}
