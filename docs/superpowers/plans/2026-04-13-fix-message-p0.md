# Fix Message P0 Issues Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the two message-system P0 issues by enforcing authenticated WebSocket identity binding and completing the notification MQ-to-database flow, while recording the fixes in `backend/OPEN_SOURCE_FIX_LIST.md`.

**Architecture:** Add a dedicated WebSocket handshake interceptor that authenticates the user from token and stores the verified `userId` in session attributes; update the message WebSocket handler to trust only that server-side identity. Connect `NotifyConsumer` to `NotificationService` with a minimal message-to-notification mapping, then document both fixes in the open-source bug list and verify the backend still compiles.

**Tech Stack:** Spring Boot, Spring WebSocket, JWT, Redis, RocketMQ, MyBatis-Plus, Markdown

---

## File Map

- **Create:** `backend/src/main/java/com/bilibili/video/ws/MessageWebSocketAuthInterceptor.java` — authenticates `/ws/message` handshake and injects verified `userId`.
- **Modify:** `backend/src/main/java/com/bilibili/video/config/WebSocketConfig.java` — register the handshake interceptor for message WebSocket.
- **Modify:** `backend/src/main/java/com/bilibili/video/ws/MessageWebSocketServer.java` — read `userId` from session attributes only.
- **Modify:** `backend/src/main/java/com/bilibili/video/mq/NotifyConsumer.java` — persist notification events through `NotificationService`.
- **Modify:** `backend/OPEN_SOURCE_FIX_LIST.md` — append fix records under P0-1 and P0-2.
- **Reference:** `backend/src/main/java/com/bilibili/video/utils/JwtUtils.java`
- **Reference:** `backend/src/main/java/com/bilibili/video/utils/AuthInterceptor.java`
- **Reference:** `backend/src/main/java/com/bilibili/video/service/NotificationService.java`
- **Reference:** `backend/src/main/java/com/bilibili/video/service/impl/NotificationServiceImpl.java`
- **Verify:** `backend/pom.xml` via `mvn -q -DskipTests compile`

### Task 1: Secure `/ws/message` identity binding

**Files:**
- Create: `backend/src/main/java/com/bilibili/video/ws/MessageWebSocketAuthInterceptor.java`
- Modify: `backend/src/main/java/com/bilibili/video/config/WebSocketConfig.java`
- Modify: `backend/src/main/java/com/bilibili/video/ws/MessageWebSocketServer.java`
- Reference: `backend/src/main/java/com/bilibili/video/utils/JwtUtils.java`
- Reference: `backend/src/main/java/com/bilibili/video/utils/AuthInterceptor.java`

- [ ] **Step 1: Write the handshake interceptor class**

Create `backend/src/main/java/com/bilibili/video/ws/MessageWebSocketAuthInterceptor.java` with this implementation:

```java
package com.bilibili.video.ws;

import com.bilibili.video.common.Constants;
import com.bilibili.video.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MessageWebSocketAuthInterceptor implements HandshakeInterceptor {

    public static final String WS_USER_ID_ATTR = "wsUserId";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtils jwtUtils;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token) || !jwtUtils.validateToken(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Long userId = jwtUtils.getUserIdFromToken(token);
        Object redisToken = redisTemplate.opsForValue().get(Constants.loginTokenPrefix + ":" + userId);
        if (redisToken == null || !token.equals(redisToken)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(WS_USER_ID_ATTR, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private String resolveToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(TOKEN_PREFIX)) {
            return authHeader.substring(TOKEN_PREFIX.length());
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
            String token = httpServletRequest.getParameter("token");
            if (StringUtils.hasText(token)) {
                return URLDecoder.decode(token, StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
```

- [ ] **Step 2: Register the handshake interceptor for `/ws/message`**

Update `backend/src/main/java/com/bilibili/video/config/WebSocketConfig.java` to this form:

