package com.idlefish.trade.im.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.TraceRestTemplateInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 LLM 客户端（ITER-F06 / PRD-P2-F06 降级链路 REQ-12）。
 * <ul>
 *   <li>真实 HTTP 调用 {@code POST {baseUrl}/chat/completions}（Bearer 鉴权），专用 RestTemplate 超时可配；</li>
 *   <li>未启用 / 未配置 baseUrl 或 apiKey 时 {@link #isAvailable()}=false，链路直接走模板回落；</li>
 *   <li>所有异常（超时 / 非 2xx / 解析失败 / 空内容）统一返回 {@code null}，由上层记录降级埋点；</li>
 *   <li>包级构造器供单测注入 Mock RestTemplate。</li>
 * </ul>
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

    private final IdlefishProperties props;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public OpenAiCompatibleLlmClient(IdlefishProperties props, ObjectMapper objectMapper) {
        this(props, objectMapper, buildRestTemplate(props));
    }

    /** 包级构造器：供单测注入 Mock {@link RestTemplate}（生产走上一构造器自建专用实例）。 */
    OpenAiCompatibleLlmClient(IdlefishProperties props, ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate buildRestTemplate(IdlefishProperties props) {
        IdlefishProperties.ImAssistant.Llm cfg = props.getImAssistant().getLlm();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(2000L));
        factory.setReadTimeout(Duration.ofMillis(Math.max(500L, cfg.getTimeoutMs())));
        RestTemplate rt = new RestTemplate(factory);
        rt.setInterceptors(List.of(new TraceRestTemplateInterceptor()));
        return rt;
    }

    @Override
    public boolean isAvailable() {
        IdlefishProperties.ImAssistant.Llm cfg = props.getImAssistant().getLlm();
        return cfg.isEnabled() && notBlank(cfg.getBaseUrl()) && notBlank(cfg.getApiKey()) && notBlank(cfg.getModel());
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        if (!isAvailable()) {
            return null;
        }
        IdlefishProperties.ImAssistant.Llm cfg = props.getImAssistant().getLlm();
        String url = cfg.getBaseUrl().trim().replaceAll("/+$", "") + "/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                Map.of("role", "user", "content", userMessage == null ? "" : userMessage)));
        body.put("temperature", 0.2);
        body.put("max_tokens", 300);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(cfg.getApiKey().trim());
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.debug("[im-assistant] LLM 非 2xx 或空响应 status={}", resp.getStatusCode());
                return null;
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isTextual() && !content.asText().isBlank()) {
                return content.asText().trim();
            }
            return null;
        } catch (Exception e) {
            // REQ-12：LLM 失败回落模板，不阻塞；此处仅记录debug，降级埋点由上层记录
            log.debug("[im-assistant] LLM 调用失败，回落模板: {}", e.toString());
            return null;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
