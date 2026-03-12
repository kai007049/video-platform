package com.bilibili.video.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MessageWebSocketServer extends TextWebSocketHandler {

    private static final Map<Long, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        if (userId != null) {
            SESSIONS.put(userId, session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserId(session);
        if (userId != null) {
            SESSIONS.remove(userId);
        }
    }

    public void push(Long userId, String payload) {
        WebSocketSession session = SESSIONS.get(userId);
        try {
            if (session != null && session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (Exception e) {
            log.error("push failed, userId={}", userId, e);
        }
    }

    private Long getUserId(WebSocketSession session) {
        try {
            String uid = session.getUri().getQuery();
            if (uid == null) return null;
            // query: userId=123
            String[] parts = uid.split("=");
            return parts.length == 2 ? Long.valueOf(parts[1]) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
