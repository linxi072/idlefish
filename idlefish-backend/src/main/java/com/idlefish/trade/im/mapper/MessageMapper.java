package com.idlefish.trade.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.im.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 消息 Mapper（PRD §E1：会话内消息序号）。
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /** 会话内当前最大序号（无消息返回 0）。 */
    @Select("SELECT COALESCE(MAX(seq), 0) FROM t_message WHERE conv_id = #{convId}")
    long maxSeqByConv(@Param("convId") String convId);
}
