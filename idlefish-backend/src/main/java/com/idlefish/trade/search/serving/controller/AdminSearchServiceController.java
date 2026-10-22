package com.idlefish.trade.search.serving.controller;

import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.search.serving.CircuitBreaker;
import com.idlefish.trade.search.serving.SearchServiceExecutor;
import com.idlefish.trade.search.serving.SearchServingFacade;
import com.idlefish.trade.search.serving.SyncLagTracker;
import com.idlefish.trade.search.serving.dto.ServingStatusVO;
import com.idlefish.trade.search.service.IndexRebuildService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 搜推服务化运营接口（ITER-T04 / REQ-04/07/08 运维面）：受统一后台鉴权（/api/admin/**）。
 * <ul>
 *   <li>状态总览：路由模式 / 熔断器 / 数据时效 / 隔离线程池 / 指标计数器；</li>
 *   <li>索引重建（内联模式）：全量重建兜底 + 重置滞后基线；远程模式明确提示由服务侧负责；</li>
 *   <li>熔断复位：故障恢复后立即闭合，免等待开放窗口。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/search-service")
public class AdminSearchServiceController {

    private final SearchServingFacade facade;
    private final CircuitBreaker breaker;
    private final SearchServiceExecutor executor;
    private final SyncLagTracker lagTracker;
    private final IndexRebuildService rebuildService;
    private final IdlefishProperties props;

    public AdminSearchServiceController(SearchServingFacade facade,
                                        CircuitBreaker breaker,
                                        SearchServiceExecutor executor,
                                        SyncLagTracker lagTracker,
                                        IndexRebuildService rebuildService,
                                        IdlefishProperties props) {
        this.facade = facade;
        this.breaker = breaker;
        this.executor = executor;
        this.lagTracker = lagTracker;
        this.rebuildService = rebuildService;
        this.props = props;
    }

    /** 状态总览（模式 / 熔断 / 滞后 / 隔离池 / 指标）。 */
    @GetMapping("/status")
    public Result<ServingStatusVO> status() {
        return Result.ok(buildStatus());
    }

    /**
     * 索引重建（内联模式）：全量重建商品索引并重置滞后基线（REQ-07 补偿手段）。
     * 远程模式下索引归搜推服务所有，返回明确提示不本地执行（避免双写歧义）。
     */
    @PostMapping("/resync")
    public Result<Map<String, Object>> resync() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", facade.mode());
        if ("remote".equals(facade.mode())) {
            out.put("rebuilt", false);
            out.put("message", "远程模式下索引由独立搜推服务负责，请在其部署侧执行重建");
            return Result.ok(out);
        }
        IndexRebuildService.RebuildReport report = rebuildService.rebuildAll();
        lagTracker.recordSuccess(SyncLagTracker.ENTITY_ITEM);
        out.put("rebuilt", true);
        out.put("report", report);
        return Result.ok(out);
    }

    /** 熔断器复位（运维故障恢复后立即闭合）。 */
    @PostMapping("/breaker/reset")
    public Result<Map<String, Object>> resetBreaker() {
        breaker.reset();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("breakerState", breaker.state().name());
        out.put("failures", breaker.failures());
        return Result.ok(out);
    }

    private ServingStatusVO buildStatus() {
        ServingStatusVO vo = new ServingStatusVO();
        vo.setMode(facade.mode());
        vo.setBreakerState(breaker.state().name());
        Map<String, Long> age = new LinkedHashMap<>();
        age.put(SyncLagTracker.ENTITY_ITEM, lagTracker.lagSeconds(SyncLagTracker.ENTITY_ITEM));
        age.put(SyncLagTracker.ENTITY_FEATURE, lagTracker.lagSeconds(SyncLagTracker.ENTITY_FEATURE));
        vo.setDataAgeSeconds(age);
        vo.setExecutor(executor.stats());
        vo.setTs(System.currentTimeMillis());
        return vo;
    }
}
