-- 闲置集 C2C 二手交易小程序后端 —— MySQL 初始化数据（生产，对应 H2 data.sql）
-- 使用 INSERT IGNORE 保证 sql.init.mode=always 下重复启动幂等（库为持久化，不似 H2 内存每次重建）。

-- ===== 一级类目 =====
INSERT IGNORE INTO t_category (id, parent_id, name, icon, level, sort, is_leaf) VALUES
(100, 0, '数码电子', 'digital', 1, 1, 0),
(200, 0, '服饰鞋包', 'fashion', 1, 2, 0),
(300, 0, '家居家电', 'home',   1, 3, 0),
(400, 0, '图书文娱', 'book',   1, 4, 0),
(500, 0, '运动户外', 'sport',  1, 5, 0);

-- ===== 二级类目 =====
INSERT IGNORE INTO t_category (id, parent_id, name, icon, level, sort, is_leaf) VALUES
(101, 100, '手机',     'phone',    2, 1, 0),
(102, 100, '电脑',     'computer', 2, 2, 0),
(103, 100, '相机',     'camera',   2, 3, 1),
(201, 200, '女装',     'women',    2, 1, 1),
(202, 200, '男装',     'men',      2, 2, 1),
(203, 200, '鞋靴',     'shoes',    2, 3, 1),
(301, 300, '大家电',   'appliance',2, 1, 1),
(302, 300, '家具',     'furniture',2, 2, 1);

-- ===== 三级类目 =====
INSERT IGNORE INTO t_category (id, parent_id, name, icon, level, sort, is_leaf) VALUES
(1011, 101, '智能手机', 'smartphone', 3, 1, 1),
(1012, 101, '平板电脑', 'tablet',    3, 2, 1),
(1021, 102, '笔记本',   'laptop',    3, 1, 1),
(1022, 102, '台式机',   'desktop',   3, 2, 1);

-- ===== 演示管理员（角色 SUPER） =====
INSERT IGNORE INTO t_admin_user (id, username, password, role, nickname, org_id, status) VALUES
(1, 'admin', 'PBKDF2HMACSHA256:10000:WCOPC+zTuZwdFFKwzNFvRg==:YvUqk84K3PVy/SfzHiZqOnpmvI1fklcxTnB9jk3jzG8=', 'SUPER', '超级管理员', 1, 1);

-- ===== 平台客服（IM 客服入口固定主体） =====
INSERT IGNORE INTO t_user (id, wx_openid, nickname, credit_score, status, real_name_verified) VALUES
(10000, 'cs_platform', '平台客服', 100, 1, 1);

-- ===== 类目属性模板 =====
INSERT IGNORE INTO t_attr_template (id, category_id, name, options, required, sort) VALUES
(1, 1011, '成色', '["99新","95新","9成新","8成新"]', 1, 1),
(2, 1011, '内存', '["128G","256G","512G","1T"]', 0, 2),
(3, 1011, '保修', '["在保","过保"]', 0, 3);

-- ===== PC 系统管理种子（RBAC + 数据字典，F-05） =====
INSERT IGNORE INTO t_sys_organization (id, parent_id, name, code, level, sort, status) VALUES
(1, 0, '闲置集科技', 'ROOT', 1, 1, 1);

INSERT IGNORE INTO t_sys_role (id, name, code, status, sort, remark) VALUES
(1, '超级管理员', 'SUPER', 1, 1, '系统内置，拥有全部权限'),
(2, '运营专员', 'OPERATOR', 1, 2, '内容与订单运营'),
(3, '财务', 'FINANCE', 1, 3, '资金与对账');

INSERT IGNORE INTO t_sys_menu (id, parent_id, name, type, path, component, icon, perms, sort, status) VALUES
(1, 0, '系统管理', 0, '/system', 'Layout', 'setting', '', 1, 1),
(2, 1, '用户管理', 1, '/system/user', 'system/user', 'user', 'system:user:list', 1, 1),
(3, 1, '角色管理', 1, '/system/role', 'system/role', 'role', 'system:role:list', 2, 1),
(4, 1, '机构管理', 1, '/system/org', 'system/org', 'org', 'system:org:list', 3, 1),
(5, 1, '菜单管理', 1, '/system/menu', 'system/menu', 'menu', 'system:menu:list', 4, 1),
(6, 1, '字典管理', 1, '/system/dict', 'system/dict', 'dict', 'system:dict:list', 5, 1),
(7, 2, '用户新增', 2, '', '', '', 'system:user:add', 1, 1),
(8, 2, '用户编辑', 2, '', '', '', 'system:user:edit', 2, 1),
(9, 2, '用户删除', 2, '', '', '', 'system:user:delete', 3, 1),
(10, 3, '角色分配菜单', 2, '', '', '', 'system:role:assign', 1, 1),
(11, 3, '角色编辑', 2, '', '', '', 'system:role:edit', 2, 1),
(12, 4, '机构编辑', 2, '', '', '', 'system:org:edit', 1, 1),
(13, 5, '菜单编辑', 2, '', '', '', 'system:menu:edit', 1, 1),
(14, 6, '字典编辑', 2, '', '', '', 'system:dict:edit', 1, 1);

INSERT IGNORE INTO t_admin_user_role (id, admin_user_id, role_id) VALUES
(1, 1, 1);

INSERT IGNORE INTO t_role_menu (id, role_id, menu_id) VALUES
(1, 1, 1),(2, 1, 2),(3, 1, 3),(4, 1, 4),(5, 1, 5),(6, 1, 6),
(7, 1, 7),(8, 1, 8),(9, 1, 9),(10, 1, 10),(11, 1, 11),(12, 1, 12),(13, 1, 13),(14, 1, 14),
(15, 2, 2),(16, 2, 4),(17, 2, 7),(18, 2, 8),
(19, 3, 2);

INSERT IGNORE INTO t_sys_dict_type (id, dict_type, dict_name, status, remark) VALUES
(1, 'sys_user_status', '管理员状态', 1, '正常/禁用'),
(2, 'sys_normal_disable', '系统正常/禁用', 1, '通用开关'),
(3, 'item_status', '商品状态', 1, '在售/售罄/待审核等');

INSERT IGNORE INTO t_sys_dict_data (id, dict_type, dict_label, dict_value, dict_sort, status) VALUES
(1, 'sys_user_status', '正常', '1', 1, 1),
(2, 'sys_user_status', '禁用', '0', 2, 1),
(3, 'sys_normal_disable', '正常', '1', 1, 1),
(4, 'sys_normal_disable', '禁用', '0', 2, 1),
(5, 'item_status', '在售', 'on_sale', 1, 1),
(6, 'item_status', '已售罄', 'sold_out', 2, 1),
(7, 'item_status', '待审核', 'pending_review', 3, 1),
(8, 'item_status', '已驳回', 'rejected', 4, 1),
(9, 'item_status', '已下架', 'removed', 5, 1);
