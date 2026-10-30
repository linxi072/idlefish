package com.idlefish.trade.workorder;

import com.idlefish.trade.workorder.service.WorkOrderService;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 客服工单时效 SLA 策略（F-12 补完）：无副作用纯函数，便于离线单测。
 *
 * <p>口径定义（均以「建单时间」为起算点，单位分钟）：
 * <ul>
 *   <li><b>首响（first response）</b>：建单 → 客服首次分配/响应（落 {@code first_response_at}）；</li>
 *   <li><b>解决（resolve）</b>：建单 → 流转到 RESOLVED（落 {@code resolved_at}）；未解决则以当前时间判定是否超期（超时扫描口径）。</li>
 * </ul>
 * 优先级越高阈值越短；未知优先级按 NORMAL 兜底，避免脏数据导致 NPE 或误判超时。
 */
public final class WorkOrderSlaPolicy {

    /** 首响阈值（分钟）。 */
    public static final long FIRST_RESPONSE_URGENT = 15L;
    public static final long FIRST_RESPONSE_HIGH = 30L;
    public static final long FIRST_RESPONSE_NORMAL = 120L;
    public static final long FIRST_RESPONSE_LOW = 480L;

    /** 解决阈值（分钟）。 */
    public static final long RESOLVE_URGENT = 120L;
    public static final long RESOLVE_HIGH = 480L;
    public static final long RESOLVE_NORMAL = 1440L;   // 24h
    public static final long RESOLVE_LOW = 4320L;      // 72h

    private static final Map<String, Long> FIRST_RESPONSE_MINUTES = buildFirstResponseMinutes();
    private static final Map<String, Long> RESOLVE_MINUTES = buildResolveMinutes();

    private WorkOrderSlaPolicy() {
    }

    private static Map<String, Long> buildFirstResponseMinutes() {
        Map<String, Long> m = new HashMap<>();
        m.put(WorkOrderService.PRIORITY_URGENT, FIRST_RESPONSE_URGENT);
        m.put(WorkOrderService.PRIORITY_HIGH, FIRST_RESPONSE_HIGH);
        m.put(WorkOrderService.PRIORITY_NORMAL, FIRST_RESPONSE_NORMAL);
        m.put(WorkOrderService.PRIORITY_LOW, FIRST_RESPONSE_LOW);
        return Collections.unmodifiableMap(m);
    }

    private static Map<String, Long> buildResolveMinutes() {
        Map<String, Long> m = new HashMap<>();
        m.put(WorkOrderService.PRIORITY_URGENT, RESOLVE_URGENT);
        m.put(WorkOrderService.PRIORITY_HIGH, RESOLVE_HIGH);
        m.put(WorkOrderService.PRIORITY_NORMAL, RESOLVE_NORMAL);
        m.put(WorkOrderService.PRIORITY_LOW, RESOLVE_LOW);
        return Collections.unmodifiableMap(m);
    }

    /** 首响阈值（分钟），未知优先级按 NORMAL 兜底。 */
    public static long firstResponseMinutes(String priority) {
        Long v = FIRST_RESPONSE_MINUTES.get(priority);
        return v == null ? FIRST_RESPONSE_NORMAL : v;
    }

    /** 解决阈值（分钟），未知优先级按 NORMAL 兜底。 */
    public static long resolveMinutes(String priority) {
        Long v = RESOLVE_MINUTES.get(priority);
        return v == null ? RESOLVE_NORMAL : v;
    }

    /** 首响到期时间；createdAt 为空返回 null（不判定）。 */
    public static LocalDateTime firstResponseDeadline(LocalDateTime createdAt, String priority) {
        if (createdAt == null) {
            return null;
        }
        return createdAt.plusMinutes(firstResponseMinutes(priority));
    }

    /** 解决到期时间；createdAt 为空返回 null（不判定）。 */
    public static LocalDateTime resolveDeadline(LocalDateTime createdAt, String priority) {
        if (createdAt == null) {
            return null;
        }
        return createdAt.plusMinutes(resolveMinutes(priority));
    }

    /**
     * 首响是否超时：已响应则以响应时间对比到期时间；未响应则以当前时间对比到期时间（超时扫描口径）。
     * createdAt 为空（无起算点）一律判未超时，避免脏数据误告警。
     */
    public static boolean firstResponseBreached(LocalDateTime createdAt, LocalDateTime firstResponseAt,
                                                String priority, LocalDateTime now) {
        LocalDateTime deadline = firstResponseDeadline(createdAt, priority);
        if (deadline == null) {
            return false;
        }
        LocalDateTime actual = firstResponseAt != null ? firstResponseAt : now;
        return actual != null && actual.isAfter(deadline);
    }

    /**
     * 解决是否超时：已解决则以解决时间对比到期时间；未解决且当前已过到期时间判超时（未结工单需预警）。
     */
    public static boolean resolveBreached(LocalDateTime createdAt, LocalDateTime resolvedAt,
                                          String priority, LocalDateTime now) {
        LocalDateTime deadline = resolveDeadline(createdAt, priority);
        if (deadline == null) {
            return false;
        }
        LocalDateTime actual = resolvedAt != null ? resolvedAt : now;
        return actual != null && actual.isAfter(deadline);
    }

    /** 工单整体是否超时（首响或解决任一超时即为超时）。 */
    public static boolean breached(LocalDateTime createdAt, LocalDateTime firstResponseAt,
                                   LocalDateTime resolvedAt, String priority, LocalDateTime now) {
        return firstResponseBreached(createdAt, firstResponseAt, priority, now)
                || resolveBreached(createdAt, resolvedAt, priority, now);
    }

    /**
     * 达标率 =（总数 - 超时数）/ 总数，返回 0~1；无样本（total &lt;= 0）默认 1.0（100% 达标），
     * 避免空窗期被误判为「0% 达标」触发无效告警。
     */
    public static double complianceRate(long total, long breached) {
        if (total <= 0) {
            return 1.0;
        }
        long ok = total - Math.min(breached, total);
        return ok * 1.0 / total;
    }
}
