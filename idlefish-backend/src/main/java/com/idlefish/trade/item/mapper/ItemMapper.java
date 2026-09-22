package com.idlefish.trade.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.item.entity.Item;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ItemMapper extends BaseMapper<Item> {
}
