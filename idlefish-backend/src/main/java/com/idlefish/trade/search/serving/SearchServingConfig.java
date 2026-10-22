package com.idlefish.trade.search.serving;

import com.idlefish.trade.common.IdlefishProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 搜推服务化 Bean 装配（ITER-T04）：熔断器 / 隔离线程池 / 滞后跟踪器。
 * <p>
 * 全部零新增外部依赖，离线 {@code mvn -o} 可编译；线程池随容器优雅停机。
 */
@Configuration
public class SearchServingConfig {

    /** 熔断器（REQ-08）：阈值与持续时间取 {@code idlefish.searchService} 配置。 */
    @Bean
    public CircuitBreaker searchServingCircuitBreaker(IdlefishProperties props) {
        IdlefishProperties.SearchService cfg = props.getSearchService();
        return new CircuitBreaker(cfg.getFailureThreshold(), cfg.getOpenDurationMs());
    }

    /** 隔离线程池（REQ-09）：检索/推荐负载专属，容器销毁时 shutdown。 */
    @Bean(destroyMethod = "shutdown")
    public SearchServiceExecutor searchServiceExecutor(IdlefishProperties props) {
        IdlefishProperties.SearchService cfg = props.getSearchService();
        return new SearchServiceExecutor(cfg.getCorePoolSize(), cfg.getMaxPoolSize(),
                cfg.getQueueCapacity(), cfg.getTimeoutMs());
    }

    /** 索引同步滞后跟踪器（REQ-07）。 */
    @Bean
    public SyncLagTracker syncLagTracker() {
        return new SyncLagTracker();
    }
}
