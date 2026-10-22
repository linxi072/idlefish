package com.idlefish.trade.search.serving;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.item.vo.ItemVO;
import com.idlefish.trade.search.serving.dto.ItemSyncRequest;
import com.idlefish.trade.search.serving.dto.ServingStatusVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 远程 HTTP 客户端单测（ITER-T04 / REQ-01/06/09）：成功解析 / 业务码非 0 / HTTP 401 /
 * 通用异常 / 服务令牌请求头 / 未配置基址快速失败。
 */
class HttpSearchRecommendGatewayTest {

    private RestTemplate restTemplate;
    private IdlefishProperties props;
    private HttpSearchRecommendGateway gateway;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        props = new IdlefishProperties();
        IdlefishProperties.SearchService cfg = new IdlefishProperties.SearchService();
        cfg.setMode(IdlefishProperties.Mode.REMOTE);
        cfg.setBaseUrl("http://sr.test");
        cfg.setServiceToken("tk-123");
        cfg.setTimeoutMs(1000L);
        cfg.setConnectTimeoutMs(300L);
        props.setSearchService(cfg);
        // 包级构造器注入 Mock（生产走公开构造器自建专用实例）
        gateway = new HttpSearchRecommendGateway(props, new ObjectMapper(), restTemplate);
    }

    private static ResponseEntity<String> ok(String dataJson) {
        return ResponseEntity.ok("{\"code\":0,\"msg\":\"success\",\"data\":" + dataJson + "}");
    }

    @Test
    @DisplayName("模式标识为 remote")
    void mode() {
        assertEquals("remote", gateway.mode());
    }

    @Test
    @DisplayName("增强检索成功：解析 Result.data 为分页，请求携带服务令牌")
    void searchSuccessWithToken() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok("{\"records\":[{\"id\":1,\"title\":\"手机\"}],\"total\":1,\"size\":20,\"current\":1,\"pages\":1}"));

        var page = gateway.search(new com.idlefish.trade.search.serving.dto.ItemQuery(), 1001L);
        assertEquals(1, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getRecords().get(0).getId());

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), captor.capture(), eq(String.class));
        assertEquals("tk-123", captor.getValue().getHeaders().getFirst("X-Service-Token"));
    }

    @Test
    @DisplayName("兜底召回成功：解析数组")
    void guessSuccess() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok("[{\"id\":1},{\"id\":2}]"));
        List<ItemVO> r = gateway.guess("手机", 10);
        assertEquals(2, r.size());
    }

    @Test
    @DisplayName("推荐流成功：解析分页")
    void feedSuccess() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok("{\"records\":[],\"total\":0,\"size\":10,\"current\":1,\"pages\":0}"));
        var page = gateway.feed("北京", 1001L, 1, 10);
        assertEquals(0, page.getTotal());
    }

    @Test
    @DisplayName("行为回传成功：POST 空响应体也接受")
    void behaviorSuccess() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok("true"));
        gateway.recordBehavior(1001L, "view_item", "88");
        verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("同步推送：POST /sr/api/sync/item")
    void pushSyncSuccess() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok("true"));
        gateway.pushSync(new ItemSyncRequest(88L, "UPSERT", System.currentTimeMillis()));
        verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("状态拉取：解析 ServingStatusVO")
    void statusSuccess() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok("{\"mode\":\"inline\",\"dataAgeSeconds\":{\"item\":3}}"));
        ServingStatusVO vo = gateway.status();
        assertEquals("inline", vo.getMode());
        assertEquals(3L, vo.getDataAgeSeconds().get("item"));
    }

    @Test
    @DisplayName("业务码非 0：抛 remote_biz_error")
    void bizError() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"code\":50001,\"msg\":\"boom\"}"));
        SearchServingException e = assertThrows(SearchServingException.class,
                () -> gateway.search(new com.idlefish.trade.search.serving.dto.ItemQuery(), null));
        assertEquals("remote_biz_error", e.getReason());
    }

    @Test
    @DisplayName("HTTP 401（服务令牌拒绝）：抛 remote_error，上层据此降级")
    void http401() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
        SearchServingException e = assertThrows(SearchServingException.class,
                () -> gateway.search(new com.idlefish.trade.search.serving.dto.ItemQuery(), null));
        assertEquals("remote_error", e.getReason());
    }

    @Test
    @DisplayName("网络异常（超时等）：抛 remote_timeout")
    void networkError() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("connect timed out"));
        SearchServingException e = assertThrows(SearchServingException.class,
                () -> gateway.search(new com.idlefish.trade.search.serving.dto.ItemQuery(), null));
        assertEquals("remote_timeout", e.getReason());
    }

    @Test
    @DisplayName("未配置 base-url：快速失败 remote_unconfigured")
    void unconfigured() {
        props.getSearchService().setBaseUrl("");
        SearchServingException e = assertThrows(SearchServingException.class,
                () -> gateway.search(new com.idlefish.trade.search.serving.dto.ItemQuery(), null));
        assertEquals("remote_unconfigured", e.getReason());
        assertTrue(e.getMessage().contains("base-url"));
    }
}
