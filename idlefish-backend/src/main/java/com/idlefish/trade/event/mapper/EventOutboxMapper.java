package com.idlefish.trade.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.event.EventOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 事件发件箱持久化（ITER-T06）。
 */
@Mapper
public interface EventOutboxMapper extends BaseMapper<EventOutbox> {

    /** 按事件 ID 查询（事件 ID 全局唯一）。 */
    @Select("SELECT * FROM t_event_outbox WHERE event_id = #{eventId}")
    EventOutbox selectByEventId(@Param("eventId") String eventId);

    /**
     * 拉取到期待分发批次（REQ-06 定时补偿）：
     * PENDING（含重试到期）与 FAILED（到期重扫），按下次重试时间升序，限定批次大小。
     */
    @Select("SELECT * FROM t_event_outbox " +
            "WHERE (status = 'PENDING' AND next_retry_at <= #{now}) " +
            "   OR (status = 'FAILED' AND next_retry_at <= #{now}) " +
            "ORDER BY next_retry_at ASC LIMIT #{limit}")
    List<EventOutbox> selectDueBatch(@Param("now") java.time.LocalDateTime now, @Param("limit") int limit);

    /** 按状态统计数量（治理端点 Outbox 概览用）。 */
    @Select("SELECT status, COUNT(*) AS cnt FROM t_event_outbox GROUP BY status")
    List<java.util.Map<String, Object>> countByStatus();

    /** 统计死信数量（REQ-08 死信告警）。 */
    @Select("SELECT COUNT(*) FROM t_event_outbox WHERE status = 'DEAD'")
    long countDead();
}
