-- 闲置集 C2C 二手交易小程序后端 —— 初始化数据（V1.0）
-- 仅演示用：三级类目树 + 一个演示管理员。业务数据由接口调用产生。

-- ===== 一级类目 =====
INSERT INTO t_category (id, parent_id, name, icon, level, sort, is_leaf) VALUES
(100, 0, '数码电子', 'digital', 1, 1, 0),
(200, 0, '服饰鞋包', 'fashion', 1, 2, 0),
(300, 0, '家居家电', 'home',   1, 3, 0),
(400, 0, '图书文娱', 'book',   1, 4, 0),
(500, 0, '运动户外', 'sport',  1, 5, 0);

-- ===== 二级类目 =====
INSERT INTO t_category (id, parent_id, name, icon, level, sort, is_leaf) VALUES
(101, 100, '手机',     'phone',    2, 1, 0),
(102, 100, '电脑',     'computer', 2, 2, 0),
(103, 100, '相机',     'camera',   2, 3, 1),
(201, 200, '女装',     'women',    2, 1, 1),
(202, 200, '男装',     'men',      2, 2, 1),
(203, 200, '鞋靴',     'shoes',    2, 3, 1),
(301, 300, '大家电',   'appliance',2, 1, 1),
(302, 300, '家具',     'furniture',2, 2, 1);

-- ===== 三级类目 =====
INSERT INTO t_category (id, parent_id, name, icon, level, sort, is_leaf) VALUES
(1011, 101, '智能手机', 'smartphone', 3, 1, 1),
(1012, 101, '平板电脑', 'tablet',    3, 2, 1),
(1021, 102, '笔记本',   'laptop',    3, 1, 1),
(1022, 102, '台式机',   'desktop',   3, 2, 1);

-- ===== 演示管理员（角色 SUPER） =====
-- 密码以 PBKDF2 加盐哈希存储（R-20 安全治理，零外部依赖）；明文 admin123 仅供演示登录。
-- 格式：PBKDF2HMACSHA256:ITER:SALT:BASE64HASH
INSERT INTO t_admin_user (id, username, password, role, nickname, status) VALUES
(1, 'admin', 'PBKDF2HMACSHA256:10000:WCOPC+zTuZwdFFKwzNFvRg==:YvUqk84K3PVy/SfzHiZqOnpmvI1fklcxTnB9jk3jzG8=', 'SUPER', '超级管理员', 1);
