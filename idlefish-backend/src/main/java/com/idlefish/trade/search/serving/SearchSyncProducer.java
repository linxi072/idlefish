package com.idlefish.trade.search.serving;

import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.util.SignUtils;
import com.idlefish.trade.search.serving.dto.ItemSyncRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 搜推索引同步生产者（ITER-T04 / REQ-02）：商品数据变更（上架/编辑/审核通过/下架/删除）时，
 * 经 RocketMQ（阿里云 HTTP 接入点）异步投递 {@link ItemSyncRequest}，由搜推侧消费更新索引与特征。
 * <p>
 * 双写语义（过渡期，PRD §八.4）：内联模式下本地索引即时更新（{@link SearchSyncService}），
 * 本生产者同步投递消息保持远程搜推服务索引热备，切流时无需追全量。
 * <p>
 * 纪律（与 RocketMqDelayServiceImpl 一致）：
 * <ul>
 *   <li>未配置 {@code idlefish.mq.endpoint} 或 {@code searchService.syncEnabled=false} 时静默跳过（仅本地双写）；</li>
 *   <li>发送失败仅记数与告警日志，绝不阻断商品业务流程（best-effort，最终一致）；</li>
 *   <li>签名 HMAC-SHA1（阿里云 ROA 风格），与延时队列实现同构。</li>
 * </ul>
 */
@Component
public class SearchSyncProducer {

    private static final Logger log = LoggerFactory.getLogger(SearchSyncProducer.class);

    /** 消息类型标识（消费侧按此路由到搜推索引处理器）。 */
    public static final String MSG_TYPE_ITEM_SYNC = "item_sync";

    private final IdlefishProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsRegistry metrics;

    public SearchSyncProducer(IdlefishProperties props, RestTemplate restTemplate,
                              ObjectMapper objectMapper, MetricsRegistry metrics) {
        this.props = props;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    /** 是否启用消息投递（配置了接入点且总开关打开）。 */
    public boolean enabled() {
        IdlefishProperties.Mq mq = props.getMq();
        IdlefishProperties.SearchService svc = props.getSearchService();
        return mq != null && mq.getEndpoint() != null && !mq.getEndpoint().isBlank()
                && svc != null && svc.isSyncEnabled();
    }

    /**
     * 投递商品同步事件（best-effort）。
     *
     * @param itemId 商品 ID
     * @param event  UPSERT / REMOVE
     */
    public void publishItemEvent(Long itemId, String event) {
        if (itemId == null || !enabled()) {
            return;
        }
        try {
            doPublish(itemId, event);
            metrics.increment("search.sync.publish.success");
        } catch (Exception e) {
            metrics.increment("search.sync.publish.failure");
            log.warn("[search:sync] 商品同步消息投递失败（本地双写已生效，最终一致）itemId={} event={}: {}",
                    itemId, event, e.getMessage());
        }
    }

    private void doPublish(Long itemId, String event) {
        IdlefishProperties.Mq mq = props.getMq();
        long now = System.currentTimeMillis();
        ItemSyncRequest payload = new ItemSyncRequest(itemId, event, now);
        // 消息体：MSG_TYPE|itemId|event|occurredAt|json
        String body = MSG_TYPE_ITEM_SYNC + "|" + itemId + "|" + event + "|" + now + "|"
                + toJson(payload);
        String date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).format(new Date());
        String sign = SignUtils.hmacSha1("POST\n\napplication/json\n" + date + "\n", mq.getSecretKey());
        String auth = "MQ " + mq.getAccessKey() + ":" + sign;
        String url = mq.getEndpoint() + "/message?topic=" + mq.getTopic() + "&instanceId=" + mq.getInstanceId();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Date", date);
        headers.set("Authorization", auth);
        headers.set("ProducerId", mq.getInstanceId());
        Map<String, Object> msg = new HashMap<>(4);
        msg.put("topic", mq.getTopic());
        msg.put("body", body);
        String resp = restTemplate.postForObject(url, new HttpEntity<>(toJson(msg), headers), String.class);
        log.info("[search:sync] 商品同步消息已投递 itemId={} event={} resp={}", itemId, event, resp);
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
