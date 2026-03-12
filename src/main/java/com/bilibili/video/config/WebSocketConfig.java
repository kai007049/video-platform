package com.bilibili.video.config;

import com.bilibili.video.ws.MessageWebSocketServer;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MessageWebSocketServer messageWebSocketServer;

    public WebSocketConfig(MessageWebSocketServer messageWebSocketServer) {
        this.messageWebSocketServer = messageWebSocketServer;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(messageWebSocketServer, "/ws/message")
                .setAllowedOrigins("*");
    }
}