```java
package com.bilibili.video.config;

import com.bilibili.video.ws.DanmuWebSocketHandler;
import com.bilibili.video.ws.MessageWebSocketAuthInterceptor;
import com.bilibili.video.ws.MessageWebSocketServer;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MessageWebSocketServer messageWebSocketServer;
    private final DanmuWebSocketHandler danmuWebSocketHandler;
    private final MessageWebSocketAuthInterceptor messageWebSocketAuthInterceptor;

    public WebSocketConfig(MessageWebSocketServer messageWebSocketServer,
                           DanmuWebSocketHandler danmuWebSocketHandler,
                           MessageWebSocketAuthInterceptor messageWebSocketAuthInterceptor) {
        this.messageWebSocketServer = messageWebSocketServer;
        this.danmuWebSocketHandler = danmuWebSocketHandler;
        this.messageWebSocketAuthInterceptor = messageWebSocketAuthInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(messageWebSocketServer, "/ws/message")
                .addInterceptors(messageWebSocketAuthInterceptor)
                .setAllowedOrigins("*");

        registry.addHandler(danmuWebSocketHandler, "/ws/danmu/{videoId}")
                .setAllowedOrigins("*");
    }
}
```

- [ ] **Step 3: Make the WebSocket server trust only authenticated session attributes**

Replace `backend/src/main/java/com/bilibili/video/ws/MessageWebSocketServer.java` with:

```java
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
            SESSIONS.remove(userId, session);
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
        Object userId = session.getAttributes().get(MessageWebSocketAuthInterceptor.WS_USER_ID_ATTR);
        if (userId instanceof Long value) {
            return value;
        }
        if (userId instanceof Number value) {
            return value.longValue();
        }
        return null;
    }
}
```

- [ ] **Step 4: Run backend compile to verify the WebSocket security fix**

Run:

```bash
mvn -q -DskipTests -f "E:/Java-Project/Bilibili/Video-Platform/backend/pom.xml" compile
```

Expected:
- Command exits with code 0
- No unresolved symbol errors for `MessageWebSocketAuthInterceptor`
- No Spring WebSocket API compile errors

### Task 2: Close the notification event loop

**Files:**
- Modify: `backend/src/main/java/com/bilibili/video/mq/NotifyConsumer.java`
- Reference: `backend/src/main/java/com/bilibili/video/service/NotificationService.java`
- Reference: `backend/src/main/java/com/bilibili/video/service/impl/NotificationServiceImpl.java`
- Reference: `backend/src/main/java/com/bilibili/video/model/mq/NotifyMessage.java`

- [ ] **Step 1: Replace `NotifyConsumer` with real notification persistence logic**

Replace `backend/src/main/java/com/bilibili/video/mq/NotifyConsumer.java` with:

```java
package com.bilibili.video.mq;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.NotifyMessage;
import com.bilibili.video.service.NotificationService;
import com.bilibili.video.service.impl.MqReliabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.NOTIFY_EVENT,
        consumerGroup = "notify-event-consumer",
        maxReconsumeTimes = 5
)
public class NotifyConsumer implements RocketMQListener<NotifyMessage> {

    private final NotificationService notificationService;
    private final MqReliabilityService mqReliabilityService;

    @Override
    public void onMessage(NotifyMessage message) {
        mqReliabilityService.consumeWithIdempotency(MqTopics.NOTIFY_EVENT, message, () -> {
            if (message == null || message.getUserId() == null) {
                return;
            }
            String type = StringUtils.hasText(message.getType()) ? message.getType() : "notification";
            String content = buildContent(message);
            notificationService.sendNotification(message.getUserId(), type, content);
            log.info("[MQ] notification persisted: userId={}, type={}, targetId={}",
                    message.getUserId(), type, message.getTargetId());
        });
    }

    private String buildContent(NotifyMessage message) {
        String content = StringUtils.trimWhitespace(message.getContent());
        Long targetId = message.getTargetId();
        String suffix = targetId == null ? "" : "（对象ID=" + targetId + "）";
        if (StringUtils.hasText(content)) {
            return content + suffix;
        }
        return switch (message.getType()) {
            case "like" -> "你收到了新的点赞" + suffix;
            case "favorite" -> "你收到了新的收藏" + suffix;
            case "comment" -> "你收到了新的评论" + suffix;
            case "danmu" -> "你收到了新的弹幕" + suffix;
            default -> "你收到了新的通知" + suffix;
        };
    }
}
```

