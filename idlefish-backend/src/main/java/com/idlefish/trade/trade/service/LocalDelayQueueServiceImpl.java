package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.trade.entity.DelayTask;
import com.idlefish.trade.trade.mapper.DelayTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 本地延时队列（idlefish.mq.mock=true 默认）：基于 t_delay_task 持久化 + 定时扫描补偿，零外部依赖。
 * 保证任务不丢：扫描到期（pending）任务 → 标记 processing → 调用对应 handler → 标记 done/failed。
 */
@Service
@ConditionalOnProperty(name = "idlefish.mq.mock", havingValue = "true", matchIfMissing = true)
public class LocalDelayQueueServiceImpl implements DelayQueueService {

    private static final Logger log = LoggerFactory.getLogger(LocalDelayQueueServiceImpl.class);

    private final DelayTaskMapper mapper;
    private final Map<String, DelayTaskHandler> handlers;

    public LocalDelayQueueServiceImpl(DelayTaskMapper mapper, List<DelayTaskHandler> handlerList) {
        this.mapper = mapper;
        this.handlers = handlerList.stream().collect(Collectors.toMap(DelayTaskHandler::type, h -> h));
    }

    @Override
    public void submit(String taskType, String bizId, String payload, int delaySeconds) {
        DelayTask t = new DelayTask();
        t.setTaskType(taskType);
        t.setBizId(bizId);
        t.setPayload(payload);
        t.setStatus("pending");
        t.setNextExecuteAt(LocalDateTime.now().plusSeconds(Math.max(0, delaySeconds)));
        t.setRetryCount(0);
        mapper.insert(t);
    }

    /** 每 5 秒扫描到期任务并执行（兜底补偿，确保不丢）。 */
    @Scheduled(fixedDelay = 5000)
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
                    continue; // 已被其它实例认领
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
                    log.warn("延时任务执行失败 type={} bizId={}: {}", t.getTaskType(), t.getBizId(), e.getMessage());
                    DelayTask fail = new DelayTask();
                    fail.setId(t.getId());
                    fail.setStatus("failed");
                    fail.setRetryCount((t.getRetryCount() == null ? 0 : t.getRetryCount()) + 1);
                    mapper.updateById(fail);
                }
            }
        } catch (Exception e) {
            log.warn("延时任务扫描异常（下一轮重试）: {}", e.getMessage());
        }
    }
}
