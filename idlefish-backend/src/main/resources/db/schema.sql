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
    seq            BIGINT        DEFAULT 0,
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
    device_id      VARCHAR(64),
    ip             VARCHAR(64),
    ua             VARCHAR(512),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_track_user ON t_track_event (user_id, event);
CREATE INDEX IF NOT EXISTS idx_track_device ON t_track_event (device_id);

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

-- ===== P0 二次补齐：属性模板 / 议价 / 提现 / 商品状态日志 =====

CREATE TABLE IF NOT EXISTS t_attr_template (
    id             BIGINT        PRIMARY KEY,
    category_id    BIGINT,
    name           VARCHAR(64),
    options        VARCHAR(512),
    required       INT           DEFAULT 0,
    sort           INT           DEFAULT 0,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_attr_cat ON t_attr_template (category_id);

CREATE TABLE IF NOT EXISTS t_bargain (
    id             BIGINT        PRIMARY KEY,
    conv_id        VARCHAR(64),
    item_id        BIGINT,
    buyer_id       BIGINT,
    seller_id      BIGINT,
    origin_price   BIGINT,
    offer_price    BIGINT,
    status         VARCHAR(16),
    expire_at      TIMESTAMP,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_bargain_conv ON t_bargain (conv_id);
CREATE INDEX IF NOT EXISTS idx_bargain_seller ON t_bargain (seller_id, status);

CREATE TABLE IF NOT EXISTS t_withdrawal (
    id             BIGINT        PRIMARY KEY,
    user_id        BIGINT,
    amount         BIGINT,
    account        VARCHAR(128),
    status         VARCHAR(16),
    done_at        TIMESTAMP,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_withdrawal_user ON t_withdrawal (user_id, status);

CREATE TABLE IF NOT EXISTS t_item_status_log (
    id             BIGINT        PRIMARY KEY,
    item_id        BIGINT,
    from_status    VARCHAR(32),
    to_status      VARCHAR(32),
    operator_id    BIGINT,
    remark         VARCHAR(255),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_item_status_log ON t_item_status_log (item_id);

-- ===== 外部组件集成：设备指纹 / 物流 / 延时队列 =====

-- 设备指纹：同一设备可绑定多个账号，用于群控/接码识别与黑名单。
CREATE TABLE IF NOT EXISTS t_device_fingerprint (
    id             BIGINT        PRIMARY KEY,
    device_id      VARCHAR(64)   NOT NULL,
    user_id        BIGINT        NOT NULL,
    fp_hash        VARCHAR(64),
    first_seen     TIMESTAMP,
    last_seen      TIMESTAMP,
    status         VARCHAR(16)   DEFAULT 'normal',  -- normal / blacklisted
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    UNIQUE KEY uk_device_user (device_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_fp_device ON t_device_fingerprint (device_id);
CREATE INDEX IF NOT EXISTS idx_fp_user ON t_device_fingerprint (user_id);

-- 物流轨迹：发货后落库物流单与轨迹（mock 与真实实现均写入）。
CREATE TABLE IF NOT EXISTS t_logistics (
    id             BIGINT        PRIMARY KEY,
    order_no       VARCHAR(32),
    logistics_no   VARCHAR(64),
    company        VARCHAR(32),
    status         VARCHAR(16)   DEFAULT 'transport', -- transport / signed / exception
    detail_json    TEXT,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_logi_order ON t_logistics (order_no);
CREATE INDEX IF NOT EXISTS idx_logi_no ON t_logistics (logistics_no);

-- 延时任务：本地延时队列（D6）持久化，定时补偿触发；真实 RocketMQ 模式下作为兜底。
CREATE TABLE IF NOT EXISTS t_delay_task (
    id             BIGINT        PRIMARY KEY,
    task_type      VARCHAR(32)   NOT NULL,
    biz_id         VARCHAR(64),
    payload        VARCHAR(1024),
    status         VARCHAR(16)   DEFAULT 'pending', -- pending / processing / done / failed
    next_execute_at TIMESTAMP    NOT NULL,
    retry_count    INT           DEFAULT 0,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_delay_next ON t_delay_task (status, next_execute_at);

-- 站内通知：订单/支付/退款/审核等事件触达用户（F-02 通知中心）。
CREATE TABLE IF NOT EXISTS t_notification (
    id             BIGINT        PRIMARY KEY,
    user_id        BIGINT        NOT NULL,
    type           VARCHAR(32)   NOT NULL,   -- 通知类型 code，见 NotificationType
    biz_id         VARCHAR(64),                -- 业务主键：订单号/退款单号/商品ID
    title          VARCHAR(128)  NOT NULL,
    content        VARCHAR(512),
    is_read        INT           DEFAULT 0,   -- 0 未读 / 1 已读
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_notice_user ON t_notification (user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_notice_created ON t_notification (user_id, created_at);
