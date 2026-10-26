package com.idlefish.trade.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.im.entity.ImAutoReply;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能助手自动应答记录 Mapper（ITER-F06 / REQ-10 来源可审计）。
 */
@Mapper
public interface ImAutoReplyMapper extends BaseMapper<ImAutoReply> {
}
