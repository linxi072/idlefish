package com.idlefish.trade.common.idempotent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.common.idempotent.entity.IdempotentRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 幂等记录数据访问。
 */
@Mapper
public interface IdempotentMapper extends BaseMapper<IdempotentRecord> {
}
