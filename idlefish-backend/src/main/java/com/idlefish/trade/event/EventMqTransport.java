package com.idlefish.trade.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.util.SignUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 事件 MQ 桥接传输（ITER-T06 可选链路）。
 * <p>
 * 本地监听器消费成功后，可将事件信封镜像至阿里云 RocketMQ（HTTP 接入，HMAC-SHA1 签名，
 * 与 {@code RocketMqDelayServiceImpl} 同一套凭据 {@code idlefish.mq.*}），
 * 供未来拆分的下游服务订阅。默认关闭（{@code idlefish.event.mq-enabled=false}）：
 * 单体阶段本地分发已覆盖全部消费语义，MQ 仅作渐进拆分的过渡通道。
 * <ul>
 *   <li>凭据缺失 / 未启用：静默跳过（不报错，本地分发语义不受影响）；</li>
 *   <li>发送失败：打点 {@code event.mq.failure} 并记日志，不影响本地消费结果（best-effort）。</li>
 * </ul>
 */
@Slf4j
@Component
public class EventMqTransport {

    private final RestTemplate restTemplate;
    private final IdlefishProperties props;
    private final MetricsRegistry metrics;
    private final ObjectMapper objectMapper;

    public EventMqTransport(RestTemplate restTemplate, IdlefishProperties props,
                            MetricsRegistry metrics, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    /** 是否具备发送条件（显式启用且凭据齐备）。 */
    public boolean isAvailable() {
        IdlefishProperties.Event ev = props.getEvent();
        IdlefishProperties.Mq mq = props.getMq();
        return ev.isMqEnabled()
                && mq.getEndpoint() != null && !mq.getEndpoint().isBlank()
                && mq.getTopic() != null && !mq.getTopic().isBlank();
    }

    /**
     * 将事件信封镜像至 MQ（即时投递）。
     *
     * @return true 已成功提交；false 未启用 / 失败（均为 best-effort，不抛异常）
     */
    public boolean send(DomainEvent event, String payloadJson) {
        if (!isAvailable()) {
            return false;
        }
        try {
            IdlefishProperties.Mq mq = props.getMq();
            String date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                    .format(new Date());
            String sign = SignUtils.hmacSha1("POST\n\napplication/json\n" + date + "\n", mq.getSecretKey());
            String auth = "MQ " + mq.getAccessKey() + ":" + sign;
            long deliverAt = System.currentTimeMillis();
            String url = mq.getEndpoint() + "/message?topic=" + mq.getTopic()
                    + "&startDeliverTime=" + deliverAt + "&instanceId=" + mq.getInstanceId();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Date", date);
            headers.set("Authorization", auth);
            headers.set("ProducerId", mq.getInstanceId());

            Map<String, Object> body = new HashMap<>(4);
            body.put("eventId", event.getEventId());
            body.put("eventType", event.getEventType());
            body.put("version", event.getVersion());
            body.put("occurredAt", event.getOccurredAt() == null ? null : event.getOccurredAt().toString());
            body.put("aggregateId", event.getAggregateId());
            body.put("traceId", event.getTraceId());
            body.put("payload", payloadJson);

            restTemplate.postForObject(url, new HttpEntity<>(toJson(body), headers), String.class);
            metrics.increment("event.mq.sent");
            log.info("[event] mirrored to mq, eventId={}, type={}", event.getEventId(), event.getEventType());
            return true;
        } catch (RuntimeException e) {
            metrics.increment("event.mq.failure");
            log.warn("[event] mq mirror failed (best-effort), eventId={}", event.getEventId(), e);
            return false;
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("mq body serialize failed", e);
        }
    }
}
