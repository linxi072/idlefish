package com.idlefish.trade.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.risk.entity.AuditLog;
import com.idlefish.trade.risk.mapper.AuditLogMapper;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.item.service.CategoryService;
import com.idlefish.trade.item.service.ItemService;
import com.idlefish.trade.risk.entity.RiskEvent;
import com.idlefish.trade.risk.mapper.RiskEventMapper;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 运营后台服务（PRD §7）：内容审核、订单管理、用户管理、类目管理、风控事件、审计留痕。
 * 所有写操作先经 RBAC 权限校验，再落库并写审计日志；操作者 ID 来自登录态（@CurrentAdmin）。
 */
@Service
public class AdminService {

    private final ItemService itemService;
    private final CategoryService categoryService;
    private final ItemMapper itemMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final RiskEventMapper riskEventMapper;
    private final AuditLogMapper auditLogMapper;
    private final AdminAuthService adminAuthService;

    public AdminService(ItemService itemService, CategoryService categoryService,
                        ItemMapper itemMapper, OrderMapper orderMapper, UserMapper userMapper,
                        RiskEventMapper riskEventMapper, AuditLogMapper auditLogMapper,
                        AdminAuthService adminAuthService) {
        this.itemService = itemService;
        this.categoryService = categoryService;
        this.itemMapper = itemMapper;
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.riskEventMapper = riskEventMapper;
        this.auditLogMapper = auditLogMapper;
        this.adminAuthService = adminAuthService;
    }

    /** 审核列表：按商品状态过滤（pending_review / on_sale / rejected），支持关键字（标题）模糊搜索。 */
    public IPage<Item> auditList(String status, String keyword, int page, int size) {
        LambdaQueryWrapper<Item> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            w.eq(Item::getStatus, status);
        } else {
            w.eq(Item::getStatus, "pending_review");
        }
        if (keyword != null && !keyword.isBlank()) {
            w.like(Item::getTitle, keyword);
        }
        w.orderByDesc(Item::getCreatedAt);
        return itemMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), w);
    }

    /** 通过审核。 */
    public void approve(String role, Long operatorId, Long itemId) {
        adminAuthService.require(role, "item:audit");
        itemService.audit(itemId, true, null);
        record(operatorId, "item:audit", "item", String.valueOf(itemId), "审核通过");
    }

    /** 驳回审核。 */
    public void reject(String role, Long operatorId, Long itemId, String reason) {
        adminAuthService.require(role, "item:audit");
        itemService.audit(itemId, false, reason);
        record(operatorId, "item:audit", "item", String.valueOf(itemId), "审核驳回:" + reason);
    }

    /** 订单列表：支持状态过滤与关键字（订单号/商品标题）模糊搜索。 */
    public IPage<Order> orderList(String status, String keyword, int page, int size) {
        LambdaQueryWrapper<Order> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            w.eq(Order::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            // 订单无独立标题列：标题存于 skuSnapshot(JSON)，关键字同时匹配订单号与快照（含标题）
            w.and(wp -> wp.like(Order::getOrderNo, keyword).or().like(Order::getSkuSnapshot, keyword));
        }
        w.orderByDesc(Order::getCreatedAt);
        return orderMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), w);
    }

    public Order orderDetail(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
    }

    /** 用户列表：支持状态过滤与关键字（昵称/手机号）模糊搜索。 */
    public IPage<User> userList(String status, String keyword, int page, int size) {
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            try {
                w.eq(User::getStatus, Integer.parseInt(status));
            } catch (NumberFormatException ignored) {
                // 非数字状态值忽略，避免非法入参导致 500
            }
        }
        if (keyword != null && !keyword.isBlank()) {
            w.and(wp -> wp.like(User::getNickname, keyword).or().like(User::getPhone, keyword));
        }
        w.orderByDesc(User::getCreatedAt);
        return userMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), w);
    }

    /** 封禁 / 解封用户。 */
    public void banUser(String role, Long operatorId, Long userId, Integer status) {
        adminAuthService.require(role, "user:ban");
        User upd = new User();
        upd.setId(userId);
        upd.setStatus(status);
        userMapper.updateById(upd);
        record(operatorId, "user:ban", "user", String.valueOf(userId), "设置状态:" + status);
    }

    /** 新增类目。 */
    public Long createCategory(String role, Long operatorId, Long parentId, String name, String icon,
                               Integer level, Integer sort, Integer isLeaf) {
        adminAuthService.require(role, "category:manage");
        Long id = categoryService.create(parentId, name, icon, level, sort, isLeaf);
        record(operatorId, "category:manage", "category", String.valueOf(id), "新增类目:" + name);
        return id;
    }

    /** 风控事件列表：支持关键字（规则名/规则码/业务ID）模糊搜索。 */
    public IPage<RiskEvent> riskList(String keyword, int page, int size) {
        LambdaQueryWrapper<RiskEvent> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(wp -> wp.like(RiskEvent::getRuleName, keyword)
                    .or().like(RiskEvent::getRuleCode, keyword)
                    .or().like(RiskEvent::getBizId, keyword));
        }
        w.orderByDesc(RiskEvent::getCreatedAt);
        return riskEventMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), w);
    }

    /** 审计日志列表：支持关键字（动作/详情/目标类型）模糊搜索。 */
    public IPage<AuditLog> auditLogList(String keyword, int page, int size) {
        LambdaQueryWrapper<AuditLog> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(wp -> wp.like(AuditLog::getAction, keyword)
                    .or().like(AuditLog::getDetail, keyword)
                    .or().like(AuditLog::getTargetType, keyword));
        }
        w.orderByDesc(AuditLog::getCreatedAt);
        return auditLogMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), w);
    }

    private void record(Long operatorId, String action, String targetType,
                        String targetId, String detail) {
        AuditLog log = new AuditLog();
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        auditLogMapper.insert(log);
    }
}
