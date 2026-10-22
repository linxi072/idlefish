-- 闲置集 C2C 二手交易小程序后端 —— MySQL 初始化 DDL（生产数据源）
-- 引擎 InnoDB，字符集 utf8mb4；索引以内联 KEY 形式定义，保证 sql.init.mode=always 可重复执行。
-- created_at/updated_at 用 DATETIME（不限 2038）。

CREATE TABLE IF NOT EXISTS t_user (
    id               BIGINT       PRIMARY KEY,
    wx_openid        VARCHAR(64),
    wx_unionid       VARCHAR(64),
    phone            VARCHAR(128),
    nickname         VARCHAR(64),
    avatar           VARCHAR(255),
    credit_score     INT,
    status           INT          DEFAULT 0,
    real_name_verified INT        DEFAULT 0,
    created_at       DATETIME,
    updated_at       DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_address (
    id               BIGINT       PRIMARY KEY,
    user_id          BIGINT,
    receiver_name    VARCHAR(64),
    phone            VARCHAR(128),
    province         VARCHAR(64),
    city             VARCHAR(64),
    district         VARCHAR(64),
    detail           VARCHAR(255),
    is_default       INT          DEFAULT 0,
    deleted          INT          DEFAULT 0,
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_address_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_category (
    id               BIGINT       PRIMARY KEY,
    parent_id        BIGINT       DEFAULT 0,
    name             VARCHAR(64),
    icon             VARCHAR(255),
    level            INT          DEFAULT 1,
    sort             INT          DEFAULT 0,
    is_leaf          INT          DEFAULT 1,
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_category_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_item (
    id               BIGINT       PRIMARY KEY,
    seller_id        BIGINT,
    category_id      BIGINT,
    title            VARCHAR(128),
    description      TEXT,
    price            BIGINT,
    original_price   BIGINT,
    images           TEXT,
    video_url        VARCHAR(255),
    condition_level  INT,
    stock            INT          DEFAULT 1,
    province         VARCHAR(64),
    city             VARCHAR(64),
    freight          BIGINT       DEFAULT 0,
    status           VARCHAR(32),
    audit_status     VARCHAR(32),
    audit_reason     VARCHAR(255),
    view_count       INT          DEFAULT 0,
    like_count       INT          DEFAULT 0,
    fav_count        INT          DEFAULT 0,
    inspection_status VARCHAR(16)  DEFAULT 'NONE' COMMENT '验货标识：NONE/INSPECTING/PASSED/REJECTED',
    delivery_type    VARCHAR(16)  DEFAULT 'EXPRESS' COMMENT '履约方式（ITER-F05/REQ-01）：EXPRESS/SELF_PICKUP/LOCAL_DELIVERY',
    version          INT          DEFAULT 0,
    deleted          INT          DEFAULT 0,
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_item_status (status, category_id, created_at),
    KEY idx_item_seller (seller_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_order (
    id               BIGINT       PRIMARY KEY,
    order_no         VARCHAR(32)  NOT NULL,
    buyer_id         BIGINT,
    seller_id        BIGINT,
    item_id          BIGINT,
    sku_snapshot     TEXT,
    quantity         INT          DEFAULT 1,
    unit_price       BIGINT,
    total_amount     BIGINT,
    freight          BIGINT,
    pay_amount       BIGINT,
    status           VARCHAR(32),
    version          INT          DEFAULT 0,
    address_snapshot TEXT,
    remark           VARCHAR(255),
    pay_no           VARCHAR(32),
    logistics_no     VARCHAR(64),
    close_type       VARCHAR(32),
    user_coupon_id   BIGINT,
    discount_amount  BIGINT       DEFAULT 0,
    used_point       BIGINT       DEFAULT 0,   -- F-13.1：下单使用的积分
    point_discount   BIGINT       DEFAULT 0,   -- F-13.1：积分抵扣金额（分）
    activity_id      BIGINT,                  -- F-13.3：参与活动 ID（拼团/秒杀）
    delivery_type    VARCHAR(16)  DEFAULT 'EXPRESS' COMMENT '履约方式（ITER-F05/REQ-06/10）：EXPRESS/SELF_PICKUP/LOCAL_DELIVERY',
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_order_pay_no (pay_no),
    KEY idx_order_buyer (buyer_id, status),
    KEY idx_order_seller (seller_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_pay_order (
    id               BIGINT       PRIMARY KEY,
    pay_no           VARCHAR(32)  NOT NULL,
    order_no         VARCHAR(32),
    buyer_id         BIGINT,
    amount           BIGINT,
    channel          VARCHAR(32),
    transaction_id   VARCHAR(64),
    status           VARCHAR(32),
    paid_at          DATETIME,
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_pay_no (pay_no),
    KEY idx_pay_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_refund (
    id               BIGINT       PRIMARY KEY,
    refund_no        VARCHAR(32)  NOT NULL,
    order_no         VARCHAR(32),
    pay_no           VARCHAR(32),
    buyer_id         BIGINT,
    seller_id        BIGINT,
    item_id          BIGINT,
    type             VARCHAR(32),
    amount           BIGINT,
    reason           VARCHAR(255),
    status           VARCHAR(32),
    logistics_no     VARCHAR(64),
    auto_agree_at    DATETIME,
    platform_at      DATETIME,
    refund_at        DATETIME,
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_refund_no (refund_no),
    KEY idx_refund_order (order_no),
    KEY idx_refund_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_settlement (
    id               BIGINT       PRIMARY KEY,
    settle_no        VARCHAR(32)  NOT NULL,
    seller_id        BIGINT,
    order_no         VARCHAR(32)  NOT NULL,
    amount           BIGINT,
    platform_fee     BIGINT,
    status           VARCHAR(32),
    settle_at        DATETIME,
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_settle_no (settle_no),
    UNIQUE KEY uk_settle_order (order_no),
    KEY idx_settle_seller (seller_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_fund_flow (
    id               BIGINT       PRIMARY KEY,
    biz_no           VARCHAR(32)  NOT NULL,
    user_id          BIGINT,
    direction        VARCHAR(16),
    amount           BIGINT,
    type             VARCHAR(32),
    balance_after    BIGINT,
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_fund_biz (biz_no),
    KEY idx_fund_user (user_id, direction)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_conversation (
    id               BIGINT       PRIMARY KEY,
    conv_id          VARCHAR(64)  NOT NULL,
    buyer_id         BIGINT,
    seller_id        BIGINT,
    item_id          BIGINT,
    last_message     VARCHAR(255),
    last_sender_id   BIGINT,
    buyer_unread     INT          DEFAULT 0,
    seller_unread    INT          DEFAULT 0,
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_conv_id (conv_id),
    KEY idx_conv_buyer (buyer_id),
    KEY idx_conv_seller (seller_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_message (
    id               BIGINT       PRIMARY KEY,
    conv_id          VARCHAR(64),
    sender_id        BIGINT,
    receiver_id      BIGINT,
    type             VARCHAR(16),
    content          VARCHAR(1024),
    seq              BIGINT       DEFAULT 0,
    read_flag        INT          DEFAULT 0,
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_msg_conv (conv_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_track_event (
    id               BIGINT       PRIMARY KEY,
    user_id          BIGINT,
    event            VARCHAR(32),
    biz_id           VARCHAR(64),
    ext              VARCHAR(512),
    device_id        VARCHAR(64),
    ip               VARCHAR(64),
    ua               VARCHAR(512),
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_track_user (user_id, event),
    KEY idx_track_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_risk_event (
    id               BIGINT       PRIMARY KEY,
    user_id          BIGINT,
    rule_code        VARCHAR(32),
    rule_name        VARCHAR(64),
    level            VARCHAR(16),
    biz_type         VARCHAR(32),
    biz_id           VARCHAR(64),
    status           VARCHAR(16),
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_risk_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_risk_rule (
    id               BIGINT       PRIMARY KEY,
    code             VARCHAR(32)  NOT NULL COMMENT '规则码，唯一，如 R1_HIGH_PUBLISH',
    name             VARCHAR(64)  NOT NULL COMMENT '展示名',
    type             VARCHAR(32)  NOT NULL COMMENT '规则类型，见 RiskRuleType',
    level            VARCHAR(16)  COMMENT '默认命中等级 low/mid/high',
    enabled          INT          DEFAULT 1 COMMENT '1 启用 / 0 停用',
    scope            VARCHAR(16)  COMMENT '维度 user/device',
    threshold        BIGINT       DEFAULT 0 COMMENT '计数/金额(分)/频次阈值，语义随 type',
    window_min       INT          DEFAULT 1 COMMENT '统计窗口(分钟)',
    priority         INT          DEFAULT 0 COMMENT '评估优先级，升序',
    description      VARCHAR(255),
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_risk_rule_code (code),
    KEY idx_risk_rule_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_audit_log (
    id               BIGINT       PRIMARY KEY,
    operator_id      BIGINT,
    action           VARCHAR(64),
    target_type      VARCHAR(32),
    target_id        VARCHAR(64),
    detail           VARCHAR(512),
    created_at       DATETIME,
    updated_at       DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_admin_user (
    id               BIGINT       PRIMARY KEY,
    username         VARCHAR(64),
    password         VARCHAR(128),
    role             VARCHAR(32)  COMMENT '遗留主角色字段（展示用）；权限以 t_admin_user_role + t_role_menu 为准',
    nickname         VARCHAR(64),
    org_id           BIGINT       COMMENT '所属机构/部门',
    status           INT          DEFAULT 1,
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_admin_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_favorite (
    id               BIGINT       PRIMARY KEY,
    user_id          BIGINT,
    item_id          BIGINT,
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_user_item (user_id, item_id),
    KEY idx_favorite_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_attr_template (
    id               BIGINT       PRIMARY KEY,
    category_id      BIGINT,
    name             VARCHAR(64),
    options          VARCHAR(512),
    required         INT          DEFAULT 0,
    sort             INT          DEFAULT 0,
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_attr_cat (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_bargain (
    id               BIGINT       PRIMARY KEY,
    conv_id          VARCHAR(64),
    item_id          BIGINT,
    buyer_id         BIGINT,
    seller_id        BIGINT,
    origin_price     BIGINT,
    offer_price      BIGINT,
    status           VARCHAR(16),
    expire_at        DATETIME,
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_bargain_conv (conv_id),
    KEY idx_bargain_seller (seller_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_withdrawal (
    id               BIGINT       PRIMARY KEY,
    user_id          BIGINT,
    amount           BIGINT,
    account          VARCHAR(128),
    status           VARCHAR(16),
    done_at          DATETIME,
    transfer_no      VARCHAR(64)  DEFAULT NULL COMMENT '渠道出款单号（微信 transfer_bill_no / out_bill_no）',
    fail_reason      VARCHAR(255) DEFAULT NULL COMMENT '出款失败原因（审批通过但真实出款失败时记录）',
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_withdrawal_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_item_status_log (
    id               BIGINT       PRIMARY KEY,
    item_id          BIGINT,
    from_status      VARCHAR(32),
    to_status        VARCHAR(32),
    operator_id      BIGINT,
    remark           VARCHAR(255),
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_item_status_log (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_device_fingerprint (
    id               BIGINT       PRIMARY KEY,
    device_id        VARCHAR(64)  NOT NULL,
    user_id          BIGINT       NOT NULL,
    fp_hash          VARCHAR(64),
    first_seen       DATETIME,
    last_seen        DATETIME,
    status           VARCHAR(16)  DEFAULT 'normal',
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_device_user (device_id, user_id),
    KEY idx_fp_device (device_id),
    KEY idx_fp_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_logistics (
    id               BIGINT       PRIMARY KEY,
    order_no         VARCHAR(32),
    logistics_no     VARCHAR(64),
    company          VARCHAR(32),
    status           VARCHAR(16)  DEFAULT 'transport',
    detail_json      TEXT,
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_logi_order (order_no),
    KEY idx_logi_no (logistics_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_delay_task (
    id               BIGINT       PRIMARY KEY,
    task_type        VARCHAR(32)  NOT NULL,
    biz_id           VARCHAR(64),
    payload          VARCHAR(1024),
    status           VARCHAR(16)  DEFAULT 'pending',
    next_execute_at  DATETIME     NOT NULL,
    retry_count      INT          DEFAULT 0,
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_delay_next (status, next_execute_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_notification (
    id               BIGINT       PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    type             VARCHAR(32)  NOT NULL,
    biz_id           VARCHAR(64),
    title            VARCHAR(128) NOT NULL,
    content          VARCHAR(512),
    is_read          INT          DEFAULT 0,
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_notice_user (user_id, is_read),
    KEY idx_notice_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===== PC 系统管理（RBAC + 数据字典，F-05） =====

-- 机构/部门树
CREATE TABLE IF NOT EXISTS t_sys_organization (
    id               BIGINT       PRIMARY KEY,
    parent_id        BIGINT       DEFAULT 0,
    name             VARCHAR(64)  NOT NULL,
    code             VARCHAR(64),
    level            INT          DEFAULT 1,
    sort             INT          DEFAULT 0,
    leader           VARCHAR(64),
    phone            VARCHAR(128),
    status           INT          DEFAULT 1,
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_org_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 角色
CREATE TABLE IF NOT EXISTS t_sys_role (
    id               BIGINT       PRIMARY KEY,
    name             VARCHAR(64)  NOT NULL,
    code             VARCHAR(64)  NOT NULL,
    status           INT          DEFAULT 1,
    sort             INT          DEFAULT 0,
    remark           VARCHAR(255),
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 菜单/权限（type: 0 目录 / 1 菜单 / 2 按钮；perms 为权限标识，如 system:user:list）
CREATE TABLE IF NOT EXISTS t_sys_menu (
    id               BIGINT       PRIMARY KEY,
    parent_id        BIGINT       DEFAULT 0,
    name             VARCHAR(64)  NOT NULL,
    type             INT          DEFAULT 1,
    path             VARCHAR(128),
    component        VARCHAR(128),
    icon             VARCHAR(64),
    perms            VARCHAR(128),
    sort             INT          DEFAULT 0,
    status           INT          DEFAULT 1,
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_menu_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 数据字典类型
CREATE TABLE IF NOT EXISTS t_sys_dict_type (
    id               BIGINT       PRIMARY KEY,
    dict_type        VARCHAR(64)  NOT NULL,
    dict_name        VARCHAR(64)  NOT NULL,
    status           INT          DEFAULT 1,
    remark           VARCHAR(255),
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_dict_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 数据字典明细
CREATE TABLE IF NOT EXISTS t_sys_dict_data (
    id               BIGINT       PRIMARY KEY,
    dict_type        VARCHAR(64)  NOT NULL,
    dict_label       VARCHAR(64)  NOT NULL,
    dict_value       VARCHAR(64)  NOT NULL,
    dict_sort        INT          DEFAULT 0,
    status           INT          DEFAULT 1,
    remark           VARCHAR(255),
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_dict_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 管理员 <-> 角色
CREATE TABLE IF NOT EXISTS t_admin_user_role (
    id               BIGINT       PRIMARY KEY,
    admin_user_id    BIGINT       NOT NULL,
    role_id          BIGINT       NOT NULL,
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_aur (admin_user_id, role_id),
    KEY idx_aur_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 角色 <-> 菜单（权限）
CREATE TABLE IF NOT EXISTS t_role_menu (
    id               BIGINT       PRIMARY KEY,
    role_id          BIGINT       NOT NULL,
    menu_id          BIGINT       NOT NULL,
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_rm (role_id, menu_id),
    KEY idx_rm_menu (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===== 评价与信用（F-06） =====

CREATE TABLE IF NOT EXISTS t_review (
    id               BIGINT       PRIMARY KEY,
    order_no         VARCHAR(32),
    item_id          BIGINT,
    reviewer_id      BIGINT,
    target_id        BIGINT,
    role             VARCHAR(16),   -- BUYER_SELLER / SELLER_BUYER
    rating           INT,
    content          VARCHAR(512),
    status           INT           DEFAULT 1,  -- 0 待审 / 1 通过 / 2 驳回
    anonymous        INT           DEFAULT 0,
    reject_reason    VARCHAR(255),
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_review_item (item_id, status),
    KEY idx_review_target (target_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_credit_log (
    id               BIGINT       PRIMARY KEY,
    user_id          BIGINT,
    delta            INT,
    reason           VARCHAR(32),
    snapshot         INT,
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_credit_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===== 优惠券 / 营销（F-10）=====

-- 优惠券模板（平台发放）：金额单位 分
CREATE TABLE IF NOT EXISTS t_coupon (
    id                BIGINT       PRIMARY KEY,
    name              VARCHAR(64),
    type              VARCHAR(32),                   -- FULL_REDUCTION / NO_THRESHOLD / DISCOUNT
    threshold_amount  BIGINT       DEFAULT 0,         -- 满减门槛（分）
    reduce_amount     BIGINT       DEFAULT 0,         -- 减免金额（分）
    discount_rate     DOUBLE       DEFAULT 1,         -- 折扣率（DISCOUNT，0.9=9折）
    max_discount_amount BIGINT     DEFAULT 0,         -- 折扣封顶减免（分）
    scope             VARCHAR(16)  DEFAULT 'ALL',     -- ALL / CATEGORY / ITEM
    scope_id          BIGINT,                         -- 适用类目/商品 ID
    total_count       INT          DEFAULT 0,         -- 发放总量
    claimed_count     INT          DEFAULT 0,         -- 已领取数
    per_user_limit    INT          DEFAULT 1,         -- 每人限领
    status            VARCHAR(16)  DEFAULT 'ACTIVE',  -- ACTIVE / PAUSED / ENDED
    start_at          DATETIME,
    end_at            DATETIME,
    created_at        DATETIME,
    updated_at        DATETIME,
    KEY idx_coupon_status (status, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 用户优惠券（领取后落库）
CREATE TABLE IF NOT EXISTS t_user_coupon (
    id               BIGINT       PRIMARY KEY,
    coupon_id         BIGINT,
    user_id          BIGINT,
    order_no         VARCHAR(32),
    status           VARCHAR(16)  DEFAULT 'UNUSED',  -- UNUSED / USED / EXPIRED
    expire_at        DATETIME,
    claimed_at       DATETIME,
    used_at          DATETIME,
    discount_amount  BIGINT       DEFAULT 0,           -- F-13.5：核销时落库的优惠金额（分），用于营销 ROI 计算
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_uc_user (user_id, status),
    KEY idx_uc_order (order_no),
    -- 券并发限领兜底：同一用户对同一券唯一，杜绝并发竞态下重复领取（与 per_user_limit 默认 1 一致）
    UNIQUE KEY uk_user_coupon (user_id, coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===== 积分体系（F-13.1）=====

-- 用户积分账户：每用户一行，惰性创建（首次产生积分时落库）
CREATE TABLE IF NOT EXISTS t_point (
    id               BIGINT       PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    balance          BIGINT       DEFAULT 0,   -- 可用积分
    total_earned     BIGINT       DEFAULT 0,   -- 累计获得积分（仅正向增加）
    version          INT          DEFAULT 0,
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_point_user (user_id),
    KEY idx_point_balance (user_id, balance)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 积分流水：每笔积分变动（获得/抵扣/释放）落一条
CREATE TABLE IF NOT EXISTS t_point_log (
    id               BIGINT       PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    biz_type         VARCHAR(32)  NOT NULL,   -- EARN_SIGNIN/EARN_TRADE/EARN_REVIEW/REDEEM/REDEEM_RELEASED
    biz_id           VARCHAR(64),             -- 订单号 / 评价 ID / 日期
    delta            BIGINT,                  -- 积分变动（正=获得，负=抵扣）
    balance_after    BIGINT,                  -- 变动后余额
    remark           VARCHAR(255),
    created_at       DATETIME,
    updated_at       DATETIME,
    KEY idx_point_log_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===== 促销活动：拼团 / 限时秒杀（F-13.3）=====

-- 促销活动定义：围绕单个商品的限时营销，自带活动库存（独立于商品库存的营销配额）
CREATE TABLE IF NOT EXISTS t_activity (
    id                  BIGINT       PRIMARY KEY,
    item_id             BIGINT       NOT NULL,
    type                VARCHAR(20)  NOT NULL COMMENT 'SECKILL/GROUP',
    activity_price      BIGINT       NOT NULL COMMENT '活动价（分）',
    stock               INT          NOT NULL DEFAULT 0 COMMENT '活动库存',
    sold_count          INT          NOT NULL DEFAULT 0 COMMENT '已锁定/已售数量',
    limit_per_user      INT          NOT NULL DEFAULT 1 COMMENT '每人限购（<=0 不限）',
    group_size          INT          DEFAULT NULL COMMENT '拼团成团人数（GROUP）',
    group_valid_minutes INT          DEFAULT NULL COMMENT '拼团有效分钟（GROUP）',
    status              VARCHAR(20)  NOT NULL DEFAULT 'ONGOING' COMMENT 'PENDING/ONGOING/ENDED',
    start_at            DATETIME     COMMENT '开始时间',
    end_at              DATETIME     COMMENT '结束时间',
    created_at          DATETIME,
    updated_at          DATETIME,
    KEY idx_activity_item (item_id),
    KEY idx_activity_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 拼团实例（GROUP 类型）：一次成团记录，人数达标转 SUCCESS，超时未达标转 FAILED
CREATE TABLE IF NOT EXISTS t_activity_group (
    id                  BIGINT       PRIMARY KEY,
    activity_id         BIGINT       NOT NULL,
    leader_id           BIGINT       NOT NULL COMMENT '团长',
    current_size        INT          NOT NULL DEFAULT 1 COMMENT '当前参团人数',
    target_size         INT          NOT NULL DEFAULT 0 COMMENT '成团目标人数',
    status              VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/SUCCESS/FAILED',
    expire_at           DATETIME     COMMENT '成团截止时间',
    created_at          DATETIME,
    updated_at          DATETIME,
    KEY idx_group_activity (activity_id),
    KEY idx_group_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 活动参与记录：每个用户每次参与落一条；唯一索引兜底并发重复参与
CREATE TABLE IF NOT EXISTS t_activity_participant (
    id                  BIGINT       PRIMARY KEY,
    activity_id         BIGINT       NOT NULL,
    user_id             BIGINT       NOT NULL,
    group_id            BIGINT       DEFAULT NULL COMMENT '所属拼团（GROUP）；秒杀为 NULL',
    order_no            VARCHAR(64)  DEFAULT NULL COMMENT '关联订单号（下单锁定库存后回写）',
    status              VARCHAR(20)  NOT NULL DEFAULT 'JOINED' COMMENT 'JOINED/PAID_PENDING/CANCELLED/COMPLETED',
    qty                 INT          NOT NULL DEFAULT 1,
    created_at          DATETIME,
    updated_at          DATETIME,
    UNIQUE KEY uk_act_user (activity_id, user_id),
    KEY idx_part_activity (activity_id),
    KEY idx_part_group (group_id),
    KEY idx_part_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===================== F-13.4 邀请拉新 / 分销 =====================
-- 邀请码：每位用户一个唯一邀请码，供被邀请人绑定
CREATE TABLE IF NOT EXISTS t_invite_code (
    id                  BIGINT       PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    code                VARCHAR(32)  NOT NULL COMMENT '邀请码（唯一，大小写不敏感）',
    created_at          DATETIME,
    updated_at          DATETIME,
    UNIQUE KEY uk_code (code),
    UNIQUE KEY uk_code_user (user_id),
    KEY idx_code_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 邀请关系：邀请人 → 被邀请人，含首单返券奖励状态
CREATE TABLE IF NOT EXISTS t_invite_relation (
    id                  BIGINT       PRIMARY KEY,
    inviter_id          BIGINT       NOT NULL COMMENT '邀请人',
    invitee_id          BIGINT       NOT NULL COMMENT '被邀请人',
    reward_coupon_id    BIGINT       DEFAULT NULL COMMENT '首单奖励券模板 ID',
    rewarded            TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未奖励 / 1 已奖励',
    created_at          DATETIME,
    updated_at          DATETIME,
    UNIQUE KEY uk_invite_pair (inviter_id, invitee_id),
    KEY idx_relation_invitee (invitee_id),
    KEY idx_relation_inviter (inviter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===================== F-14.3 搜索词运营 =====================
-- 热搜词：记录用户搜索热度，运营可置为屏蔽
CREATE TABLE IF NOT EXISTS t_search_hot_word (
    id                  BIGINT       PRIMARY KEY,
    word                VARCHAR(64)  NOT NULL,
    heat                INT          NOT NULL DEFAULT 0,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/BLOCKED',
    created_at          DATETIME,
    updated_at          DATETIME,
    UNIQUE KEY uk_hot_word (word),
    KEY idx_hot_heat (heat)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 用户搜索历史
CREATE TABLE IF NOT EXISTS t_search_history (
    id                  BIGINT       PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    word                VARCHAR(64)  NOT NULL,
    created_at          DATETIME,
    KEY idx_history_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 搜索屏蔽词
CREATE TABLE IF NOT EXISTS t_search_block_word (
    id                  BIGINT       PRIMARY KEY,
    word                VARCHAR(64)  NOT NULL COMMENT '归一化后的值（小写、折叠空白）',
    created_at          DATETIME,
    UNIQUE KEY uk_block_word (word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 搜索同义词词典
CREATE TABLE IF NOT EXISTS t_search_synonym (
    id                  BIGINT       PRIMARY KEY,
    word                VARCHAR(64)  NOT NULL,
    synonym             VARCHAR(64)  NOT NULL,
    created_at          DATETIME,
    KEY idx_syn_word (word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===================== F-13.2 会员等级与权益 =====================
-- 会员等级配置：成长值阈值 + 等级权益（免运费 / 优先审核 / 平台佣金折扣）。
-- 由运营后台维护（AdminMemberLevelController），应用启动时若为空则种子默认 5 级。
CREATE TABLE IF NOT EXISTS t_member_level (
    id                    BIGINT       PRIMARY KEY,
    level_code            VARCHAR(20)  NOT NULL COMMENT '等级编码（唯一，如 L1..L5）',
    level_name            VARCHAR(32)  NOT NULL COMMENT '等级名称',
    min_growth            INT          NOT NULL DEFAULT 0 COMMENT '达到该等级所需成长值（阈值）',
    free_shipping         TINYINT      NOT NULL DEFAULT 0 COMMENT '0/1 是否免运费',
    priority_review       TINYINT      NOT NULL DEFAULT 0 COMMENT '0/1 是否优先审核（发布/评价）',
    commission_discount   INT          NOT NULL DEFAULT 0 COMMENT '平台佣金减免（千分比，0=不减免，100=减10%）',
    icon                  VARCHAR(64)  DEFAULT NULL COMMENT '前端展示图标（emoji 或图标名）',
    color                 VARCHAR(16)  DEFAULT NULL COMMENT '主题色',
    sort_order            INT          NOT NULL DEFAULT 0 COMMENT '展示排序',
    created_at            DATETIME,
    updated_at            DATETIME,
    UNIQUE KEY uk_level_code (level_code),
    KEY idx_level_sort (sort_order, min_growth)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 用户成长值账户：每个用户一行（惰性创建），记录累计成长值与当前等级
CREATE TABLE IF NOT EXISTS t_user_growth (
    id                    BIGINT       PRIMARY KEY,
    user_id               BIGINT       NOT NULL,
    growth_value          INT          NOT NULL DEFAULT 0 COMMENT '累计成长值',
    level_code            VARCHAR(20)  NOT NULL DEFAULT 'L1' COMMENT '当前等级编码',
    created_at            DATETIME,
    updated_at            DATETIME,
    UNIQUE KEY uk_growth_user (user_id),
    KEY idx_growth_level (level_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===================== F-17 售后维权工单 =====================
-- 维权工单：买家发起争议 → 卖家举证 → 平台介入 → 裁决。
-- 裁决仅落库「裁决结果与裁决退款金额」，实际资金动作交由既有退款链路（F-07/F-08）执行，避免重复出款资损。
CREATE TABLE IF NOT EXISTS t_dispute (
    id                    BIGINT       PRIMARY KEY,
    dispute_no            VARCHAR(32)  NOT NULL COMMENT '工单号（唯一，DP 前缀）',
    order_no              VARCHAR(64)  NOT NULL COMMENT '关联订单号',
    buyer_id              BIGINT       NOT NULL COMMENT '申请人（买家）',
    seller_id             BIGINT       NOT NULL COMMENT '被申诉方（卖家）',
    type                  VARCHAR(20)  NOT NULL COMMENT '争议类型：REFUND_REJECTED/NOT_RECEIVED/DAMAGED/NOT_AS_DESC',
    expectation           VARCHAR(20)  NOT NULL COMMENT '诉求：REFUND 仅退款 / RETURN_REFUND 退货退款',
    reason                VARCHAR(255) DEFAULT '' COMMENT '原因描述',
    amount                BIGINT       NOT NULL DEFAULT 0 COMMENT '争议金额（分）',
    status                VARCHAR(20)  NOT NULL COMMENT 'PENDING/SELLER_REPLIED/PLATFORM/RESOLVED/CLOSED/CANCELED',
    buyer_evidence        TEXT         COMMENT '买家举证（图片 URL，逗号分隔）',
    seller_evidence       TEXT         COMMENT '卖家举证（图片 URL，逗号分隔）',
    result                VARCHAR(20)  DEFAULT NULL COMMENT '裁决结果：BUYER_WIN/SELLER_WIN/PARTIAL',
    platform_remark       VARCHAR(255) DEFAULT NULL COMMENT '平台裁决说明',
    refund_amount         BIGINT       NOT NULL DEFAULT 0 COMMENT '裁决退款金额（分），0=不支持退款',
    handled_at            DATETIME     DEFAULT NULL COMMENT '处理/裁决时间',
    created_at            DATETIME,
    updated_at            DATETIME,
    UNIQUE KEY uk_dispute_no (dispute_no),
    KEY idx_dispute_order (order_no),
    KEY idx_dispute_buyer (buyer_id),
    KEY idx_dispute_seller (seller_id),
    KEY idx_dispute_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===================== F-02 鉴定验货履约 =====================
-- 验货单：买家送检 → 机构收件 → 验货 → 通过/不通过/异常。验货中冻结订单发货（REQ-04），
-- 服务费按「分」计入资金流水（REQ-10）。状态流转见 InspectionStateMachine。
CREATE TABLE IF NOT EXISTS t_inspection_order (
    id                    BIGINT       PRIMARY KEY,
    inspection_no         VARCHAR(32)  NOT NULL COMMENT '验货单号（唯一，IV 前缀）',
    order_no              VARCHAR(64)  NOT NULL COMMENT '关联订单号',
    item_id               BIGINT       NOT NULL COMMENT '关联商品 ID',
    seller_id             BIGINT       NOT NULL COMMENT '卖家',
    buyer_id              BIGINT       NOT NULL COMMENT '送检买家',
    type                  VARCHAR(16)  NOT NULL COMMENT '验货类型：STANDARD 标准 / ACCURATE 精密',
    status                VARCHAR(20)  NOT NULL COMMENT 'WAIT_PICKUP/IN_TRANSIT/INSPECTING/PASSED/REJECTED/EXCEPTION/CANCELED',
    fee_amount            BIGINT       NOT NULL DEFAULT 0 COMMENT '服务费（分，REQ-10）',
    agency_id             BIGINT       DEFAULT NULL COMMENT '鉴定机构 ID',
    report_id             BIGINT       DEFAULT NULL COMMENT '关联报告 ID',
    related_dispute_id    BIGINT       DEFAULT NULL COMMENT '关联维权工单 ID（REQ-06）',
    timeout_at            DATETIME     DEFAULT NULL COMMENT '超时时刻（REQ-09）',
    received_at           DATETIME     DEFAULT NULL COMMENT '机构收件时间',
    started_at            DATETIME     DEFAULT NULL COMMENT '验货开始时间',
    finished_at           DATETIME     DEFAULT NULL COMMENT '验货完成时间',
    remark                VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at            DATETIME,
    updated_at            DATETIME,
    UNIQUE KEY uk_inspection_no (inspection_no),
    KEY idx_inspection_order (order_no),
    KEY idx_inspection_item (item_id),
    KEY idx_inspection_buyer (buyer_id),
    KEY idx_inspection_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 鉴定报告快照（F-02）：机构回传后落库，不可篡改（REQ-03）。
CREATE TABLE IF NOT EXISTS t_inspection_report (
    id                    BIGINT       PRIMARY KEY,
    report_no             VARCHAR(32)  NOT NULL COMMENT '报告编号（唯一）',
    inspection_no         VARCHAR(32)  NOT NULL COMMENT '关联验货单号',
    order_no              VARCHAR(64)  NOT NULL COMMENT '关联订单号',
    item_id               BIGINT       NOT NULL COMMENT '关联商品 ID',
    agency_id             BIGINT       DEFAULT NULL COMMENT '鉴定机构 ID',
    grade                 VARCHAR(8)   DEFAULT NULL COMMENT '评级：A/B/C/D',
    pass                  TINYINT(1)   DEFAULT NULL COMMENT '是否通过（REQ-03）：1 通过 / 0 不通过',
    func_items            TEXT         COMMENT '功能项检测结果（JSON）',
    flaws                 TEXT         COMMENT '瑕疵项（JSON）',
    cover_images          TEXT         COMMENT '报告封面/图（逗号分隔 URL）',
    raw_json              TEXT         COMMENT '机构原始回传报文（不可篡改存证）',
    report_version        VARCHAR(16)  DEFAULT '1.0' COMMENT '报告版本',
    created_at            DATETIME,
    updated_at            DATETIME,
    UNIQUE KEY uk_report_no (report_no),
    KEY idx_report_inspection (inspection_no),
    KEY idx_report_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 幂等记录（F-18 幂等框架）：三态 PROCESSING/SUCCESS/FAILED
-- uk_idem_key 为终局防重屏障（并发下仅一个请求可插入成功），与 uk_user_coupon 限领同一范式
CREATE TABLE IF NOT EXISTS t_idempotent_record (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    idem_key       VARCHAR(128) NOT NULL COMMENT '幂等键 {bizType}:{bizKey}，超长取 SHA-256 摘要',
    biz_type       VARCHAR(32)  NOT NULL COMMENT '业务类型：order.create/refund.apply/withdraw.apply',
    status         VARCHAR(16)  NOT NULL COMMENT 'PROCESSING/SUCCESS/FAILED',
    result         TEXT         COMMENT '首次成功结果快照（JSON），用于重复请求回放',
    attempt        INT          NOT NULL DEFAULT 1 COMMENT '执行次数（超时接管递增）',
    expire_at      DATETIME     NOT NULL COMMENT 'PROCESSING 过期时刻，超时可被接管',
    create_time    DATETIME,
    update_time    DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idem_key (idem_key),
    KEY idx_idem_expire (expire_at),
    KEY idx_idem_create (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 资金对账账单（ITER-T02 / F-11.4 深化）：日终多维对账结果持久化（REQ-06/07/08）。
-- (reconcile_date, dimension) 唯一；金额恒以「分」整型，避免浮点资损。
-- 维度 PAY/REFUND/SETTLE；FREEZE 因提现无独立冻结终态而排除，避免误报。
CREATE TABLE IF NOT EXISTS t_reconcile_bill (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    reconcile_date    DATE         NOT NULL COMMENT '对账自然日',
    dimension         VARCHAR(16)  NOT NULL COMMENT '对账维度：PAY/REFUND/SETTLE',
    expected_count    INT          NOT NULL DEFAULT 0 COMMENT '平台侧预期笔数',
    actual_count      INT          NOT NULL DEFAULT 0 COMMENT '资金流水侧实际笔数',
    expected_amount   BIGINT       NOT NULL DEFAULT 0 COMMENT '预期金额合计（分）',
    actual_amount     BIGINT       NOT NULL DEFAULT 0 COMMENT '实际金额合计（分）',
    diff_amount       BIGINT       NOT NULL DEFAULT 0 COMMENT '金额差异（分，实际-预期）',
    matched           TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '笔数+金额是否一致：1 是 / 0 否',
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RESOLVED/CLOSED',
    alert_sent        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已发送差异告警',
    note              VARCHAR(255) DEFAULT NULL COMMENT '备注/关闭说明',
    created_at        DATETIME,
    updated_at        DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_date_dimension (reconcile_date, dimension),
    KEY idx_bill_date (reconcile_date),
    KEY idx_bill_dimension (dimension),
    KEY idx_bill_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 资金对账差异明细（ITER-T02 / REQ-07）：账单级不平的逐笔下钻工单，供人工核验。
CREATE TABLE IF NOT EXISTS t_reconcile_diff (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    bill_id           BIGINT       NOT NULL COMMENT '关联 t_reconcile_bill.id',
    dimension         VARCHAR(16)  NOT NULL COMMENT '对账维度：PAY/REFUND/SETTLE',
    biz_no            VARCHAR(64)  NOT NULL COMMENT '业务单号 orderNo/refundNo/settleNo',
    expected_amount   BIGINT       NOT NULL DEFAULT 0 COMMENT '平台侧金额（分）',
    actual_amount     BIGINT       NOT NULL DEFAULT 0 COMMENT '资金流水侧金额（分）',
    diff_amount       BIGINT       NOT NULL DEFAULT 0 COMMENT '金额差异（分，实际-预期）',
    expected_count    INT          NOT NULL DEFAULT 0 COMMENT '平台侧笔数',
    actual_count      INT          NOT NULL DEFAULT 0 COMMENT '资金流水侧笔数',
    status            VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RESOLVED/CLOSED',
    handle_note       VARCHAR(255) DEFAULT NULL COMMENT '处理说明',
    handler_id        BIGINT       DEFAULT NULL COMMENT '处理人（管理员 ID）',
    created_at        DATETIME,
    updated_at        DATETIME,
    PRIMARY KEY (id),
    KEY idx_diff_bill (bill_id),
    KEY idx_diff_dimension (dimension),
    KEY idx_diff_biz (biz_no),
    KEY idx_diff_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===================== 估价引擎（ITER-F03）=====================
-- 历史成交样本（REQ-05）：支付成功回流，按 (品类, 成色) 维度分位估价与校准。
-- 金额恒「分」整型；dealAt 作为日历聚合键。
CREATE TABLE IF NOT EXISTS t_price_sample (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    item_id         BIGINT       DEFAULT NULL COMMENT '关联商品 ID',
    category_id     BIGINT       NOT NULL COMMENT '类目 ID',
    condition_level INT          DEFAULT NULL COMMENT '成色等级 1~5（1 全新 ~ 5 功能完好）',
    deal_price_fen  BIGINT       NOT NULL DEFAULT 0 COMMENT '成交价（分，REQ-01）',
    deal_at         DATETIME     DEFAULT NULL COMMENT '成交时间',
    source          VARCHAR(16)  DEFAULT 'deal' COMMENT '样本来源：deal(真实成交)/manual(人工录入)',
    created_at      DATETIME,
    updated_at      DATETIME,
    PRIMARY KEY (id),
    KEY idx_sample_category (category_id),
    KEY idx_sample_cat_cond (category_id, condition_level),
    KEY idx_sample_deal_at (deal_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 品类估价配置（REQ-08）：运营可配置品类基准价与全局系数覆盖；category_id 唯一。
CREATE TABLE IF NOT EXISTS t_price_estimate_config (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    category_id     BIGINT       NOT NULL COMMENT '类目 ID（唯一）',
    base_price_fen  BIGINT       DEFAULT NULL COMMENT '品类基准价（全新参考，分，REQ-01）',
    multiplier      DECIMAL(10,4) DEFAULT '1.0000' COMMENT '运营系数覆盖（默认 1.0）',
    operator_id     BIGINT       DEFAULT NULL COMMENT '最近操作人（管理员 ID）',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    created_at      DATETIME,
    updated_at      DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_category (category_id),
    KEY idx_config_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 估价审计/留痕（REQ-08 系数覆盖 / REQ-10 异常低价送审 / RECALC 自动重算）。
CREATE TABLE IF NOT EXISTS t_price_estimate_audit (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    category_id     BIGINT       DEFAULT NULL COMMENT '关联类目 ID（ABNORMAL_LOW 时为商品所属类目）',
    biz_type        VARCHAR(16)  NOT NULL COMMENT '业务类型：CONFIG_OVERRIDE/ABNORMAL_LOW/RECALC',
    biz_id          BIGINT       DEFAULT NULL COMMENT '业务主键：ABNORMAL_LOW→商品 ID；其余→配置 ID',
    before_json     TEXT         COMMENT '变更前快照（JSON）',
    after_json      TEXT         COMMENT '变更后快照（JSON）',
    operator_id     BIGINT       DEFAULT NULL COMMENT '操作人（管理员 ID；系统行为为 0）',
    reason          VARCHAR(255) DEFAULT NULL COMMENT '操作说明/原因',
    created_at      DATETIME,
    updated_at      DATETIME,
    PRIMARY KEY (id),
    KEY idx_audit_category (category_id),
    KEY idx_audit_biz_type (biz_type),
    KEY idx_audit_biz_id (biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 真机验收用例（ITER-F01 / F-11.5）：PRD「第十节 验收」REQ-01~REQ-09 的逐条追踪项。
-- status: NOT_RUN(未执行) / PASSED(通过) / FAILED(未通过) / SKIPPED(沙箱不适用已跳过)。
CREATE TABLE IF NOT EXISTS t_acceptance_case (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    req_code     VARCHAR(16)  NOT NULL COMMENT '关联需求编号 REQ-01~REQ-09',
    title        VARCHAR(128) NOT NULL COMMENT '用例标题',
    category     VARCHAR(16)  NOT NULL DEFAULT 'PAY' COMMENT '分类 PAY/IM/SIGN/CONFIG/DEFECT',
    status       VARCHAR(16)  NOT NULL DEFAULT 'NOT_RUN' COMMENT 'NOT_RUN/PASSED/FAILED/SKIPPED',
    executor     VARCHAR(64)  DEFAULT NULL COMMENT '验收执行人',
    result_note  VARCHAR(512) DEFAULT NULL COMMENT '结果备注',
    executed_at  DATETIME     DEFAULT NULL COMMENT '验收执行时间',
    prd_link     VARCHAR(255) DEFAULT NULL COMMENT '关联 PRD 文档标识',
    created_at   DATETIME,
    updated_at   DATETIME,
    PRIMARY KEY (id),
    KEY idx_case_req (req_code),
    KEY idx_case_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 真机验收缺陷（ITER-F01 / REQ-09）：真机缺陷登记并回链 PRD。
-- status: OPEN(待修复) / FIXED(已修复待验证) / CLOSED(已闭环)；severity: P0/P1/P2。
CREATE TABLE IF NOT EXISTS t_acceptance_defect (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    case_id      BIGINT       DEFAULT NULL COMMENT '关联验收用例 ID（可空）',
    req_code     VARCHAR(16)  DEFAULT NULL COMMENT '关联需求编号 REQ-01~REQ-09',
    title        VARCHAR(128) NOT NULL COMMENT '缺陷标题',
    severity     VARCHAR(8)   NOT NULL DEFAULT 'P1' COMMENT 'P0/P1/P2',
    status       VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/FIXED/CLOSED',
    linked_prd   VARCHAR(255) DEFAULT NULL COMMENT '回链 PRD 文档标识',
    description  VARCHAR(1024) DEFAULT NULL COMMENT '缺陷描述',
    fixed_at     DATETIME     DEFAULT NULL COMMENT '修复时间',
    closed_at    DATETIME     DEFAULT NULL COMMENT '闭环时间',
    created_at   DATETIME,
    updated_at   DATETIME,
    PRIMARY KEY (id),
    KEY idx_defect_case (case_id),
    KEY idx_defect_status (status),
    KEY idx_defect_severity (severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- AB 实验（ITER-F04 搜索推荐深化 / REQ-06 稳定分桶、REQ-07 一键回滚）：实验配置与分桶依据。
-- status: DRAFT(草稿) / RUNNING(运行中) / ROLLED_BACK(已回滚)。
-- name 唯一（运营识别 + 冲突校验）；treatment_percent 为实验组流量占比 0~100。
CREATE TABLE IF NOT EXISTS t_ab_experiment (
    id                  BIGINT       PRIMARY KEY,
    name                VARCHAR(64)  NOT NULL COMMENT '实验名称（唯一，便于冲突校验与运营识别）',
    status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/RUNNING/ROLLED_BACK',
    treatment_percent   INT          NOT NULL DEFAULT 0 COMMENT '实验组流量占比 0~100',
    strategy_params     VARCHAR(4096) DEFAULT NULL COMMENT '策略参数 JSON（如 recallBoost/semanticRecall）',
    start_at            DATETIME     DEFAULT NULL COMMENT '生效起始时间（空=立即生效）',
    end_at              DATETIME     DEFAULT NULL COMMENT '生效结束时间（空=长期）',
    creator             VARCHAR(64)  DEFAULT NULL COMMENT '创建人标识',
    description         VARCHAR(255) DEFAULT NULL COMMENT '实验说明',
    created_at          DATETIME,
    updated_at          DATETIME,
    UNIQUE KEY uk_ab_name (name),
    KEY idx_ab_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 上门回收单（ITER-F05 同城回收与本地履约）：上门回收预估 / 申请 / 取消 / 质检调价 / 确认放款。
-- 金额单位：分。状态：PENDING_PICKUP / PICKED_UP / INSPECTING / QUOTED / CONFIRMED / PAID /
--           CANCELED / REJECTED / UNAVAILABLE（见 RecycleStateMachine）。
-- org_id 支撑区域机构数据隔离（REQ-08）；address / contact_phone 落库前脱敏（PRD §九）。
CREATE TABLE IF NOT EXISTS t_recycle_order (
    id               BIGINT       PRIMARY KEY,
    recycle_no       VARCHAR(32)  NOT NULL COMMENT '回收单号（RC 前缀，唯一）',
    user_id          BIGINT       NOT NULL COMMENT '申请人 userId',
    org_id           BIGINT       DEFAULT NULL COMMENT '区域机构 ID（REQ-08 数据隔离；空=未归属）',
    item_id          BIGINT       DEFAULT NULL COMMENT '关联商品 ID（一键回收 REQ-11）',
    category_id      BIGINT       NOT NULL COMMENT '类目 ID（白名单校验 + 估价维度）',
    title            VARCHAR(128) DEFAULT NULL COMMENT '回收品标题快照',
    condition_level  INT          NOT NULL DEFAULT 3 COMMENT '成色档位 1 全新 ~ 5 功能完好',
    pickup_type      VARCHAR(16)  NOT NULL DEFAULT 'HOME' COMMENT '取件方式 HOME 上门 / SELF 自送',
    estimate_amount  BIGINT       NOT NULL DEFAULT 0 COMMENT '预估回收价（分，REQ-02）',
    final_amount     BIGINT       NOT NULL DEFAULT 0 COMMENT '质检后最终价（分，REQ-04）',
    status           VARCHAR(24)  NOT NULL DEFAULT 'PENDING_PICKUP' COMMENT '状态机见 RecycleStateMachine',
    province         VARCHAR(64)  DEFAULT NULL COMMENT '省',
    city             VARCHAR(64)  DEFAULT NULL COMMENT '市（同城可用性判定 REQ-01/09）',
    district         VARCHAR(64)  DEFAULT NULL COMMENT '区',
    address          VARCHAR(255) DEFAULT NULL COMMENT '脱敏后的取件地址（隐去门牌号）',
    contact_name     VARCHAR(32)  DEFAULT NULL COMMENT '联系人姓名',
    contact_phone    VARCHAR(32)  DEFAULT NULL COMMENT '脱敏后的联系电话（138****1234）',
    appointment_at   DATETIME     DEFAULT NULL COMMENT '预约上门时间',
    quote_remark     VARCHAR(512) DEFAULT NULL COMMENT '质检调价说明（对用户明示 REQ-04）',
    serial_no        VARCHAR(64)  DEFAULT NULL COMMENT '第三方回收商外部单号',
    cancel_reason    VARCHAR(512) DEFAULT NULL COMMENT '取消/退回原因',
    operator         VARCHAR(64)  DEFAULT NULL COMMENT '运营处理人',
    paid_at          DATETIME     DEFAULT NULL COMMENT '放款时间（REQ-07）',
    picked_up_at     DATETIME     DEFAULT NULL COMMENT '揽收时间',
    quoted_at        DATETIME     DEFAULT NULL COMMENT '质检完成时间',
    finished_at      DATETIME     DEFAULT NULL COMMENT '终态时间',
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_recycle_no (recycle_no),
    KEY idx_recycle_user (user_id),
    KEY idx_recycle_status (status),
    KEY idx_recycle_org (org_id),
    KEY idx_recycle_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 同城配送单（ITER-F05 本地履约 / REQ-06、REQ-07）：第三方调度骑手，轨迹回传 t_logistics。
-- 金额单位：分。状态：CREATED / ACCEPTED / DELIVERING / ARRIVED / SIGNED / CANCELED / FAILED
--           （见 LocalDeliveryStateMachine）。
CREATE TABLE IF NOT EXISTS t_local_delivery (
    id             BIGINT       PRIMARY KEY,
    delivery_no    VARCHAR(32)  NOT NULL COMMENT '配送单号（LD 前缀，唯一）',
    biz_type       VARCHAR(16)  NOT NULL DEFAULT 'ORDER' COMMENT '业务类型 ORDER 订单 / RECYCLE 回收',
    biz_no         VARCHAR(32)  NOT NULL COMMENT '业务单号（订单号 / 回收单号）',
    user_id        BIGINT       NOT NULL COMMENT '发起方 userId',
    city           VARCHAR(64)  DEFAULT NULL COMMENT '配送城市（同城可用性判定）',
    from_address   VARCHAR(255) DEFAULT NULL COMMENT '起点（脱敏）地址',
    to_address     VARCHAR(255) DEFAULT NULL COMMENT '终点（脱敏）地址',
    distance_km    DECIMAL(10,2) DEFAULT NULL COMMENT '配送距离（公里）',
    freight        BIGINT       NOT NULL DEFAULT 0 COMMENT '配送费（分，REQ-07）',
    paid           INT          NOT NULL DEFAULT 0 COMMENT '配送费是否已入账（0 否 / 1 是）',
    status         VARCHAR(24)  NOT NULL DEFAULT 'CREATED' COMMENT '状态机见 LocalDeliveryStateMachine',
    provider_name  VARCHAR(64)  DEFAULT NULL COMMENT '第三方服务商名称',
    provider_no    VARCHAR(64)  DEFAULT NULL COMMENT '第三方运单号',
    courier_name   VARCHAR(64)  DEFAULT NULL COMMENT '骑手姓名',
    courier_phone  VARCHAR(32)  DEFAULT NULL COMMENT '脱敏后的骑手电话',
    fail_reason    VARCHAR(512) DEFAULT NULL COMMENT '失败/不可用原因（REQ-09 明确提示）',
    accepted_at    DATETIME     DEFAULT NULL COMMENT '骑手接单时间',
    delivered_at   DATETIME     DEFAULT NULL COMMENT '送达时间',
    finished_at    DATETIME     DEFAULT NULL COMMENT '终态时间（签收/取消/失败）',
    created_at     DATETIME,
    updated_at     DATETIME,
    UNIQUE KEY uk_delivery_no (delivery_no),
    KEY idx_delivery_biz (biz_type, biz_no),
    KEY idx_delivery_user (user_id),
    KEY idx_delivery_status (status),
    KEY idx_delivery_provider (provider_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===== ITER-F05 增量迁移（幂等） =====
-- 对既已初始化的存量库补 t_item.delivery_type / t_order.delivery_type 两列；
-- 新库在上方 CREATE TABLE IF NOT EXISTS 中已包含，此处判定为已存在则跳过。
-- 采用 information_schema 守卫 + PREPARE 动态 SQL（MySQL 不支持 ADD COLUMN IF NOT EXISTS）。
-- 注意：串内单引号按 SQL 规范用两个单引号转义，避免依赖 ANSI_QUOTES 之外的引号行为差异。
SET @has_item_dt := (SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 't_item' AND column_name = 'delivery_type');
SET @item_dt_sql := IF(@has_item_dt = 0,
    'ALTER TABLE t_item ADD COLUMN delivery_type VARCHAR(16) DEFAULT ''EXPRESS'' COMMENT ''EXPRESS/SELF_PICKUP/LOCAL_DELIVERY''',
    'SELECT 1');
PREPARE stmt FROM @item_dt_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_order_dt := (SELECT COUNT(*) FROM information_schema.columns
                      WHERE table_schema = DATABASE() AND table_name = 't_order' AND column_name = 'delivery_type');
SET @order_dt_sql := IF(@has_order_dt = 0,
    'ALTER TABLE t_order ADD COLUMN delivery_type VARCHAR(16) DEFAULT ''EXPRESS'' COMMENT ''EXPRESS/SELF_PICKUP/LOCAL_DELIVERY''',
    'SELECT 1');
PREPARE stmt FROM @order_dt_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
