package com.idlefish.trade.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.idlefish.trade.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 查询尚未建立成长值账户的存量用户 ID（LEFT JOIN 反查 t_user_growth 缺失行）。
     * 用于 ITER-F07 存量账户初始化（PRD §12.4 遗留项②），返回至多 {@code limit} 条。
     */
    @Select("SELECT u.id FROM t_user u LEFT JOIN t_user_growth g ON u.id = g.user_id " +
            "WHERE g.id IS NULL ORDER BY u.id ASC LIMIT #{limit}")
    List<Long> selectMissingGrowthUserIds(int limit);
}
