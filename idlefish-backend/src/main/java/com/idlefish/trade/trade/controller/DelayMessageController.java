package com.idlefish.trade.trade.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.trade.service.RocketMqDelayServiceImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RocketMQ 推送消费入口（真实延时队列模式）：由 RocketMQ HTTP 消费者收到延时消息后回调。
 * 消息体约定为 "taskType|bizId|payload"，解析后交由对应 DelayTaskHandler 执行。
 * Mock（本地延时队列）模式下该端点无实际作用（getIfAvailable 为空，直接返回）。
 */
@RestController
@RequestMapping("/api/mq")
public class DelayMessageController {

    private final ObjectProvider<RocketMqDelayServiceImpl> rocketMqProvider;
    private final ObjectMapper objectMapper;

    public DelayMessageController(ObjectProvider<RocketMqDelayServiceImpl> rocketMqProvider, ObjectMapper objectMapper) {
        this.rocketMqProvider = rocketMqProvider;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/consume")
    public Result<Void> consume(@RequestBody String raw) {
        RocketMqDelayServiceImpl svc = rocketMqProvider.getIfAvailable();
        if (svc == null) {
            return Result.ok(); // Mock 模式：本地延时队列自行扫描执行，无需消费
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            String body = root.path("body").asText(null);
            if (body != null && !body.isBlank()) {
                String[] parts = body.split("\\|", 3);
                if (parts.length >= 2) {
                    svc.handleMessage(parts[0], parts[1], parts.length == 3 ? parts[2] : null);
                }
            }
        } catch (Exception e) {
            // 消费失败由 RocketMQ 重试；同时 DB 兜底扫描补偿，确保不丢
            return Result.fail(Code.BIZ_ERROR.getCode(), "消费失败: " + e.getMessage());
        }
        return Result.ok();
    }
}
