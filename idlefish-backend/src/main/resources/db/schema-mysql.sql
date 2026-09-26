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
