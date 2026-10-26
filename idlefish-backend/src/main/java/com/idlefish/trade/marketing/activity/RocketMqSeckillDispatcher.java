package com.idlefish.trade.marketing.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.util.SignUtils;
import com.idlefish.trade.marketing.activity.entity.SeckillSettleTask;
import com.idlefish.trade.marketing.activity.mapper.SeckillSettleTaskMapper;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 直播秒杀结算分发（真实 RocketMQ HTTP 接入点，默认启用 + DB 兜底）。
 * <p>
 * 与 {@code RocketMqDelayServiceImpl} 同源：无论 MQ 是否成功均先落 DB 兜底任务，
 * 再由 {@code SeckillSettleRecovery} 补偿，保证「缓存预扣 → 异步落库」最终一致（REQ-07）。
 */
@Component
public class RocketMqSeckillDispatcher implements SeckillAsyncDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqSeckillDispatcher.class);
    private static final String TASK_TYPE = "SECKILL_SETTLE";

    private final IdlefishProperties props;
    private final RestTemplate restTemplate;
    private final SeckillSettleTaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    public RocketMqSeckillDispatcher(IdlefishProperties props, RestTemplate restTemplate,
                                     SeckillSettleTaskMapper taskMapper, ObjectMapper objectMapper) {
        this.props = props;
        this.restTemplate = restTemplate;
        this.taskMapper = taskMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void dispatch(SeckillSettleCommand cmd) {
        // 1) 始终落库兜底任务，保证不丢
        persistFallback(cmd);
        // 2) 尝试发送 RocketMQ 消息（失败仅告警，由兜底调度补偿）
        try {
            sendMq(cmd);
        } catch (Exception e) {
            log.warn("秒杀结算 MQ 发送失败，已降级为 DB 兜底: {}", e.getMessage());
        }
    }

    private void persistFallback(SeckillSettleCommand cmd) {
        try {
            SeckillSettleTask t = new SeckillSettleTask();
            t.setTaskType(TASK_TYPE);
            t.setBizId(cmd.getIdempotencyKey());
            t.setPayload(objectMapper.writeValueAsString(cmd));
            t.setStatus("pending");
            t.setNextExecuteAt(java.time.LocalDateTime.now());
            t.setRetryCount(0);
            taskMapper.insert(t);
        } catch (Exception e) {
            log.error("秒杀结算兜底落库失败，存在丢失风险: {}", e.getMessage());
        }
    }

    private void sendMq(SeckillSettleCommand cmd) {
        IdlefishProperties.Mq mq = props.getMq();
        if (mq.getEndpoint() == null || mq.getEndpoint().isBlank()) {
            return; // 未配置则不发送（仅 DB 兜底）
        }
        String body = TASK_TYPE + "|" + cmd.getIdempotencyKey() + "|" + cmd.getBizId();
        String date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).format(new Date());
        String sign = SignUtils.hmacSha1("POST\n\napplication/json\n" + date + "\n", mq.getSecretKey());
        String auth = "MQ " + mq.getAccessKey() + ":" + sign;
        String url = mq.getEndpoint() + "/message?topic=" + mq.getTopic()
                + "&instanceId=" + mq.getInstanceId();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Date", date);
        headers.set("Authorization", auth);
        headers.set("ProducerId", mq.getInstanceId());
        Map<String, Object> msg = new HashMap<>(4);
        msg.put("topic", mq.getTopic());
        msg.put("body", body);
        try {
            String resp = restTemplate.postForObject(url, new HttpEntity<>(objectMapper.writeValueAsString(msg), headers), String.class);
            log.info("秒杀结算 MQ 已提交 bizId={} resp={}", cmd.getIdempotencyKey(), resp);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("秒杀结算指令序列化失败", e);
        }
    }
}
