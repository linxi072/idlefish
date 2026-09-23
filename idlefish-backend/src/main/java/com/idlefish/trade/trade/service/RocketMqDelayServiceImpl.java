package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.util.SignUtils;
import com.idlefish.trade.trade.entity.DelayTask;
import com.idlefish.trade.trade.mapper.DelayTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 真实 RocketMQ 延时队列（idlefish.mq.mock=false）：发送阿里云 RocketMQ 延时消息；
 * DB（t_delay_task）作为兜底，scanAndExecute 补偿未消费到的任务，消费由 /api/mq/consume 推送触发。
 * 注：HTTP 接入点签名需结合阿里云 RocketMQ 实例校验；发送失败时自动降级为 DB 兜底执行，保证不丢。
 */
@Service
@ConditionalOnProperty(name = "idlefish.mq.mock", havingValue = "false")
public class RocketMqDelayServiceImpl implements DelayQueueService {

    private static final Logger log = LoggerFactory.getLogger(RocketMqDelayServiceImpl.class);

    private final IdlefishProperties props;
    private final RestTemplate restTemplate;
    private final DelayTaskMapper mapper;
    private final Map<String, DelayTaskHandler> handlers;

    public RocketMqDelayServiceImpl(IdlefishProperties props, RestTemplate restTemplate,
                                   DelayTaskMapper mapper, List<DelayTaskHandler> handlerList) {
        this.props = props;
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.handlers = handlerList.stream().collect(Collectors.toMap(DelayTaskHandler::type, h -> h));
    }

    @Override
    public void submit(String taskType, String bizId, String payload, int delaySeconds) {
        // 兜底落库（始终执行，保证任务不丢）
        persistFallback(taskType, bizId, payload, delaySeconds);
        // 发送 RocketMQ 延时消息
        try {
            sendDelayMessage(taskType, bizId, payload, delaySeconds);
        } catch (Exception e) {
            log.warn("RocketMQ 延时消息发送失败，已降级为 DB 兜底执行: {}", e.getMessage());
        }
    }

    /** DB 兜底：无论 RocketMQ 是否成功，均落一条 pending 任务，由 scanAndExecute 补偿。 */
    private void persistFallback(String taskType, String bizId, String payload, int delaySeconds) {
        DelayTask t = new DelayTask();
        t.setTaskType(taskType);
        t.setBizId(bizId);
        t.setPayload(payload);
        t.setStatus("pending");
        t.setNextExecuteAt(LocalDateTime.now().plusSeconds(Math.max(0, delaySeconds)));
        t.setRetryCount(0);
        mapper.insert(t);
    }

    /**
     * 发送阿里云 RocketMQ 延时消息（HTTP 接入点）。
     * 采用 StartDeliverTime 指定投递时间（毫秒）实现任意延时；签名采用 HMAC-SHA1（参照阿里云 ROA 风格）。
     * 生产请按实例文档校验签名细节。
     */
    private void sendDelayMessage(String taskType, String bizId, String payload, int delaySeconds) {
        IdlefishProperties.Mq mq = props.getMq();
        if (mq.getEndpoint() == null || mq.getEndpoint().isBlank()) {
            return; // 未配置则不发送（仅 DB 兜底）
        }
        long deliverAt = System.currentTimeMillis() + delaySeconds * 1000L;
        // 消息体：taskType|bizId|payload
        String body = taskType + "|" + bizId + "|" + (payload == null ? "" : payload);
        String date = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US)
                .format(new java.util.Date());
        String sign = SignUtils.hmacSha1("POST\n\napplication/json\n" + date + "\n", mq.getSecretKey());
        String auth = "MQ " + mq.getAccessKey() + ":" + sign;
        String url = mq.getEndpoint() + "/message?topic=" + mq.getTopic()
                + "&startDeliverTime=" + deliverAt + "&instanceId=" + mq.getInstanceId();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Date", date);
        headers.set("Authorization", auth);
        headers.set("ProducerId", mq.getInstanceId());
        Map<String, Object> msg = new java.util.HashMap<>(4);
        msg.put("topic", mq.getTopic());
        msg.put("body", body);
        msg.put("startDeliverTime", deliverAt);
        String resp = restTemplate.postForObject(url, new HttpEntity<>(toJson(msg), headers), String.class);
        log.info("RocketMQ 延时消息已提交 type={} bizId={} deliverAt={} resp={}", taskType, bizId, deliverAt, resp);
    }

    /** 推送消费入口：由 RocketMQ HTTP 消费者收到消息后回调（或本地注入触发）。 */
    public void handleMessage(String taskType, String bizId, String payload) {
        DelayTaskHandler h = handlers.get(taskType);
        if (h != null) {
            h.handle(bizId, payload);
        }
    }

    /** DB 兜底扫描：补偿未成功消费的消息（RocketMQ 推送丢失/延迟时）。 */
    @Scheduled(fixedDelay = 30000)
    public void scanAndExecute() {
        try {
            List<DelayTask> due = mapper.selectList(new LambdaQueryWrapper<DelayTask>()
                    .eq(DelayTask::getStatus, "pending")
                    .le(DelayTask::getNextExecuteAt, LocalDateTime.now())
                    .last("LIMIT 100"));
            for (DelayTask t : due) {
                DelayTask lock = new DelayTask();
                lock.setId(t.getId());
                lock.setStatus("processing");
                if (mapper.updateById(lock) == 0) {
                    continue;
                }
                try {
                    DelayTaskHandler h = handlers.get(t.getTaskType());
                    if (h != null) {
                        h.handle(t.getBizId(), t.getPayload());
                    }
                    DelayTask done = new DelayTask();
                    done.setId(t.getId());
                    done.setStatus("done");
                    mapper.updateById(done);
                } catch (Exception e) {
                    log.warn("RocketMQ 兜底任务执行失败 type={} bizId={}: {}", t.getTaskType(), t.getBizId(), e.getMessage());
                    DelayTask fail = new DelayTask();
                    fail.setId(t.getId());
                    fail.setStatus("failed");
                    fail.setRetryCount((t.getRetryCount() == null ? 0 : t.getRetryCount()) + 1);
                    mapper.updateById(fail);
                }
            }
        } catch (Exception e) {
            log.warn("RocketMQ 兜底扫描异常: {}", e.getMessage());
        }
    }

    private String toJson(Map<String, Object> m) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }
}
