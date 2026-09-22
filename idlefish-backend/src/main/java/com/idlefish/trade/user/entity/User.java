package com.idlefish.trade.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户（PRD §11.1）。手机号加密存储于 {@code phone} 字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class User extends BaseEntity {

    /** 微信 openid */
    private String wxOpenid;

    /** 微信 unionid */
    private String wxUnionid;

    /** 手机号（加密存储） */
    private String phone;

    private String nickname;

    private String avatar;

    /** 简版信用分（认证完整度 + 注册时长 + 履约率 + 评价，P1 启用） */
    private Integer creditScore;

    /** 0 正常 / 1 封禁 */
    private Integer status;

    /** 实名认证状态：0 未认证 / 1 已认证 */
    private Integer realNameVerified;
}
