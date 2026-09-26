package com.idlefish.trade.common.observability;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * 出站 HTTP 透传拦截器（F-12.4）：在对外调用（微信支付 / OSS / ES / RocketMQ / 物流）时，
 * 将当前 traceId 写入 {@code X-Trace-Id} 请求头，使下游系统可串联同一条链路。
 * 挂载于 {@code InfraConfig} 的全局 {@code RestTemplate} Bean，对所有出站请求生效。
 */
public class TraceRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                       ClientHttpRequestExecution execution) throws IOException {
        String traceId = TraceContext.ensure();
        request.getHeaders().set(TraceContext.HEADER, traceId);
        return execution.execute(request, body);
    }
}
