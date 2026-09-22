package com.idlefish.trade.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;

import java.io.Serializable;

/**
 * 后台管理员（RBAC）：角色决定可操作权限。
 */
@TableName("t_admin_user")
public class AdminUser extends BaseEntity implements Serializable {

    private String username;
    private String password;   // 演示用明文；生产须加盐哈希
    private String role;       // SUPER / OPERATOR / FINANCE
    private String nickname;
    private Integer status = 1; // 1 启用 / 0 禁用

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
