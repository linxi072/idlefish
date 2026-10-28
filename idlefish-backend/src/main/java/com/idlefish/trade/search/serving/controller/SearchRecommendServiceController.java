package com.idlefish.trade.search.serving.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.item.dto.ItemQueryDTO;
import com.idlefish.trade.item.vo.ItemVO;
import com.idlefish.trade.search.serving.CircuitBreaker;
import com.idlefish.trade.search.serving.InlineSearchRecommendGateway;
import com.idlefish.trade.search.serving.SearchServiceExecutor;
import com.idlefish.trade.search.serving.SearchSyncService;
import com.idlefish.trade.search.serving.SyncLagTracker;
import com.idlefish.trade.search.serving.dto.BehaviorReport;
import com.idlefish.trade.search.serving.dto.ItemSyncRequest;
import com.idlefish.trade.search.serving.dto.ServingStatusVO;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜推独立服务接口（ITER-T04 / REQ-01/06/09）：独立部署单元的服务面（{@code /sr/api/**}）。
 * <p>
 * 定位说明：
 * <ul>
 *   <li>本控制器即「搜推服务」的 API 契约面（{@code search-recommend.json} 纳入 T03 门禁，REQ-06）；</li>
 *   <li>执行业内联管线（{@link InlineSearchRecommendGateway}），<b>绝不</b>回连远程网关，
 *       结构性杜绝自调用环路；</li>
 *   <li>服务间鉴权（REQ-09）：{@code X-Service-Token} 与 {@code idlefish.searchService.serviceToken}
 *       比对；未配置令牌时 fail-closed 拒绝（安全默认），生产须经 {@code IDLEFISH_SEARCH_SERVICE_TOKEN} 注入；</li>
 *   <li>业务侧（{@code mode=remote}）由 {@code HttpSearchRecommendGateway} 调用本面，
 *       失败走降级阶梯（{@code SearchServingFacade}）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/sr/api")
public class SearchRecommendServiceController {

    private static final String TOKEN_HEADER = "X-Service-Token";

    private final InlineSearchRecommendGateway gateway;
    private final SearchSyncService syncService;
    private final SyncLagTracker lagTracker;
    private final CircuitBreaker breaker;
    private final SearchServiceExecutor executor;
    private final IdlefishProperties props;

    public SearchRecommendServiceController(InlineSearchRecommendGateway gateway,
                                            SearchSyncService syncService,
                                            SyncLagTracker lagTracker,
                                            CircuitBreaker breaker,
                                            SearchServiceExecutor executor,
                                            IdlefishProperties props) {
        this.gateway = gateway;
        this.syncService = syncService;
        this.lagTracker = lagTracker;
        this.breaker = breaker;
        this.executor = executor;
        this.props = props;
    }

    /**
     * 增强检索（屏蔽词/同义词融合/实时加权/AB/兜底一体）。
     *
     * @param q      检索条件
     * @param userId 用户 ID（可空）
     */
    @GetMapping("/search/query")
    public Result<IPage<ItemVO>> search(ItemQueryDTO q,
                                        @RequestParam(required = false) Long userId,
                                        HttpServletRequest request) {
        authorize(request);
        return Result.ok(gateway.search(
                com.idlefish.trade.search.serving.dto.ItemQuery.from(q), userId));
    }

    /** 零结果兜底「猜你想找」。 */
    @GetMapping("/search/guess")
    public Result<List<ItemVO>> guess(@RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "10") int limit,
                                      HttpServletRequest request) {
        authorize(request);
        return Result.ok(gateway.guess(keyword, limit));
    }

    /** 首页推荐流（同城优先 + 热度衰减 + 行为加权 + 冷启动保量）。 */
    @GetMapping("/recommend/feed")
    public Result<IPage<ItemVO>> feed(@RequestParam(required = false) String city,
                                      @RequestParam(required = false) Long userId,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size,
                                      HttpServletRequest request) {
        authorize(request);
        return Result.ok(gateway.feed(city, userId, page, size));
    }

    /** 实时行为回传（60s 窗口实时兴趣，反哺搜索与推荐）。 */
    @PostMapping("/behavior")
    public Result<Boolean> behavior(@Valid @RequestBody BehaviorReport report, HttpServletRequest request) {
        authorize(request);
        gateway.recordBehavior(report.getUserId(), report.getEvent(), report.getBizId());
        return Result.ok(true);
    }

    /**
     * 索引/特征同步应用（REQ-02 消费侧回调 / 补偿推送）：更新本地索引并按事件时间刷新滞后口径。
     * 消息重复投递时幂等（索引写入为覆盖语义）。
     */
    @PostMapping("/sync/item")
    public Result<Boolean> syncItem(@Valid @RequestBody ItemSyncRequest req, HttpServletRequest request) {
        authorize(request);
        syncService.applySyncedItem(req);
        return Result.ok(true);
    }

    /** 服务状态（REQ-04/07/08）：模式 + 熔断器 + 数据时效 + 隔离池快照。 */
    @GetMapping("/status")
    public Result<ServingStatusVO> status(HttpServletRequest request) {
        authorize(request);
        return Result.ok(buildStatus());
    }

    // ===== 内部 =====

    /**
     * 服务间令牌校验（REQ-09，fail-closed）：
     * 配置了令牌必须匹配；未配置令牌一律拒绝（杜绝忘配导致裸奔）。
     */
    private void authorize(HttpServletRequest request) {
        String expected = props.getSearchService().getServiceToken();
        if (expected == null || expected.isBlank()) {
            throw new BizException(Code.UNAUTHORIZED,
                    "搜推服务令牌未配置（idlefish.searchService.serviceToken），拒绝服务间调用");
        }
        String actual = request.getHeader(TOKEN_HEADER);
        if (!expected.equals(actual)) {
            throw new BizException(Code.UNAUTHORIZED, "服务令牌不匹配，拒绝服务间调用");
        }
    }

    private ServingStatusVO buildStatus() {
        ServingStatusVO vo = new ServingStatusVO();
        vo.setMode("inline");
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
