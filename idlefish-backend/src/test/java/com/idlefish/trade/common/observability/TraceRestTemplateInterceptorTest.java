package com.idlefish.trade.common.observability;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TraceRestTemplateInterceptorTest {

    @Test
    void intercept_adds_trace_header_and_forwards() throws IOException {
        HttpRequest request = mock(HttpRequest.class);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);

        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(execution.execute(any(), any())).thenReturn(response);

        new TraceRestTemplateInterceptor().intercept(request, new byte[0], execution);

        assertNotNull(headers.getFirst(TraceContext.HEADER));
        assertEquals(1, headers.get(TraceContext.HEADER).size());
        verify(execution).execute(request, new byte[0]);
    }
}
