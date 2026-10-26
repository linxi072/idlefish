package com.idlefish.trade.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.im.entity.ImKnowledge;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能助手知识库 Mapper（ITER-F06 / REQ-11 品类话术优先）。
 */
@Mapper
public interface ImKnowledgeMapper extends BaseMapper<ImKnowledge> {
}
