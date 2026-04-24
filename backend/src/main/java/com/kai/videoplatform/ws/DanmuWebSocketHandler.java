package com.kai.videoplatform.ws;

import com.kai.videoplatform.entity.User;
import com.kai.videoplatform.mapper.UserMapper;
import com.kai.videoplatform.model.dto.DanmuDTO;
import com.kai.videoplatform.service.DanmuService;
import com.kai.videoplatform.utils.JwtUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 弹幕 WebSocket 处理器。
 * 连接路径: /ws/danmu/{videoId}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DanmuWebSocketHandler extends TextWebSocketHandler {

    private static final String RATE_LIMIT_KEY_PREFIX = "danmu:rate:";
    private static final String DUPLICATE_KEY_PREFIX = "danmu:dup:";
    private static final int MAX_MESSAGES_PER_SECOND = 3;
    private static final long DUPLICATE_WINDOW_SECONDS = 2L;

    private final DanmuService danmuService;
    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private final Map<Long, Map<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long videoId = getVideoIdFromUri(session);
        if (videoId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        roomSessions.computeIfAbsent(videoId, key -> new ConcurrentHashMap<>()).put(session.getId(), session);
        log.debug("Danmu websocket connected: videoId={}, sessionId={}", videoId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long videoId = getVideoIdFromUri(session);
        if (videoId == null) {
            sendError(session, "视频房间不存在");
            return;
        }

        try {
            DanmuDTO dto = objectMapper.readValue(message.getPayload(), DanmuDTO.class);
            Long userId = getUserIdFromSession(session);
            if (userId == null) {
                sendError(session, "未登录或登录已失效");
                return;
            }

            dto.setVideoId(videoId);
            dto.setUserId(userId);
            dto.setContent(dto.getContent() == null ? null : dto.getContent().trim());
            if (dto.getContent() == null || dto.getContent().isEmpty()) {
                sendError(session, "弹幕内容不能为空");
                return;
            }
            if (dto.getContent().length() > 256) {
                sendError(session, "弹幕内容过长");
                return;
            }
            if (dto.getTimePoint() == null || dto.getTimePoint() < 0) {
                dto.setTimePoint(0);
            }

            if (!allowSend(userId)) {
                sendError(session, "发送过于频繁，请稍后再试");
                return;
            }
            if (isDuplicateDanmu(userId, videoId, dto)) {
                sendError(session, "请勿短时间重复发送相同弹幕");
                return;
            }

            User user = userMapper.selectById(userId);
            if (user != null) {
                dto.setUsername(user.getUsername());
            }

            danmuService.saveDanmu(dto);
            broadcastToRoom(videoId, dto, session.getId());
        } catch (JsonProcessingException e) {
            log.warn("Danmu payload parse failed: {}", e.getMessage());
            sendError(session, "弹幕消息格式错误");
        } catch (Exception e) {
            log.warn("Handle danmu message error", e);
            sendError(session, "弹幕发送失败，请稍后重试");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long videoId = getVideoIdFromUri(session);
        if (videoId == null) {
            return;
        }
        Map<String, WebSocketSession> room = roomSessions.get(videoId);
        if (room == null) {
            return;
        }
        room.remove(session.getId());
        if (room.isEmpty()) {
            roomSessions.remove(videoId);
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
            TextMessage textMessage = new TextMessage(json);
            for (Map.Entry<String, WebSocketSession> entry : room.entrySet()) {
                if (entry.getKey().equals(excludeSessionId)) {
                    continue;
                }
                WebSocketSession target = entry.getValue();
                if (!target.isOpen()) {
                    continue;
                }
                try {
                    target.sendMessage(textMessage);
                } catch (IOException e) {
                    log.warn("Send danmu failed: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Broadcast danmu failed", e);
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

    private boolean allowSend(Long userId) {
        String key = RATE_LIMIT_KEY_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, 1, TimeUnit.SECONDS);
        }
        return count == null || count <= MAX_MESSAGES_PER_SECOND;
    }

    private boolean isDuplicateDanmu(Long userId, Long videoId, DanmuDTO dto) {
        String payload = dto.getContent() + "|" + dto.getTimePoint();
        String key = DUPLICATE_KEY_PREFIX + userId + ":" + videoId + ":" + payload.hashCode();
        Boolean accepted = redisTemplate.opsForValue().setIfAbsent(
                key,
                dto.getClientMessageId() == null ? "1" : dto.getClientMessageId(),
                DUPLICATE_WINDOW_SECONDS,
                TimeUnit.SECONDS
        );
        return !Boolean.TRUE.equals(accepted);
    }
}