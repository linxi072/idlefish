package com.idlefish.trade.common.util;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 轻量 HTTP 工具：统一封装 JSON POST / GET，避免各真实对接实现重复样板。
 * 超时由 {@code InfraConfig} 中 RestTemplate Bean 统一控制。
 */
public final class HttpUtils {

    private HttpUtils() {
    }

    /** 发送 JSON POST，返回响应体字符串。 */
    public static String postJson(RestTemplate rt, String url, Map<String, String> headers, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (headers != null) {
            headers.forEach(h::set);
        }
        HttpEntity<String> req = new HttpEntity<>(body, h);
        ResponseEntity<String> resp = rt.exchange(url, HttpMethod.POST, req, String.class);
        return resp.getBody();
    }

    /** 发送 GET（带可选请求头），返回响应体字符串。 */
    public static String get(RestTemplate rt, String url, Map<String, String> headers) {
        HttpHeaders h = new HttpHeaders();
        if (headers != null) {
            headers.forEach(h::set);
        }
        HttpEntity<Void> req = new HttpEntity<>(h);
        ResponseEntity<String> resp = rt.exchange(url, HttpMethod.GET, req, String.class);
        return resp.getBody();
    }

    /** 发送表单 POST（application/x-www-form-urlencoded），返回响应体字符串。 */
    public static String postForm(RestTemplate rt, String url, Map<String, String> headers, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (headers != null) {
            headers.forEach(h::set);
        }
        HttpEntity<String> req = new HttpEntity<>(body, h);
        ResponseEntity<String> resp = rt.exchange(url, HttpMethod.POST, req, String.class);
        return resp.getBody();
    }
}
