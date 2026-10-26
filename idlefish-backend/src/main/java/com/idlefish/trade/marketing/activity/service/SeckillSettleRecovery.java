package com.idlefish.trade.marketing.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.marketing.activity.SeckillSettleCommand;
import com.idlefish.trade.marketing.activity.entity.SeckillSettleTask;
import com.idlefish.trade.marketing.activity.mapper.SeckillSettleTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 直播秒杀结算兜底调度（ITER-F08 / REQ-07）。
 * <p>
 * 补偿 MQ 未消费到的结算指令，保证「缓存预扣 → 异步落库」最终一致且不丢。
 * 与 {@code RocketMqDelayServiceImpl.scanAndExecute} 同源设计；{@code LiveSeckillService.settle} 幂等，重复补偿安全。
 */
@Component
public class SeckillSettleRecovery {

    private static final Logger log = LoggerFactory.getLogger(SeckillSettleRecovery.class);

    private final SeckillSettleTaskMapper taskMapper;
    private final LiveSeckillService seckillService;
    private final ObjectMapper objectMapper;

    public SeckillSettleRecovery(SeckillSettleTaskMapper taskMapper, LiveSeckillService seckillService,
                                 ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.seckillService = seckillService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5000)
    public void scanAndSettle() {
        try {
            List<SeckillSettleTask> due = taskMapper.selectList(new LambdaQueryWrapper<SeckillSettleTask>()
                    .eq(SeckillSettleTask::getStatus, "pending")
                    .last("LIMIT 100"));
            for (SeckillSettleTask t : due) {
                SeckillSettleTask lock = new SeckillSettleTask();
                lock.setId(t.getId());
                lock.setStatus("processing");
                if (taskMapper.updateById(lock) == 0) {
                    continue; // 已被其他节点/消费方抢占
                }
                try {
                    SeckillSettleCommand cmd = objectMapper.readValue(t.getPayload(), SeckillSettleCommand.class);
                    seckillService.settle(cmd);
                    SeckillSettleTask done = new SeckillSettleTask();
                    done.setId(t.getId());
                    done.setStatus("done");
                    taskMapper.updateById(done);
                } catch (Exception e) {
                    log.warn("秒杀结算兜底执行失败 bizId={}: {}", t.getBizId(), e.getMessage());
                    SeckillSettleTask fail = new SeckillSettleTask();
                    fail.setId(t.getId());
                    fail.setStatus("failed");
                    fail.setRetryCount((t.getRetryCount() == null ? 0 : t.getRetryCount()) + 1);
                    taskMapper.updateById(fail);
                }
            }
        } catch (Exception e) {
            log.warn("秒杀结算兜底扫描异常: {}", e.getMessage());
        }
    }
}
