package com.idlefish.trade.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.idlefish.trade.common.observability.TraceRestTemplateInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

/**
 * 基础设施 Bean：对外 HTTP 调用统一 RestTemplate（超时受控，避免雪崩）。
 * 真实对接（ES / OSS / 微信支付 / 物流 / 内容安全 / RocketMQ）均复用此实例。
 * 挂载 {@link TraceRestTemplateInterceptor} 使所有出站请求携带 {@code X-Trace-Id}（F-12.4 全链路透传）。
 */
@Configuration
public class InfraConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setInterceptors(List.of(new TraceRestTemplateInterceptor()));
        return restTemplate;
    }
}
