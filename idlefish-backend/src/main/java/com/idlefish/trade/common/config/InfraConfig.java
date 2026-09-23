package com.idlefish.trade.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 基础设施 Bean：对外 HTTP 调用统一 RestTemplate（超时受控，避免雪崩）。
 * 真实对接（ES / OSS / 微信支付 / 物流 / 内容安全 / RocketMQ）均复用此实例。
 */
@Configuration
public class InfraConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }
}
