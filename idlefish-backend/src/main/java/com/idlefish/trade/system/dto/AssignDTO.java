package com.idlefish.trade.system.dto;

import lombok.Data;

import java.util.List;

/**
 * 通用关联分配 DTO（角色↔菜单、管理员↔角色复用）。
 * id 为主实体 ID（角色 ID / 管理员 ID），ids 为被关联实体 ID 集合。
 */
@Data
public class AssignDTO {

    private Long id;
    private List<Long> ids;
}
