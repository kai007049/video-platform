package com.bilibili.video.config;

import com.bilibili.video.websocket.DanmuWebSocketHandler;
import com.bilibili.video.ws.MessageWebSocketServer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MessageWebSocketServer messageWebSocketServer;
    private final DanmuWebSocketHandler danmuWebSocketHandler;

    public WebSocketConfig(MessageWebSocketServer messageWebSocketServer, DanmuWebSocketHandler danmuWebSocketHandler) {
        this.messageWebSocketServer = messageWebSocketServer;
        this.danmuWebSocketHandler = danmuWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(messageWebSocketServer, "/ws/message")
                .addHandler(danmuWebSocketHandler, "/ws/danmu/{videoId}")
                .setAllowedOrigins("*");
    }
}
