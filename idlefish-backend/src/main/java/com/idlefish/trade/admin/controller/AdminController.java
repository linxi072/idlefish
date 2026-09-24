package com.idlefish.trade.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.mapper.AdminUserMapper;
import com.idlefish.trade.admin.service.AdminService;
import com.idlefish.trade.admin.vo.AdminLoginVO;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.util.JwtUtil;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.service.AttrTemplateService;
import com.idlefish.trade.item.service.CategoryService;
import com.idlefish.trade.item.vo.CategoryVO;
import com.idlefish.trade.risk.entity.AuditLog;
import com.idlefish.trade.risk.entity.RiskEvent;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.Withdrawal;
import com.idlefish.trade.trade.service.OrderService;
import com.idlefish.trade.trade.service.ReconciliationService;
import com.idlefish.trade.trade.service.RefundService;
import com.idlefish.trade.trade.service.ReviewService;
import com.idlefish.trade.trade.service.SettlementService;
import com.idlefish.trade.trade.service.WithdrawalService;
import com.idlefish.trade.user.entity.User;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 运营后台接口（PRD §7）。
 * 鉴权改为独立管理员登录态：/api/admin/auth/login 校验账号密码并签发 JWT，
 * 角色由服务端读取（不信任客户端）。所有写操作经 RBAC 权限校验与审计留痕。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final AdminUserMapper adminUserMapper;
    private final JwtUtil jwtUtil;
    private final OrderService orderService;
    private final RefundService refundService;
    private final CategoryService categoryService;
    private final AttrTemplateService attrTemplateService;
    private final WithdrawalService withdrawalService;
    private final ReconciliationService reconciliationService;
    private final SettlementService settlementService;
    private final ReviewService reviewService;

    public AdminController(AdminService adminService, AdminUserMapper adminUserMapper, JwtUtil jwtUtil,
                           OrderService orderService, RefundService refundService, CategoryService categoryService,
                           AttrTemplateService attrTemplateService, WithdrawalService withdrawalService,
                           ReconciliationService reconciliationService, SettlementService settlementService,
                           ReviewService reviewService) {
        this.adminService = adminService;
        this.adminUserMapper = adminUserMapper;
        this.jwtUtil = jwtUtil;
        this.orderService = orderService;
        this.refundService = refundService;
        this.categoryService = categoryService;
        this.attrTemplateService = attrTemplateService;
        this.withdrawalService = withdrawalService;
        this.reconciliationService = reconciliationService;
        this.settlementService = settlementService;
        this.reviewService = reviewService;
    }

    /** 后台登录：校验账号密码，签发管理员 JWT（角色由服务端决定）。 */
    @PostMapping("/auth/login")
    public Result<AdminLoginVO> login(@RequestParam String username, @RequestParam String password) {
        AdminUser u = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, username));
        if (u == null || !com.idlefish.trade.common.util.PasswordUtils.matches(password, u.getPassword())) {
            throw new BizException(Code.UNAUTHORIZED, "用户名或密码错误");
        }
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw new BizException(Code.FORBIDDEN, "账号已禁用");
        }
        String token = jwtUtil.generateAdmin(u.getId(), u.getRole());
        return Result.ok(new AdminLoginVO(token, u.getRole(), u.getNickname(), u.getId()));
    }

    /** 审核列表（默认待审核），支持状态过滤与关键字（标题）搜索。 */
    @GetMapping("/items")
    public Result<IPage<Item>> items(@CurrentAdmin AdminUser admin,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.auditList(status, keyword, page, size));
    }

    /** 通过审核。 */
    @PostMapping("/items/{itemId}/approve")
    public Result<Void> approve(@CurrentAdmin AdminUser admin, @PathVariable Long itemId) {
        adminService.approve(admin.getRole(), admin.getId(), itemId);
        return Result.ok();
    }

    /** 驳回审核。 */
    @PostMapping("/items/{itemId}/reject")
    public Result<Void> reject(@CurrentAdmin AdminUser admin, @PathVariable Long itemId,
                               @RequestParam String reason) {
        adminService.reject(admin.getRole(), admin.getId(), itemId, reason);
        return Result.ok();
    }

    /** 订单列表，支持状态过滤与关键字（订单号/商品标题）搜索。 */
    @GetMapping("/orders")
    public Result<IPage<Order>> orders(@CurrentAdmin AdminUser admin,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.orderList(status, keyword, page, size));
    }

    /** 订单详情。 */
    @GetMapping("/orders/{orderNo}")
    public Result<Order> orderDetail(@CurrentAdmin AdminUser admin, @PathVariable String orderNo) {
        return Result.ok(adminService.orderDetail(orderNo));
    }

    /** 用户列表，支持状态过滤与关键字（昵称/手机号）搜索。 */
    @GetMapping("/users")
    public Result<IPage<User>> users(@CurrentAdmin AdminUser admin,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.userList(status, keyword, page, size));
    }

    /** 封禁 / 解封用户（status=1 封禁，0 解封）。 */
    @PostMapping("/users/{userId}/ban")
    public Result<Void> ban(@CurrentAdmin AdminUser admin, @PathVariable Long userId,
                            @RequestParam Integer status) {
        adminService.banUser(admin.getRole(), admin.getId(), userId, status);
        return Result.ok();
    }

    /** 新增类目。 */
    @PostMapping("/categories")
    public Result<Long> categories(@CurrentAdmin AdminUser admin,
                                  @RequestParam(required = false) Long parentId,
                                  @RequestParam String name,
                                  @RequestParam(required = false) String icon,
                                  @RequestParam(required = false) Integer level,
                                  @RequestParam(required = false) Integer sort,
                                  @RequestParam(required = false) Integer isLeaf) {
        return Result.ok(adminService.createCategory(admin.getRole(), admin.getId(),
                parentId, name, icon, level, sort, isLeaf));
    }

    /** 类目树（GET，供后台类目管理页）。 */
    @GetMapping("/categories")
    public Result<List<CategoryVO>> categoryTree(@CurrentAdmin AdminUser admin) {
        return Result.ok(categoryService.tree());
    }

    /** 风控事件列表，支持关键字（规则名/规则码/业务ID）搜索。 */
    @GetMapping("/risks")
    public Result<IPage<RiskEvent>> risks(@CurrentAdmin AdminUser admin,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.riskList(keyword, page, size));
    }

    /** 审计日志列表，支持关键字（动作/详情/目标类型）搜索。 */
    @GetMapping("/audit-logs")
    public Result<IPage<AuditLog>> auditLogs(@CurrentAdmin AdminUser admin,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.auditLogList(keyword, page, size));
    }

    /** 后台代发货（运营操作，R-13）。 */
    @PostMapping("/orders/{orderNo}/ship")
    public Result<Void> ship(@CurrentAdmin AdminUser admin, @PathVariable String orderNo,
                             @RequestParam(required = false) String logisticsNo) {
        orderService.adminShip(orderNo, logisticsNo, admin.getId());
        return Result.ok();
    }

    /** 后台代处理退款同意（运营操作，R-13）。 */
    @PostMapping("/orders/{orderNo}/refund")
    public Result<Void> refund(@CurrentAdmin AdminUser admin, @PathVariable String orderNo) {
        refundService.adminAgree(orderNo);
        return Result.ok();
    }

    // ===== 类目属性模板（PRD §B1） =====

    /** 类目属性模板列表。 */
    @GetMapping("/attr-templates")
    public Result<List<com.idlefish.trade.item.entity.AttrTemplate>> attrTemplates(@CurrentAdmin AdminUser admin,
                                                                                @RequestParam Long categoryId) {
        return Result.ok(attrTemplateService.listByCategory(categoryId));
    }

    /** 新增类目属性模板。 */
    @PostMapping("/attr-template")
    public Result<Long> attrTemplate(@CurrentAdmin AdminUser admin,
                                    @RequestParam Long categoryId,
                                    @RequestParam String name,
                                    @RequestParam(required = false) String options,
                                    @RequestParam(required = false) Integer required,
                                    @RequestParam(required = false) Integer sort) {
        return Result.ok(attrTemplateService.create(categoryId, name, options, required, sort));
    }

    // ===== 提现与对账（PRD §F4） =====

    /** 提现申请列表。 */
    @GetMapping("/withdrawals")
    public Result<List<Withdrawal>> withdrawals(@CurrentAdmin AdminUser admin) {
        return Result.ok(withdrawalService.list());
    }

    /** 审批通过提现（实际出账）。 */
    @PostMapping("/withdrawals/{id}/approve")
    public Result<Void> approveWithdrawal(@CurrentAdmin AdminUser admin, @PathVariable Long id) {
        withdrawalService.approve(id);
        return Result.ok();
    }

    /** 驳回提现（解冻）。 */
    @PostMapping("/withdrawals/{id}/reject")
    public Result<Void> rejectWithdrawal(@CurrentAdmin AdminUser admin, @PathVariable Long id) {
        withdrawalService.reject(id);
        return Result.ok();
    }

    /** 资金对账报告（默认昨日）。 */
    @GetMapping("/reconciliation")
    public Result<Map<String, Object>> reconciliation(@CurrentAdmin AdminUser admin,
                                                     @RequestParam(required = false) String day) {
        LocalDate d = null;
        if (day != null && !day.isBlank()) {
            try {
                d = LocalDate.parse(day);
            } catch (Exception ignored) {
                d = null;
            }
        }
        return Result.ok(reconciliationService.reconcile(d));
    }

    // ===== 结算风控解冻（PRD §D7） =====

    /** 解冻被风控冻结的结算单。 */
    @PostMapping("/settlements/{id}/unfreeze")
    public Result<Void> unfreezeSettlement(@CurrentAdmin AdminUser admin, @PathVariable Long id) {
        settlementService.unfreeze(id);
        return Result.ok();
    }

    // ===== 评价管理（F-06） =====

    /** 待审核评价列表。 */
    @GetMapping("/reviews")
    public Result<List<com.idlefish.trade.trade.vo.ReviewVO>> reviews(@CurrentAdmin AdminUser admin) {
        return Result.ok(reviewService.pendingList());
    }

    /** 审核通过评价（触发被评价方信用重算）。 */
    @PostMapping("/reviews/{id}/approve")
    public Result<Void> approveReview(@CurrentAdmin AdminUser admin, @PathVariable Long id) {
        reviewService.approve(id);
        return Result.ok();
    }

    /** 审核驳回评价。 */
    @PostMapping("/reviews/{id}/reject")
    public Result<Void> rejectReview(@CurrentAdmin AdminUser admin, @PathVariable Long id,
                                     @RequestParam(required = false) String reason) {
        reviewService.reject(id, reason);
        return Result.ok();
    }
}