- [ ] **Step 2: Run backend compile to verify the notification fix**

Run:

```bash
mvn -q -DskipTests -f "E:/Java-Project/Bilibili/Video-Platform/backend/pom.xml" compile
```

Expected:
- Command exits with code 0
- No unresolved symbol errors for `NotificationService`
- `NotifyConsumer` compiles without switch or import issues

### Task 3: Record both fixes in `OPEN_SOURCE_FIX_LIST.md`

**Files:**
- Modify: `backend/OPEN_SOURCE_FIX_LIST.md`

- [ ] **Step 1: Add a fix record under P0-1**

Insert the following block after the existing `修复建议` list for `P0-1`:

```md
**修复记录（2026-04-13）**

- 修复状态：已修复
- 修复方式：新增 WebSocket 握手鉴权拦截器，握手阶段从 `Authorization` 头或 query 中的 `token` 解析登录态，并将认证后的 `userId` 写入 `WebSocketSession.attributes`。`MessageWebSocketServer` 不再读取 query 中的 `userId`，只信任服务端握手阶段写入的身份信息。
- 涉及文件：
  - `src/main/java/com/bilibili/video/ws/MessageWebSocketAuthInterceptor.java`
  - `src/main/java/com/bilibili/video/config/WebSocketConfig.java`
  - `src/main/java/com/bilibili/video/ws/MessageWebSocketServer.java`
- 验证建议：未携带合法 token 时应无法建立 `/ws/message` 连接；伪造 `userId` 也不应冒充他人接收消息。
```

- [ ] **Step 2: Add a fix record under P0-2**

Insert the following block after the existing `修复建议` list for `P0-2`:

```md
**修复记录（2026-04-13）**

- 修复状态：已修复
- 修复方式：`NotifyConsumer` 已接入 `NotificationService`，消费点赞、评论、收藏、弹幕等通知事件时会真正写入 `notification` 表、更新未读数，并通过既有通知推送链路发送到消息 WebSocket。
- 涉及文件：
  - `src/main/java/com/bilibili/video/mq/NotifyConsumer.java`
- 验证建议：触发点赞/评论/收藏/弹幕后，应能在通知列表中看到新通知，且未读数会同步变化。
```

- [ ] **Step 3: Read the updated markdown and verify both fix records exist**

Read: `E:/Java-Project/Bilibili/Video-Platform/backend/OPEN_SOURCE_FIX_LIST.md`

Expected:
- P0-1 section contains one `修复记录（2026-04-13）`
- P0-2 section contains one `修复记录（2026-04-13）`
- Existing problem description, impact, and suggestion text is still present above each fix record

### Task 4: Final verification of the whole change set

**Files:**
- Verify: `backend/src/main/java/com/bilibili/video/config/WebSocketConfig.java`
- Verify: `backend/src/main/java/com/bilibili/video/ws/MessageWebSocketServer.java`
- Verify: `backend/src/main/java/com/bilibili/video/ws/MessageWebSocketAuthInterceptor.java`
- Verify: `backend/src/main/java/com/bilibili/video/mq/NotifyConsumer.java`
- Verify: `backend/OPEN_SOURCE_FIX_LIST.md`

- [ ] **Step 1: Re-read the changed Java files and verify root-cause fixes are actually in place**

Check these exact conditions:

```text
- MessageWebSocketServer no longer parses query string for userId
- WebSocket identity comes from session attributes only
- WebSocketConfig registers a handshake interceptor for /ws/message
- NotifyConsumer calls NotificationService.sendNotification(...)
- OPEN_SOURCE_FIX_LIST.md contains repair notes for both P0 items
```

Expected result: all five conditions are true.

- [ ] **Step 2: Run one final backend compile**

Run:

```bash
mvn -q -DskipTests -f "E:/Java-Project/Bilibili/Video-Platform/backend/pom.xml" compile
```

Expected:
- Command exits with code 0
- No compile regressions introduced by the four changed Java files
