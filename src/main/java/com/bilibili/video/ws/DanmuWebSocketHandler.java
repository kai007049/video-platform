package com.bilibili.video.ws;

import com.bilibili.video.model.dto.DanmuDTO;
import com.bilibili.video.entity.User;
import com.bilibili.video.mapper.UserMapper;
import com.bilibili.video.service.DanmuService;
import com.bilibili.video.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹幕 WebSocket 处理器
 * 连接路径: /ws/danmu/{videoId}
 * 客户端连接后可发送弹幕 JSON，服务器广播给同视频房间用户
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DanmuWebSocketHandler extends TextWebSocketHandler {

    private final DanmuService danmuService;
    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    // videoId -> sessions
    private final Map<Long, Map<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long videoId = getVideoIdFromUri(session);
        if (videoId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        roomSessions.computeIfAbsent(videoId, k -> new ConcurrentHashMap<>()).put(session.getId(), session);
        log.debug("WebSocket connected: videoId={}, sessionId={}", videoId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long videoId = getVideoIdFromUri(session);
        if (videoId == null) {
            return;
        }

        try {
            DanmuDTO dto = objectMapper.readValue(message.getPayload(), DanmuDTO.class);
            dto.setVideoId(videoId);

            Long userId = getUserIdFromSession(session);
            if (userId == null) {
                sendError(session, "未登录");
                return;
            }
            dto.setUserId(userId);

            if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
                sendError(session, "弹幕内容不能为空");
                return;
            }
            if (dto.getContent().length() > 256) {
                sendError(session, "弹幕内容过长");
                return;
            }
            if (dto.getTimePoint() == null) {
                dto.setTimePoint(0);
            }

            User user = userMapper.selectById(userId);
            if (user != null) {
                dto.setUsername(user.getUsername());
            }
            danmuService.saveDanmu(dto);
            broadcastToRoom(videoId, dto, session.getId());
        } catch (Exception e) {
            log.warn("Handle danmu message error: {}", e.getMessage());
            sendError(session, "消息格式错误");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long videoId = getVideoIdFromUri(session);
        if (videoId != null) {
            Map<String, WebSocketSession> room = roomSessions.get(videoId);
            if (room != null) {
                room.remove(session.getId());
                if (room.isEmpty()) {
                    roomSessions.remove(videoId);
                }
            }
        }
    }

    private Long getVideoIdFromUri(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("danmu".equals(parts[i]) && i + 1 < parts.length) {
                try {
                    return Long.parseLong(parts[i + 1]);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private Long getUserIdFromSession(WebSocketSession session) {
        String token = session.getUri() != null ? session.getUri().getQuery() : null;
        if (token != null && token.startsWith("token=")) {
            token = token.substring(6);
        }
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            return jwtUtils.getUserIdFromToken(token);
        } catch (Exception e) {
            return null;
        }
    }

    private void broadcastToRoom(Long videoId, DanmuDTO dto, String excludeSessionId) {
        Map<String, WebSocketSession> room = roomSessions.get(videoId);
        if (room == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(dto);
            TextMessage msg = new TextMessage(json);
            for (Map.Entry<String, WebSocketSession> entry : room.entrySet()) {
                if (!entry.getKey().equals(excludeSessionId) && entry.getValue().isOpen()) {
                    try {
                        entry.getValue().sendMessage(msg);
                    } catch (IOException e) {
                        log.warn("Send danmu failed: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Broadcast danmu failed: {}", e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage("{\"error\":\"" + message + "\"}"));
            }
        } catch (IOException e) {
            log.warn("Send error failed: {}", e.getMessage());
        }
    }
}
