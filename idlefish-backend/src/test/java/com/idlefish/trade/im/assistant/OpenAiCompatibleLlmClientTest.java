package com.idlefish.trade.im.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OpenAI 兼容 LLM 客户端单测（ITER-F06 / REQ-12）：未配置快速失败 / 成功解析 / 异常回落 null。
 */
class OpenAiCompatibleLlmClientTest {

    private RestTemplate restTemplate;
    private IdlefishProperties props;
    private OpenAiCompatibleLlmClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        props = new IdlefishProperties();
        IdlefishProperties.ImAssistant.Llm llm = new IdlefishProperties.ImAssistant.Llm();
        llm.setEnabled(true);
        llm.setBaseUrl("http://llm.test/v1");
        llm.setApiKey("key-123");
        llm.setModel("test-model");
        llm.setTimeoutMs(1000L);
        props.getImAssistant().setLlm(llm);
        // 包级构造器注入 Mock（生产走公开构造器自建专用实例）
        client = new OpenAiCompatibleLlmClient(props, new ObjectMapper(), restTemplate);
    }

    @Test
    @DisplayName("配置齐全时可用")
    void availableWhenConfigured() {
        assertTrue(client.isAvailable());
    }

    @Test
    @DisplayName("未启用或未配置时不可用（链路回落模板）")
    void unavailableWhenDisabled() {
        props.getImAssistant().getLlm().setEnabled(false);
        assertTrue(!client.isAvailable());
        props.getImAssistant().getLlm().setEnabled(true);
        props.getImAssistant().getLlm().setBaseUrl("");
        assertTrue(!client.isAvailable());
    }

    @Test
    @DisplayName("成功解析 choices[0].message.content")
    void parsesContent() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"choices\":[{\"message\":{\"content\":\"48小时内发货\"}}]}"));
        assertEquals("48小时内发货", client.chat("system", "user"));
    }

    @Test
    @DisplayName("超时/异常返回 null（上层回落模板，不阻塞 REQ-12）")
    void returnsNullOnFailure() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("timeout"));
        assertNull(client.chat("system", "user"));
    }

    @Test
    @DisplayName("非 2xx 返回 null")
    void returnsNullOnNon2xx() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));
        assertNull(client.chat("system", "user"));
    }

    @Test
    @DisplayName("空 content 返回 null")
    void returnsNullOnEmptyContent() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"choices\":[{\"message\":{\"content\":\"\"}}]}"));
        assertNull(client.chat("system", "user"));
    }
}
