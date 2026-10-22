package com.idlefish.trade.search.serving;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.TraceContext;
import com.idlefish.trade.common.observability.TraceRestTemplateInterceptor;
import com.idlefish.trade.item.vo.ItemVO;
import com.idlefish.trade.search.serving.dto.BehaviorReport;
import com.idlefish.trade.search.serving.dto.ItemPage;
import com.idlefish.trade.search.serving.dto.ItemQuery;
import com.idlefish.trade.search.serving.dto.ItemSyncRequest;
import com.idlefish.trade.search.serving.dto.ServingStatusVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 远程模式 HTTP 客户端（ITER-T04 / REQ-01/06/09）：以「服务令牌 + 超时」调用独立部署的搜推服务
 * （{@code /sr/api/**}，同一份代码不同配置的部署单元）。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>专用 {@link RestTemplate}：连接/读取超时独立可配（不共用全局 5s/10s 实例），
 *       并挂载 {@link TraceRestTemplateInterceptor} 透传 traceId（REQ-04 全链路串联）；</li>
 *   <li>服务令牌（{@code X-Service-Token}）：REQ-09 服务间鉴权，未配置时服务端 fail-closed 拒绝，
 *       客户端调用失败走上层降级，绝不明文裸奔；</li>
 *   <li>所有异常（超时 / 非 2xx / 业务码非 0 / 解析失败）统一包装 {@link SearchServingException}；</li>
 *   <li>仅解析 {@code Result.data} 载荷，分页以 {@link ItemPage} 传输，避免跨进程传递框架类型。</li>
 * </ul>
 */
@Component
public class HttpSearchRecommendGateway implements SearchRecommendGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpSearchRecommendGateway.class);

    private static final String TOKEN_HEADER = "X-Service-Token";

    private final IdlefishProperties props;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public HttpSearchRecommendGateway(IdlefishProperties props, ObjectMapper objectMapper) {
        this(props, objectMapper, buildRestTemplate(props));
    }

    /** 包级构造器：供单测注入 Mock {@link RestTemplate}（生产走上一构造器自建专用实例）。 */
    HttpSearchRecommendGateway(IdlefishProperties props, ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    /** 专用 RestTemplate：连接/读取超时独立可配，并透传 traceId。 */
    private static RestTemplate buildRestTemplate(IdlefishProperties props) {
        IdlefishProperties.SearchService cfg = props.getSearchService();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(100L, cfg.getConnectTimeoutMs())));
        factory.setReadTimeout(Duration.ofMillis(Math.max(200L, cfg.getTimeoutMs())));
        RestTemplate rt = new RestTemplate(factory);
        rt.setInterceptors(List.of(new TraceRestTemplateInterceptor()));
        return rt;
    }

    @Override
    public String mode() {
        return "remote";
    }

    /** 远程基址（如 http://search-svc.internal:8080），未配置视为不可用。 */
    private String base() {
        String baseUrl = props.getSearchService().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new SearchServingException("remote_unconfigured",
                    "远程模式未配置 idlefish.searchService.base-url，无法调用搜推服务");
        }
        return baseUrl.trim().replaceAll("/+$", "");
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String token = props.getSearchService().getServiceToken();
        if (token != null && !token.isBlank()) {
            h.set(TOKEN_HEADER, token);
        }
        String traceId = TraceContext.current();
        if (traceId != null && !traceId.isBlank()) {
            h.set(TraceContext.HEADER, traceId);
        }
        return h;
    }

    @Override
    public IPage<ItemVO> search(ItemQuery q, Long userId) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(base() + "/sr/api/search/query")
                .queryParam("page", q.getPage() == null ? 1 : q.getPage())
                .queryParam("size", q.getSize() == null ? 20 : q.getSize());
        if (q.getKeyword() != null) b.queryParam("keyword", q.getKeyword());
        if (q.getCategoryId() != null) b.queryParam("categoryId", q.getCategoryId());
        if (q.getCity() != null) b.queryParam("city", q.getCity());
        if (q.getProvince() != null) b.queryParam("province", q.getProvince());
        if (q.getMinPrice() != null) b.queryParam("minPrice", q.getMinPrice());
        if (q.getMaxPrice() != null) b.queryParam("maxPrice", q.getMaxPrice());
        if (q.getConditionLevel() != null) b.queryParam("conditionLevel", q.getConditionLevel());
        if (q.getLocalOnly() != null) b.queryParam("localOnly", q.getLocalOnly());
        if (q.getSort() != null) b.queryParam("sort", q.getSort());
        if (userId != null) b.queryParam("userId", userId);
        JsonNode data = getData(b.build().toUriString(), "search");
        ItemPage page = convert(data, ItemPage.class);
        return page == null ? emptyPage(q) : page.toPage();
    }

    @Override
    public List<ItemVO> guess(String keyword, int limit) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(base() + "/sr/api/search/guess")
                .queryParam("limit", limit);
        if (keyword != null && !keyword.isBlank()) {
            b.queryParam("keyword", keyword);
        }
        JsonNode data = getData(b.build().toUriString(), "guess");
        if (data == null || !data.isArray()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(data.toString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ItemVO.class));
        } catch (Exception e) {
            throw new SearchServingException("remote_parse_error", "远程兜底结果解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public IPage<ItemVO> feed(String city, Long userId, int page, int size) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(base() + "/sr/api/recommend/feed")
                .queryParam("page", page)
                .queryParam("size", size);
        if (city != null) b.queryParam("city", city);
        if (userId != null) b.queryParam("userId", userId);
        JsonNode data = getData(b.build().toUriString(), "feed");
        ItemPage p = convert(data, ItemPage.class);
        return p == null ? emptyFeed(page, size) : p.toPage();
    }

    @Override
    public void recordBehavior(Long userId, String event, String bizId) {
        String url = base() + "/sr/api/behavior";
        try {
            String body = objectMapper.writeValueAsString(new BehaviorReport(userId, event, bizId));
            ResponseEntity<String> resp = restTemplate.postForEntity(url, new HttpEntity<>(body, headers()), String.class);
            checkOk(resp.getBody(), "behavior");
        } catch (SearchServingException e) {
            throw e;
        } catch (Exception e) {
            throw new SearchServingException("remote_error", "远程行为回传失败: " + e.getMessage(), e);
        }
    }

    /** 同步事件推送（消费侧回调 / 补偿；与 {@link ItemSyncRequest} 对应）。 */
    public void pushSync(ItemSyncRequest req) {
        String url = base() + "/sr/api/sync/item";
        try {
            String body = objectMapper.writeValueAsString(req);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, new HttpEntity<>(body, headers()), String.class);
            checkOk(resp.getBody(), "sync/item");
        } catch (SearchServingException e) {
            throw e;
        } catch (Exception e) {
            throw new SearchServingException("remote_error", "远程索引同步失败: " + e.getMessage(), e);
        }
    }

    /** 拉取远程服务状态（滞后刷新 / 探活）。 */
    public ServingStatusVO status() {
        JsonNode data = getData(base() + "/sr/api/status", "status");
        try {
            return objectMapper.treeToValue(data, ServingStatusVO.class);
        } catch (Exception e) {
            throw new SearchServingException("remote_parse_error", "远程状态解析失败: " + e.getMessage(), e);
        }
    }

    // ===== 内部工具 =====

    private JsonNode getData(String url, String action) {
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers()), String.class);
            return checkOk(resp.getBody(), action);
        } catch (SearchServingException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            // 服务令牌缺失/错误时服务端 fail-closed 返回 401：属预期拒绝，走降级
            throw new SearchServingException("remote_error",
                    "远程 " + action + " HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new SearchServingException("remote_timeout", "远程 " + action + " 调用失败: " + e.getMessage(), e);
        }
    }

    /** 校验 Result 包装：code==0 返回 data 节点，否则抛异常。 */
    private JsonNode checkOk(String body, String action) {
        if (body == null || body.isBlank()) {
            throw new SearchServingException("remote_error", "远程 " + action + " 响应为空");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new SearchServingException("remote_parse_error", "远程 " + action + " 响应解析失败: " + e.getMessage(), e);
        }
        int code = root.path("code").asInt(-1);
        if (code != 0) {
            throw new SearchServingException("remote_biz_error",
                    "远程 " + action + " 业务码非 0: " + root.path("msg").asText("unknown"));
        }
        return root.get("data");
    }

    private <T> T convert(JsonNode data, Class<T> type) {
        if (data == null || data.isNull()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(data, type);
        } catch (Exception e) {
            throw new SearchServingException("remote_parse_error", "远程响应转换失败: " + e.getMessage(), e);
        }
    }

    private com.baomidou.mybatisplus.extension.plugins.pagination.Page<ItemVO> emptyPage(ItemQuery q) {
        return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                q.getPage() == null ? 1 : q.getPage(), q.getSize() == null ? 20 : q.getSize(), 0);
    }

    private com.baomidou.mybatisplus.extension.plugins.pagination.Page<ItemVO> emptyFeed(int page, int size) {
        return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size, 0);
    }
}
