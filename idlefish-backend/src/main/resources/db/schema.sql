-- 闲置集 C2C 二手交易小程序后端 —— H2 初始化 DDL（V1.0）
-- 生产环境替换为 MySQL 等价建表语句（仅改类型/引擎即可）。
-- 表名/字段统一小写 + 下划线，与 MyBatis-Plus map-underscore-to-camel-case 对应。

CREATE TABLE IF NOT EXISTS t_user (
    id             BIGINT        PRIMARY KEY,
    wx_openid      VARCHAR(64),
    wx_unionid     VARCHAR(64),
    phone          VARCHAR(128),
    nickname       VARCHAR(64),
    avatar         VARCHAR(255),
    credit_score   INT,
    status         INT           DEFAULT 0,
    real_name_verified INT       DEFAULT 0,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_address (
    id             BIGINT        PRIMARY KEY,
    user_id        BIGINT,
    receiver_name  VARCHAR(64),
    phone          VARCHAR(128),
    province       VARCHAR(64),
    city           VARCHAR(64),
    district       VARCHAR(64),
    detail         VARCHAR(255),
    is_default     INT           DEFAULT 0,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_address_user ON t_address (user_id);

CREATE TABLE IF NOT EXISTS t_category (
    id             BIGINT        PRIMARY KEY,
    parent_id      BIGINT        DEFAULT 0,
    name           VARCHAR(64),
    icon           VARCHAR(255),
    level          INT           DEFAULT 1,
    sort           INT           DEFAULT 0,
    is_leaf        INT           DEFAULT 1,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_category_parent ON t_category (parent_id);

CREATE TABLE IF NOT EXISTS t_item (
    id             BIGINT        PRIMARY KEY,
    seller_id      BIGINT,
    category_id    BIGINT,
    title          VARCHAR(128),
    description    TEXT,
    price          BIGINT,
    original_price BIGINT,
    images         TEXT,
    video_url      VARCHAR(255),
    condition_level INT,
    stock          INT           DEFAULT 1,
    province       VARCHAR(64),
    city           VARCHAR(64),
    freight        BIGINT        DEFAULT 0,
    status         VARCHAR(32),
    audit_status   VARCHAR(32),
    audit_reason   VARCHAR(255),
    view_count     INT           DEFAULT 0,
    like_count     INT           DEFAULT 0,
    fav_count      INT           DEFAULT 0,
    version        INT           DEFAULT 0,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_item_status ON t_item (status, category_id, created_at);
CREATE INDEX IF NOT EXISTS idx_item_seller ON t_item (seller_id);

CREATE TABLE IF NOT EXISTS t_order (
    id             BIGINT        PRIMARY KEY,
    order_no       VARCHAR(32)   UNIQUE,
    buyer_id       BIGINT,
    seller_id      BIGINT,
    item_id        BIGINT,
    sku_snapshot   TEXT,
    quantity       INT           DEFAULT 1,
    unit_price     BIGINT,
    total_amount   BIGINT,
    freight        BIGINT,
    pay_amount     BIGINT,
    status         VARCHAR(32),
    version        INT           DEFAULT 0,
    address_snapshot TEXT,
    remark         VARCHAR(255),
    pay_no         VARCHAR(32)   UNIQUE,
    logistics_no   VARCHAR(64),
    close_type     VARCHAR(32),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_order_buyer ON t_order (buyer_id, status);
CREATE INDEX IF NOT EXISTS idx_order_seller ON t_order (seller_id, status);

CREATE TABLE IF NOT EXISTS t_pay_order (
    id             BIGINT        PRIMARY KEY,
    pay_no         VARCHAR(32)   UNIQUE,
    order_no       VARCHAR(32),
    buyer_id       BIGINT,
    amount         BIGINT,
    channel        VARCHAR(32),
    transaction_id VARCHAR(64),
    status         VARCHAR(32),
    paid_at        TIMESTAMP,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_pay_order_no ON t_pay_order (order_no);

CREATE TABLE IF NOT EXISTS t_refund (
    id             BIGINT        PRIMARY KEY,
    refund_no      VARCHAR(32)   UNIQUE,
    order_no       VARCHAR(32),
    pay_no         VARCHAR(32),
    buyer_id       BIGINT,
    seller_id      BIGINT,
    item_id        BIGINT,
    type           VARCHAR(32),
    amount         BIGINT,
    reason         VARCHAR(255),
    status         VARCHAR(32),
    logistics_no   VARCHAR(64),
    auto_agree_at  TIMESTAMP,
    platform_at    TIMESTAMP,
    refund_at      TIMESTAMP,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_refund_order ON t_refund (order_no);
CREATE INDEX IF NOT EXISTS idx_refund_status ON t_refund (status);

CREATE TABLE IF NOT EXISTS t_settlement (
    id             BIGINT        PRIMARY KEY,
    settle_no      VARCHAR(32)   UNIQUE,
    seller_id      BIGINT,
    order_no       VARCHAR(32)   UNIQUE,
    amount         BIGINT,
    platform_fee   BIGINT,
    status         VARCHAR(32),
    settle_at      TIMESTAMP,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_settle_seller ON t_settlement (seller_id, status);

CREATE TABLE IF NOT EXISTS t_fund_flow (
    id             BIGINT        PRIMARY KEY,
    biz_no         VARCHAR(32)   UNIQUE,
    user_id        BIGINT,
    direction      VARCHAR(16),
    amount         BIGINT,
    type           VARCHAR(32),
    balance_after  BIGINT,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_fund_biz ON t_fund_flow (biz_no);
CREATE INDEX IF NOT EXISTS idx_fund_user ON t_fund_flow (user_id, direction);

CREATE TABLE IF NOT EXISTS t_conversation (
    id             BIGINT        PRIMARY KEY,
    conv_id        VARCHAR(64)   UNIQUE,
    buyer_id       BIGINT,
    seller_id      BIGINT,
    item_id        BIGINT,
    last_message   VARCHAR(255),
    last_sender_id BIGINT,
    buyer_unread   INT           DEFAULT 0,
    seller_unread  INT           DEFAULT 0,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_conv_buyer ON t_conversation (buyer_id);
CREATE INDEX IF NOT EXISTS idx_conv_seller ON t_conversation (seller_id);

CREATE TABLE IF NOT EXISTS t_message (
    id             BIGINT        PRIMARY KEY,
    conv_id        VARCHAR(64),
    sender_id      BIGINT,
    receiver_id    BIGINT,
    type           VARCHAR(16),
    content        VARCHAR(1024),
    read_flag      INT           DEFAULT 0,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_msg_conv ON t_message (conv_id, created_at);

CREATE TABLE IF NOT EXISTS t_track_event (
    id             BIGINT        PRIMARY KEY,
    user_id        BIGINT,
    event          VARCHAR(32),
    biz_id         VARCHAR(64),
    ext            VARCHAR(512),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_track_user ON t_track_event (user_id, event);

CREATE TABLE IF NOT EXISTS t_risk_event (
    id             BIGINT        PRIMARY KEY,
    user_id        BIGINT,
    rule_code      VARCHAR(32),
    rule_name      VARCHAR(64),
    level          VARCHAR(16),
    biz_type       VARCHAR(32),
    biz_id         VARCHAR(64),
    status         VARCHAR(16),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_risk_user ON t_risk_event (user_id, status);

CREATE TABLE IF NOT EXISTS t_audit_log (
    id             BIGINT        PRIMARY KEY,
    operator_id    BIGINT,
    action         VARCHAR(64),
    target_type    VARCHAR(32),
    target_id      VARCHAR(64),
    detail         VARCHAR(512),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_admin_user (
    id             BIGINT        PRIMARY KEY,
    username       VARCHAR(64),
    password       VARCHAR(128),
    role           VARCHAR(32),
    nickname       VARCHAR(64),
    status         INT           DEFAULT 1,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_favorite (
    id             BIGINT        PRIMARY KEY,
    user_id        BIGINT,
    item_id        BIGINT,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    UNIQUE KEY uk_user_item (user_id, item_id)
);
CREATE INDEX IF NOT EXISTS idx_favorite_user ON t_favorite (user_id);
