package com.idlefish.trade.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.im.entity.ImUnanswered;
import org.apache.ibatis.annotations.Mapper;

/**
 * 未命中问题池 Mapper（ITER-F06 / REQ-14）。
 */
@Mapper
public interface ImUnansweredMapper extends BaseMapper<ImUnanswered> {
}
