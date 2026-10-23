package com.idlefish.trade.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.event.EventRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 领域事件存储持久化（ITER-T06 / REQ-09 可回放）。
 */
@Mapper
public interface EventRecordMapper extends BaseMapper<EventRecord> {

    /** 按事件 ID 查询。 */
    @Select("SELECT * FROM t_domain_event WHERE event_id = #{eventId}")
    EventRecord selectByEventId(@Param("eventId") String eventId);
}
