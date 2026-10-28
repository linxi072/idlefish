package com.idlefish.trade.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 通用关联分配 DTO（角色↔菜单、管理员↔角色复用）。
 * id 为主实体 ID（角色 ID / 管理员 ID），ids 为被关联实体 ID 集合。
 */
@Data
public class AssignDTO {

    @NotNull(message = "主实体 ID 不能为空")
    private Long id;

    @NotNull(message = "被关联实体 ID 集合不能为空")
    private List<Long> ids;
}
