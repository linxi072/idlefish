package com.idlefish.trade.im.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册 IM 通道 /ws/im，并在握手阶段鉴权。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ImWebSocketHandler imWebSocketHandler;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;

    public WebSocketConfig(ImWebSocketHandler imWebSocketHandler, AuthHandshakeInterceptor authHandshakeInterceptor) {
        this.imWebSocketHandler = imWebSocketHandler;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(imWebSocketHandler, "/ws/im")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
