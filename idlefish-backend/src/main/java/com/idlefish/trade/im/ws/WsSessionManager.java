package com.idlefish.trade.im.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护 userId -> WebSocketSession 映射，用于向在线用户实时推送消息。
 * 支持多端（同一用户多个连接）。
 */
@Component
public class WsSessionManager {

    private final Map<Long, ConcurrentHashMap<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void add(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(session.getId(), session);
    }

    public void remove(Long userId, String sessionId) {
        ConcurrentHashMap<String, WebSocketSession> map = sessions.get(userId);
        if (map != null) {
            map.remove(sessionId);
            if (map.isEmpty()) {
                sessions.remove(userId);
            }
        }
    }

    public boolean isOnline(Long userId) {
        ConcurrentHashMap<String, WebSocketSession> map = sessions.get(userId);
        return map != null && !map.isEmpty();
    }

    /**
     * 向指定用户所有在线连接推送文本。
     * 顺带清理已关闭的失效会话（防止连接泄漏导致内存膨胀与重复推送失败）。
     * @return 实际投递成功的连接数（供监控 / 真机联调统计投递率）。
     */
    public int send(Long userId, String payload) {
        ConcurrentHashMap<String, WebSocketSession> map = sessions.get(userId);
        if (map == null) {
            return 0;
        }
        int delivered = 0;
        Iterator<Map.Entry<String, WebSocketSession>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, WebSocketSession> e = it.next();
            WebSocketSession s = e.getValue();
            if (!s.isOpen()) {
                it.remove(); // 失效会话清理
                continue;
            }
            try {
                s.sendMessage(new TextMessage(payload));
                delivered++;
            } catch (IOException ex) {
                it.remove(); // 推送失败的连接直接剔除，避免雪崩
            }
        }
        if (map.isEmpty()) {
            sessions.remove(userId);
        }
        return delivered;
    }
}
