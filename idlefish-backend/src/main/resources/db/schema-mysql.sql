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
    auto_reply_enabled INT       DEFAULT 1 COMMENT 'ITER-F06 智能助手会话级开关：0=卖家关闭代答，1=开启',
    human_takeover   INT          DEFAULT 0 COMMENT 'ITER-F06 人工接管标记：置 1 后停止一切自动作答（REQ-09）',
    dispute_risk_score INT        DEFAULT 0 COMMENT 'ITER-F06 会话累计纠纷风险分（窗口内，封顶 100）',
    dispute_risk_at  DATETIME     COMMENT 'ITER-F06 风险分最近更新时间（窗口重置基准）',
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

-- ===================== F-12 客服工单（脱离纯 IM 的独立 workflow） =====================
-- 客服工单：用户提交问题 → 客服分配 → 处理/待用户补充 → 解决 → 关闭（含满意度回访）。
-- 与维权纠纷（t_dispute）解耦：本表是「通用客服流转」载体，不承载资金裁决。状态流转见 WorkOrderStateMachine。
CREATE TABLE IF NOT EXISTS t_work_order (
    id                    BIGINT       PRIMARY KEY,
    ticket_no             VARCHAR(32)  NOT NULL COMMENT '工单号（唯一，WO 前缀）',
    user_id               BIGINT       NOT NULL COMMENT '发起用户 ID',
    type                  VARCHAR(20)  NOT NULL COMMENT '工单类型：GENERAL/REFUND/TECHNICAL/COMPLAINT/SUGGESTION',
    priority              VARCHAR(20)  NOT NULL COMMENT '优先级：LOW/NORMAL/HIGH/URGENT',
    status                VARCHAR(20)  NOT NULL COMMENT 'OPEN/IN_PROGRESS/WAITING_USER/RESOLVED/CLOSED',
    channel               VARCHAR(20)  NOT NULL COMMENT '来源渠道：APP/WEB/MINI/PHONE',
    subject               VARCHAR(128) NOT NULL COMMENT '工单标题',
    description           VARCHAR(1024) NOT NULL COMMENT '问题描述',
    order_no              VARCHAR(64)  DEFAULT NULL COMMENT '关联订单号（可选）',
    assignee_id           BIGINT       DEFAULT NULL COMMENT '受理客服（admin 用户 ID，分配前为空）',
    resolution            VARCHAR(1024) DEFAULT NULL COMMENT '解决方案/处理说明',
    satisfaction          TINYINT      DEFAULT NULL COMMENT '满意度评分 1-5（用户关闭时回填）',
    first_response_at     DATETIME     DEFAULT NULL COMMENT '首次响应时间（SLA 度量）',
    resolved_at           DATETIME     DEFAULT NULL COMMENT '解决时间',
    closed_at             DATETIME     DEFAULT NULL COMMENT '关闭时间',
    created_at            DATETIME,
    updated_at            DATETIME,
    UNIQUE KEY uk_ticket_no (ticket_no),
    KEY idx_wo_user (user_id),
    KEY idx_wo_status (status),
    KEY idx_wo_type (type),
    KEY idx_wo_priority (priority),
    KEY idx_wo_order (order_no)
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

-- ===== ITER-T05 多租户/机构数据隔离深化（幂等） =====

-- 临时授权表（REQ-05）：超级管理员向某管理员临时开放额外机构可见范围
CREATE TABLE IF NOT EXISTS t_data_scope_temp_grant (
    id           BIGINT       PRIMARY KEY,
    grant_no     VARCHAR(32)  NOT NULL COMMENT '授权单号（GR 前缀）',
    admin_id     BIGINT       NOT NULL COMMENT '被授权管理员 ID',
    org_ids      VARCHAR(512) COMMENT '临时开放可见的机构 ID 集合，逗号分隔',
    expired_at   DATETIME     COMMENT '有效期截止；超过即失效',
    status       INT          DEFAULT 1 COMMENT '1 启用 / 0 已撤销',
    created_at   DATETIME,
    updated_at   DATETIME,
    UNIQUE KEY uk_grant_no (grant_no),
    KEY idx_grant_admin (admin_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 角色数据范围：data_scope（SELF/SELF_AND_CHILD/APPOINT/ALL）+ data_org_ids（APPOINT 指定机构集合）
SET @has_role_ds := (SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 't_sys_role' AND column_name = 'data_scope');
SET @role_ds_sql := IF(@has_role_ds = 0,
    'ALTER TABLE t_sys_role ADD COLUMN data_scope VARCHAR(32) DEFAULT ''SELF'' COMMENT ''SELF/SELF_AND_CHILD/APPOINT/ALL''',
    'SELECT 1');
PREPARE stmt FROM @role_ds_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_role_doi := (SELECT COUNT(*) FROM information_schema.columns
                      WHERE table_schema = DATABASE() AND table_name = 't_sys_role' AND column_name = 'data_org_ids');
SET @role_doi_sql := IF(@has_role_doi = 0,
    'ALTER TABLE t_sys_role ADD COLUMN data_org_ids VARCHAR(512) COMMENT ''APPOINT 指定机构集合，逗号分隔''',
    'SELECT 1');
PREPARE stmt FROM @role_doi_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 商品数据归属机构（REQ-04）：发布时写入所属机构，供数据范围隔离使用
SET @has_item_org := (SELECT COUNT(*) FROM information_schema.columns
                      WHERE table_schema = DATABASE() AND table_name = 't_item' AND column_name = 'org_id');
SET @item_org_sql := IF(@has_item_org = 0,
    'ALTER TABLE t_item ADD COLUMN org_id BIGINT COMMENT ''数据归属机构（ITER-T05）''',
    'SELECT 1');
PREPARE stmt FROM @item_org_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ===== ITER-T06 运营数据导出中心（幂等） =====

-- 导出任务表（REQ-07）：每次导出落一条记录，便于审计追溯与后续下载
CREATE TABLE IF NOT EXISTS t_export_task (
    id              BIGINT       PRIMARY KEY,
    export_no       VARCHAR(32)  NOT NULL COMMENT '导出单号（EXP 前缀）',
    type            VARCHAR(32)  NOT NULL COMMENT '导出类型 code（admin_user/item/user/order）',
    admin_id        BIGINT       NOT NULL COMMENT '发起导出的管理员 ID',
    org_ids_snapshot VARCHAR(512) COMMENT '生效机构集合快照（JSON 数组）；空表示不受限全量',
    row_count       INT          COMMENT '实际导出行数',
    format          VARCHAR(16)  DEFAULT 'CSV' COMMENT '导出格式',
    file_key        VARCHAR(256) COMMENT '存储键（文件名）',
    file_name       VARCHAR(256) COMMENT '下载文件名',
    status          VARCHAR(16)  DEFAULT 'DONE' COMMENT 'DONE 已完成 / FAILED 失败',
    created_at      DATETIME,
    updated_at      DATETIME,
    UNIQUE KEY uk_export_no (export_no),
    KEY idx_export_admin (admin_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- ===== ITER-T06 事件驱动架构（幂等，PRD-P2-T06） =====

-- 事件发件箱（REQ-05 不丢事件）：与业务同事务写入，提交后本地分发 + 定时补偿（REQ-06）+ 死信重投（REQ-08）
CREATE TABLE IF NOT EXISTS t_event_outbox (
    id            BIGINT       PRIMARY KEY,
    event_id      VARCHAR(64)  NOT NULL COMMENT '全局唯一事件 ID（32 位 UUID hex）',
    event_type    VARCHAR(64)  NOT NULL COMMENT '事件类型 code（见 EventType，如 ORDER_PAID）',
    version       INT          DEFAULT 1 COMMENT 'schema 版本号（REQ-02 版本化）',
    aggregate_id  VARCHAR(64)  COMMENT '聚合根标识（订单号/支付单号/商品 ID 等）',
    trace_id      VARCHAR(64)  COMMENT '发布链路 traceId（REQ-10 跨异步透传）',
    payload       TEXT         COMMENT '业务负载 JSON（金额字段单位「分」）',
    status        VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED/DEAD',
    retry_count   INT          DEFAULT 0 COMMENT '失败重试次数（耗尽转 DEAD）',
    next_retry_at DATETIME     COMMENT '下次重试时间（指数退避）',
    last_error    VARCHAR(500) COMMENT '最近一次分发失败原因',
    created_at    DATETIME,
    updated_at    DATETIME,
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_outbox_scan (status, next_retry_at),
    KEY idx_outbox_type (event_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 领域事件存储（REQ-09：持久化 ≥90 天可回放；治理端点查询与回放的数据源）
CREATE TABLE IF NOT EXISTS t_domain_event (
    id           BIGINT       PRIMARY KEY,
    event_id     VARCHAR(64)  NOT NULL COMMENT '全局唯一事件 ID',
    event_type   VARCHAR(64)  NOT NULL COMMENT '事件类型 code',
    version      INT          DEFAULT 1 COMMENT 'schema 版本号',
    aggregate_id VARCHAR(64)  COMMENT '聚合根标识',
    trace_id     VARCHAR(64)  COMMENT '发布链路 traceId',
    payload      TEXT         COMMENT '业务负载 JSON',
    occurred_at  DATETIME     COMMENT '业务发生时间',
    created_at   DATETIME,
    updated_at   DATETIME,
    UNIQUE KEY uk_record_event_id (event_id),
    KEY idx_record_type_time (event_type, occurred_at),
    KEY idx_record_aggregate (aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 事件回放任务（REQ-13：治理端点发起的限流回放；副作用监听器跳过）
CREATE TABLE IF NOT EXISTS t_event_replay (
    id               BIGINT       PRIMARY KEY,
    replay_no        VARCHAR(32)  NOT NULL COMMENT '回放单号（EVR 前缀）',
    event_type       VARCHAR(64)  NOT NULL COMMENT '回放的事件类型 code',
    from_time        DATETIME     COMMENT '回放时间窗起（含）',
    to_time          DATETIME     COMMENT '回放时间窗止（含）',
    operator_admin_id BIGINT      NOT NULL COMMENT '发起回放的管理员 ID',
    status           VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED',
    replayed_count   INT          DEFAULT 0 COMMENT '已重放事件数',
    skipped_count    INT          DEFAULT 0 COMMENT '因副作用跳过的事件数（REQ-13）',
    failed_count     INT          DEFAULT 0 COMMENT '失败事件数（单条失败不阻断任务）',
    cursor_id        BIGINT       DEFAULT 0 COMMENT '回放游标（最后已处理的事件存储主键，增量拉取）',
    note             VARCHAR(500) COMMENT '备注（失败原因/人工说明）',
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_replay_no (replay_no),
    KEY idx_replay_admin (operator_admin_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===== ITER-F07 会员积分成长深化（幂等） =====

-- 成长值流水（REQ-09 全量留痕可对账）：来源、规则版本快照（REQ-14 不追溯）、变更前/后值、关联单号。
-- (user_id, biz_type, biz_id) 唯一索引承担发放幂等兜底，杜绝同一订单 / 评价重复计成长值。
CREATE TABLE IF NOT EXISTS t_growth_value_log (
    id            BIGINT       PRIMARY KEY,
    user_id       BIGINT       NOT NULL COMMENT '用户 ID',
    biz_type      VARCHAR(32)  NOT NULL COMMENT '来源 code（EARN_TRADE/DEDUCT_DISPUTE/DECAY/RESTORE_DISPUTE 等）',
    biz_id        VARCHAR(64)  COMMENT '关联单号（订单号/商品ID/评价ID/工单号/衰减日期）',
    delta         INT          NOT NULL DEFAULT 0 COMMENT '成长值增量（正=获得，负=扣减；0=拦截留痕）',
    before_value  INT          NOT NULL DEFAULT 0 COMMENT '变更前成长值',
    after_value   INT          NOT NULL DEFAULT 0 COMMENT '变更后成长值',
    level_code    VARCHAR(20)  COMMENT '变更后等级编码',
    rule_version  VARCHAR(32)  COMMENT '生效规则版本快照（REQ-14 规则变更不追溯重算）',
    frozen        TINYINT      NOT NULL DEFAULT 0 COMMENT '变更后发放通道冻结状态 0/1',
    remark        VARCHAR(500) COMMENT '说明（命中规则 / 判责原因 / 拦截原因）',
    created_at    DATETIME,
    updated_at    DATETIME,
    UNIQUE KEY uk_gvlog_user_biz (user_id, biz_type, biz_id),
    KEY idx_gvlog_user (user_id, created_at),
    KEY idx_gvlog_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 权益目录（REQ-07/12/13）：兑换消耗积分（可消耗），成长值只作等级门槛（REQ-01）。
-- 库存原子扣减 + 条件更新双保险防超发（REQ-08）；金额类权益面额一律「分」。
CREATE TABLE IF NOT EXISTS t_benefit (
    id                 BIGINT       PRIMARY KEY,
    benefit_code       VARCHAR(32)  NOT NULL COMMENT '权益编码（唯一）',
    benefit_name       VARCHAR(64)  NOT NULL COMMENT '权益名称',
    benefit_type       VARCHAR(32)  NOT NULL COMMENT 'COUPON/VOUCHER/POINT_PACKAGE/SERVICE',
    description        VARCHAR(500) COMMENT '权益说明（使用条件/有效期，合规明示）',
    cost_points        BIGINT       NOT NULL DEFAULT 0 COMMENT '所需积分（可消耗）',
    min_level_code     VARCHAR(20)  COMMENT '最低等级门槛（成长值决定，REQ-01/07）',
    coupon_id          BIGINT       COMMENT '券模板 ID（COUPON 类型必填）',
    voucher_amount_fen BIGINT       COMMENT '金额类权益面额（分，REQ-13）',
    stock              INT          NOT NULL DEFAULT 0 COMMENT '剩余库存',
    total_stock        INT          NOT NULL DEFAULT 0 COMMENT '总库存',
    daily_limit        INT          NOT NULL DEFAULT 0 COMMENT '每用户每日兑换上限，0=不限',
    status             VARCHAR(16)  NOT NULL DEFAULT 'ON_SHELF' COMMENT 'ON_SHELF/OFF_SHELF',
    online_at          DATETIME     COMMENT '上架时间（空=不限制）',
    offline_at         DATETIME     COMMENT '下架时间（空=不限制，到期自动回收 REQ-12）',
    sort_order         INT          NOT NULL DEFAULT 0 COMMENT '展示排序',
    created_at         DATETIME,
    updated_at         DATETIME,
    UNIQUE KEY uk_benefit_code (benefit_code),
    KEY idx_benefit_status (status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 权益兑换记录（REQ-07/09）：exchange_no 同时作为积分流水 bizId，实现三方对账。
CREATE TABLE IF NOT EXISTS t_benefit_exchange (
    id            BIGINT       PRIMARY KEY,
    exchange_no   VARCHAR(32)  NOT NULL COMMENT '兑换单号（BX 前缀）',
    benefit_id    BIGINT       NOT NULL COMMENT '权益 ID',
    benefit_code  VARCHAR(32)  NOT NULL COMMENT '权益编码快照',
    user_id       BIGINT       NOT NULL COMMENT '兑换用户',
    cost_points   BIGINT       NOT NULL DEFAULT 0 COMMENT '实际扣除积分',
    benefit_type  VARCHAR(32)  NOT NULL COMMENT '权益类型快照',
    coupon_id     BIGINT       COMMENT '券模板 ID 快照（COUPON 类型）',
    status        VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED',
    remark        VARCHAR(500) COMMENT '说明',
    created_at    DATETIME,
    updated_at    DATETIME,
    UNIQUE KEY uk_exchange_no (exchange_no),
    KEY idx_exchange_user (user_id, created_at),
    KEY idx_exchange_benefit (benefit_id, created_at),
    KEY idx_exchange_user_benefit (user_id, benefit_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- ===== ITER-F06 IM 客服智能化（PRD-P2-F06）=====
-- 知识库话术（REQ-11 品类话术优先）：category_id=0 为通用回落，keywords 逗号分隔小写比对。
CREATE TABLE IF NOT EXISTS t_im_knowledge (
    id              BIGINT       PRIMARY KEY,
    category_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '类目 ID，0=通用话术（全品类回落）',
    intent          VARCHAR(32)  NOT NULL COMMENT '意图枚举名（SHIPPING/PRICE/REFUND/INSPECTION/LOGISTICS/CHITCHAT）',
    keywords        VARCHAR(500) NOT NULL COMMENT '触发词（逗号分隔，小写比对）',
    answer_template VARCHAR(1000) NOT NULL COMMENT '答案模板（金额占位按「分」存储，展示时格式化为元）',
    priority        INT          NOT NULL DEFAULT 0 COMMENT '运营排序权重（同分竞争时生效）',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ON' COMMENT 'ON/OFF',
    remark          VARCHAR(500) COMMENT '说明',
    created_at      DATETIME,
    updated_at      DATETIME,
    KEY idx_imkb_intent (intent, status),
    KEY idx_imkb_cat (category_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 自动应答记录（REQ-10 来源标记可审计 / REQ-14 反馈入池）
CREATE TABLE IF NOT EXISTS t_im_auto_reply (
    id                 BIGINT       PRIMARY KEY,
    reply_no           VARCHAR(32)  NOT NULL COMMENT '应答单号（AR 前缀）',
    conv_id            VARCHAR(64)  NOT NULL COMMENT '会话 ID',
    item_id            BIGINT       COMMENT '商品 ID',
    buyer_id           BIGINT       COMMENT '买家（提问方）',
    seller_id          BIGINT       COMMENT '卖家（代答方）',
    trigger_message_id BIGINT       COMMENT '触发挥答的买家消息 ID',
    intent             VARCHAR(32)  COMMENT '识别意图',
    source             VARCHAR(32)  NOT NULL COMMENT 'KNOWLEDGE/TEMPLATE_FALLBACK/LLM',
    confidence         DOUBLE       COMMENT '匹配置信度（0~1）',
    content            VARCHAR(1024) NOT NULL COMMENT '实际发出内容（脱敏后）',
    question           VARCHAR(255) COMMENT '触发问题的脱敏原文（反馈入池定位 REQ-14）',
    summary            VARCHAR(1000) COMMENT '升级时的会话摘要（REQ-07，仅 escalated=1 有值）',
    feedback           VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE/USEFUL/USELESS',
    escalated          INT          NOT NULL DEFAULT 0 COMMENT '是否伴随转人工 0/1',
    escalate_reason    VARCHAR(32)  COMMENT '转人工原因',
    created_at         DATETIME,
    updated_at         DATETIME,
    UNIQUE KEY uk_reply_no (reply_no),
    KEY idx_ar_conv (conv_id, created_at),
    KEY idx_ar_intent (intent, created_at),
    KEY idx_ar_feedback (feedback)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 纠纷预警记录（REQ-06 信号预警 + 一键 F-17 提示）
CREATE TABLE IF NOT EXISTS t_im_dispute_warning (
    id           BIGINT       PRIMARY KEY,
    warning_no   VARCHAR(32)  NOT NULL COMMENT '预警单号（DW 前缀）',
    conv_id      VARCHAR(64)  NOT NULL COMMENT '会话 ID',
    item_id      BIGINT       COMMENT '商品 ID',
    buyer_id     BIGINT       COMMENT '买家',
    seller_id    BIGINT       COMMENT '卖家',
    risk_score   INT          NOT NULL DEFAULT 0 COMMENT '风险分（封顶 100）',
    level        VARCHAR(16)  NOT NULL COMMENT 'WARN/HIGH',
    signals      VARCHAR(255) COMMENT '命中信号（逗号分隔）',
    notice_text  VARCHAR(500) COMMENT '会话内提示卡文案',
    handled_flag INT          NOT NULL DEFAULT 0 COMMENT '人工是否已跟进 0/1',
    handled_by   BIGINT       COMMENT '跟进人（管理员 userId）',
    created_at   DATETIME,
    updated_at   DATETIME,
    UNIQUE KEY uk_warning_no (warning_no),
    KEY idx_dw_conv (conv_id, created_at),
    KEY idx_dw_level (level, handled_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 内容拦截记录（REQ-08 屏蔽词/引流拦截并记风控；文本脱敏后存储）
CREATE TABLE IF NOT EXISTS t_im_content_block (
    id           BIGINT       PRIMARY KEY,
    conv_id      VARCHAR(64)  NOT NULL COMMENT '会话 ID',
    user_id      BIGINT       COMMENT '触发用户',
    hit_type     VARCHAR(16)  NOT NULL COMMENT 'CONTACT（站外联系方式）/LEAD（引流话术）',
    matched_text VARCHAR(255) COMMENT '脱敏后的命中文本',
    action       VARCHAR(32)  COMMENT '处置：BLOCK_AND_ESCALATE',
    created_at   DATETIME,
    updated_at   DATETIME,
    KEY idx_cb_conv (conv_id, created_at),
    KEY idx_cb_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 未命中问题池（REQ-14 点踩/追问入池，供运营补充话术）
CREATE TABLE IF NOT EXISTS t_im_unanswered (
    id           BIGINT       PRIMARY KEY,
    question     VARCHAR(255) NOT NULL COMMENT '脱敏后的问题原文',
    intent       VARCHAR(32)  COMMENT '识别意图（可为空）',
    category_id  BIGINT       COMMENT '商品类目 ID',
    hit_count    INT          NOT NULL DEFAULT 1 COMMENT '累计出现次数',
    last_conv_id VARCHAR(64)  COMMENT '最近一次出现的会话',
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RESOLVED',
    created_at   DATETIME,
    updated_at   DATETIME,
    KEY idx_un_status (status, hit_count),
    KEY idx_un_intent (intent)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- ===== ITER-F08 营销玩法扩展（幂等） =====
-- 统一活动内核 + 三玩法（砍价/抽奖/直播秒杀）共用一套定义表与参与记录表；玩法特有表承载逐刀/奖品/结算。

-- 营销活动定义表：砍价/抽奖/直播秒杀共用一张，玩法特有配置序列化于 play_config（JSON）（REQ-01 复用内核）
CREATE TABLE IF NOT EXISTS t_marketing_activity (
    id                BIGINT       PRIMARY KEY,
    kind              VARCHAR(32)  NOT NULL COMMENT '玩法类型 BARGAIN/LOTTERY/LIVE_SECKILL',
    title             VARCHAR(128) NOT NULL COMMENT '活动标题',
    item_id           BIGINT       COMMENT '关联商品（秒杀/砍价标的）',
    activity_price    BIGINT       COMMENT '基准价/秒杀价（分）',
    stock             INT          NOT NULL DEFAULT 0 COMMENT '活动库存（营销配额，独立于商品库存）',
    sold_count        INT          NOT NULL DEFAULT 0 COMMENT '已锁定/已售数量',
    limit_per_user    INT          DEFAULT 0 COMMENT '每人限购（<=0 表示不限，REQ-03）',
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ONGOING/ENDED',
    start_at          DATETIME     COMMENT '活动开始时间',
    end_at            DATETIME     COMMENT '活动结束时间',
    audience_type     VARCHAR(32)  DEFAULT 'ALL' COMMENT '人群定向 ALL/NEW_USER/LEVEL/REALNAME（REQ-13）',
    audience_value    VARCHAR(64)  COMMENT '定向参数：NEW_USER=天数 / LEVEL=最低信用分',
    risk_level        VARCHAR(16)  DEFAULT 'LOW' COMMENT '风控等级 LOW/MID/HIGH（REQ-05）',
    subsidy_budget_fen BIGINT     DEFAULT 0 COMMENT '补贴预算（分，REQ-15）',
    subsidy_used_fen  BIGINT       DEFAULT 0 COMMENT '已用补贴（分，原子累加）',
    loss_floor_fen    BIGINT       DEFAULT 0 COMMENT '成本线（分）：低于此值配置阶段阻断（REQ-14）',
    play_config       TEXT         COMMENT '玩法特有配置 JSON',
    created_at        DATETIME,
    updated_at        DATETIME,
    KEY idx_act_kind (kind, status),
    KEY idx_act_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 活动参与记录：所有玩法共用，承载幂等键（REQ-06）与补贴数据（REQ-15）
CREATE TABLE IF NOT EXISTS t_activity_record (
    id               BIGINT       PRIMARY KEY,
    record_no        VARCHAR(32)  NOT NULL COMMENT '记录单号（MR 前缀，幂等可读）',
    activity_id      BIGINT       NOT NULL COMMENT '活动 ID',
    user_id          BIGINT       NOT NULL COMMENT '用户 ID',
    kind             VARCHAR(32)  NOT NULL COMMENT '玩法类型',
    biz_id           VARCHAR(64)  COMMENT '玩法业务 ID：砍价会话/抽奖轮次/秒杀订单占位',
    idempotency_key  VARCHAR(64)  NOT NULL COMMENT '幂等键（REQ-06）',
    status           VARCHAR(32)  NOT NULL DEFAULT 'JOINED' COMMENT 'JOINED/WON/LOST/QUALIFIED/PENDING_REALNAME/SETTLED/CANCELLED',
    amount_fen       BIGINT       DEFAULT 0 COMMENT '成交金额/奖品价值（分）',
    subsidy_fen      BIGINT       DEFAULT 0 COMMENT '平台补贴（分）',
    coupon_id        BIGINT       COMMENT '关联发放券（F-10 链路）',
    device_id        VARCHAR(64)  COMMENT '设备指纹（REQ-05）',
    extra            TEXT         COMMENT '审计扩展 JSON：抽奖概率快照/砍价逐刀明细等（REQ-09）',
    created_at       DATETIME,
    updated_at       DATETIME,
    UNIQUE KEY uk_activity_idem (activity_id, idempotency_key),
    KEY idx_rec_user (user_id, activity_id),
    KEY idx_rec_no (record_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 活动对账流水（REQ-11）：每活动一条，结束/异常时结算；记录库存消耗/补贴成本/亏损
CREATE TABLE IF NOT EXISTS t_activity_reconciliation (
    id                BIGINT       PRIMARY KEY,
    activity_id       BIGINT       NOT NULL COMMENT '活动 ID',
    kind              VARCHAR(32)  COMMENT '玩法类型',
    stock_total       INT          DEFAULT 0 COMMENT '库存总量',
    stock_sold        INT          DEFAULT 0 COMMENT '实际售出/消耗',
    stock_recycled    INT          DEFAULT 0 COMMENT '回收未使用库存（REQ-11）',
    subsidy_total_fen BIGINT       DEFAULT 0 COMMENT '补贴总成本（分）',
    participant_count INT          DEFAULT 0 COMMENT '参与人次',
    draw_count        INT          DEFAULT 0 COMMENT '抽奖次数',
    win_count         INT          DEFAULT 0 COMMENT '中奖次数',
    loss_fen          BIGINT       DEFAULT 0 COMMENT '亏损额（分）= 成本−营收',
    status            VARCHAR(16)  DEFAULT 'OPEN' COMMENT 'OPEN/SETTLED',
    settled_at        DATETIME     COMMENT '结算时间',
    created_at        DATETIME,
    updated_at        DATETIME,
    KEY idx_recon_act (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 砍价会话（REQ-08）：一次砍价发起对应一条会话，达成底价/目标人数置 QUALIFIED 并生成下单资格
CREATE TABLE IF NOT EXISTS t_bargain_session (
    id                BIGINT       PRIMARY KEY,
    session_no        VARCHAR(32)  NOT NULL COMMENT '会话单号（BS 前缀，幂等可读）',
    activity_id       BIGINT       NOT NULL COMMENT '活动 ID',
    initiator_user_id BIGINT       NOT NULL COMMENT '发起人（享受砍后价下单的用户）',
    record_no         VARCHAR(32)  COMMENT '关联内核参与记录单号',
    current_price_fen BIGINT       NOT NULL COMMENT '当前价（分）',
    floor_price_fen   BIGINT       NOT NULL COMMENT '底价（分）',
    target_assist_count INT        DEFAULT 0 COMMENT '目标助力人数',
    assist_count      INT          DEFAULT 0 COMMENT '已助力人数',
    status            VARCHAR(16)  DEFAULT 'ONGOING' COMMENT 'ONGOING/QUALIFIED/EXPIRED',
    expire_at         DATETIME     COMMENT '活动结束时间（逾期未成团转 EXPIRED）',
    qualify_expire_at DATETIME     COMMENT '下单资格有效期（QUALIFIED 后生效，REQ-08）',
    created_at        DATETIME,
    updated_at        DATETIME,
    UNIQUE KEY uk_session_no (session_no),
    KEY idx_bs_act (activity_id, status),
    KEY idx_bs_user (initiator_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 砍价助力记录（REQ-08/REQ-09）：同一用户对同一会话仅可助力一次，逐刀明细供审计防刷
CREATE TABLE IF NOT EXISTS t_bargain_assist (
    id            BIGINT       PRIMARY KEY,
    session_id    BIGINT       NOT NULL COMMENT '会话 ID',
    session_no    VARCHAR(32)  NOT NULL COMMENT '会话单号',
    assist_user_id BIGINT      NOT NULL COMMENT '助力用户（好友）',
    device_id     VARCHAR(64)  COMMENT '设备指纹（防刷，REQ-05）',
    amount_fen    BIGINT       DEFAULT 0 COMMENT '本次助力砍价额（分）',
    created_at    DATETIME,
    updated_at    DATETIME,
    UNIQUE KEY uk_session_assist (session_id, assist_user_id),
    KEY idx_assist_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 抽奖奖品（REQ-09/REQ-10）：probability 配置概率（之和 ≤1，余量为未中奖），stock 原子扣减防超发
CREATE TABLE IF NOT EXISTS t_lottery_prize (
    id                 BIGINT       PRIMARY KEY,
    activity_id        BIGINT       NOT NULL COMMENT '活动 ID',
    prize_id           VARCHAR(32)  NOT NULL COMMENT '活动内奖品唯一标识',
    name               VARCHAR(128) NOT NULL COMMENT '奖品名称',
    type               VARCHAR(16)  NOT NULL DEFAULT 'COUPON' COMMENT 'COUPON/PHYSICAL/VIRTUAL',
    value_fen          BIGINT       DEFAULT 0 COMMENT '奖品价值（分），ROI 核算/高价值判定',
    probability        DECIMAL(10,6) DEFAULT 0 COMMENT '配置概率（0~1）',
    stock              INT          DEFAULT 0 COMMENT '奖品份数（原子扣减）',
    coupon_template_id BIGINT       COMMENT '关联券模板（type=COUPON，REQ-10 经 F-10 发放）',
    status             VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    created_at         DATETIME,
    updated_at         DATETIME,
    KEY idx_prize_act (activity_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 直播秒杀结算兜底任务（REQ-07）：MQ 发送失败时由 SeckillSettleRecovery 调度补偿，保证最终一致
CREATE TABLE IF NOT EXISTS t_seckill_settle_task (
    id             BIGINT       PRIMARY KEY,
    task_type      VARCHAR(32)  COMMENT '任务类型',
    biz_id         VARCHAR(64)  COMMENT '业务 ID',
    payload        TEXT         COMMENT '结算指令 JSON（SeckillSettleCommand）',
    status         VARCHAR(16)  DEFAULT 'pending' COMMENT 'pending/processing/done/failed',
    next_execute_at DATETIME    COMMENT '下次执行时间',
    retry_count    INT          DEFAULT 0 COMMENT '重试次数',
    created_at     DATETIME,
    updated_at     DATETIME,
    KEY idx_seckill_task (status, next_execute_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- ===== 幂等列迁移（存量库升级：仅当列缺失时补齐；新库由上方 CREATE TABLE 直接建齐） =====
-- 说明：CREATE TABLE IF NOT EXISTS 不会为已存在的表追加新列，历史库需在此处做幂等 ADD COLUMN。
-- 采用 information_schema 探测 + PREPARE 动态执行（兼容 spring.sql.init 的分号切分，无需 DELIMITER）。

-- F-02 鉴定验货：t_item.inspection_status
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 't_item' AND column_name = 'inspection_status');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_item ADD COLUMN inspection_status VARCHAR(16) DEFAULT ''NONE'' COMMENT ''验货标识：NONE/INSPECTING/PASSED/REJECTED''',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;

-- ITER-F07：t_user_growth 追加冻结 / 活跃 / 等级生效时间 / 通知时间 / 累计获得五列
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 't_user_growth' AND column_name = 'growth_frozen');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_user_growth ADD COLUMN growth_frozen TINYINT NOT NULL DEFAULT 0 COMMENT ''0/1 发放通道冻结（REQ-03 刷分/封禁）''',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 't_user_growth' AND column_name = 'last_active_at');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_user_growth ADD COLUMN last_active_at DATETIME COMMENT ''最后活跃时间（REQ-05 衰减基准）''',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 't_user_growth' AND column_name = 'level_effective_at');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_user_growth ADD COLUMN level_effective_at DATETIME COMMENT ''当前等级生效时间（REQ-04 跨阈值即时升级）''',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 't_user_growth' AND column_name = 'decay_notified_at');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_user_growth ADD COLUMN decay_notified_at DATETIME COMMENT ''最近一次衰减提前通知时间（REQ-05 提前7日）''',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 't_user_growth' AND column_name = 'total_earned');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_user_growth ADD COLUMN total_earned INT NOT NULL DEFAULT 0 COMMENT ''累计获得成长值（对账口径，REQ-09）''',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;

-- ITER-F06 IM 客服智能化：t_conversation 追加自动代答开关 / 人工接管标记两列
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 't_conversation' AND column_name = 'auto_reply_enabled');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_conversation ADD COLUMN auto_reply_enabled INT DEFAULT 1 COMMENT ''ITER-F06 智能助手会话级开关：0=卖家关闭代答，1=开启''',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 't_conversation' AND column_name = 'human_takeover');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_conversation ADD COLUMN human_takeover INT DEFAULT 0 COMMENT ''ITER-F06 人工接管标记：置 1 后停止一切自动作答（REQ-09）''',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 't_conversation' AND column_name = 'dispute_risk_score');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_conversation ADD COLUMN dispute_risk_score INT DEFAULT 0 COMMENT ''ITER-F06 会话累计纠纷风险分（窗口内，封顶 100）''',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 't_conversation' AND column_name = 'dispute_risk_at');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_conversation ADD COLUMN dispute_risk_at DATETIME COMMENT ''ITER-F06 风险分最近更新时间（窗口重置基准）''',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;

-- ITER-F06：t_im_auto_reply 追加触发问题原文 / 升级会话摘要两列
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 't_im_auto_reply' AND column_name = 'question');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_im_auto_reply ADD COLUMN question VARCHAR(255) COMMENT ''触发问题的脱敏原文（反馈入池定位 REQ-14）''',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 't_im_auto_reply' AND column_name = 'summary');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_im_auto_reply ADD COLUMN summary VARCHAR(1000) COMMENT ''升级时的会话摘要（REQ-07，仅 escalated=1 有值）''',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;

-- F-12 补完：t_work_order 追加「维权关联」与「时效 SLA」四列（沿用幂等迁移范式，重复执行安全）
-- 1) dispute_no：维权单转人工的关联键（可空，通用咨询工单不绑定）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 't_work_order' AND column_name = 'dispute_no');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_work_order ADD COLUMN dispute_no VARCHAR(64) DEFAULT NULL COMMENT ''关联维权工单号（F-12 纠纷联通，可空）''',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;

-- 2) first_response_deadline：首响到期时间（建单时按优先级计算，见 WorkOrderSlaPolicy）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 't_work_order' AND column_name = 'first_response_deadline');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_work_order ADD COLUMN first_response_deadline DATETIME DEFAULT NULL COMMENT ''首响到期时间（SLA，按优先级计算）''',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;

-- 3) resolve_deadline：解决到期时间
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 't_work_order' AND column_name = 'resolve_deadline');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_work_order ADD COLUMN resolve_deadline DATETIME DEFAULT NULL COMMENT ''解决到期时间（SLA，按优先级计算）''',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;

-- 4) sla_breached：是否超时（0 未超时 / 1 已超时），扫描任务与流转时回填
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE() AND table_name = 't_work_order' AND column_name = 'sla_breached');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_work_order ADD COLUMN sla_breached TINYINT NOT NULL DEFAULT 0 COMMENT ''是否超时：0 未超时 / 1 已超时（SLA）''',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;

-- 5) 维权关联索引：支撑「同一维权单未结工单」幂等查询与后台按维权号检索
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE() AND table_name = 't_work_order' AND index_name = 'idx_wo_dispute');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE t_work_order ADD INDEX idx_wo_dispute (dispute_no)',
    'SELECT 1');
PREPARE mig_stmt FROM @ddl;
EXECUTE mig_stmt;
DEALLOCATE PREPARE mig_stmt;
