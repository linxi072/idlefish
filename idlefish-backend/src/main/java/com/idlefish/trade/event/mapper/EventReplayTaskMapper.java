package com.idlefish.trade.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.event.EventReplayTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 事件回放任务持久化（ITER-T06 / REQ-13）。
 */
@Mapper
public interface EventReplayTaskMapper extends BaseMapper<EventReplayTask> {

    /** 按回放单号查询（回放单号唯一）。 */
    @Select("SELECT * FROM t_event_replay WHERE replay_no = #{replayNo}")
    EventReplayTask selectByReplayNo(@Param("replayNo") String replayNo);
}
