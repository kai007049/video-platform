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
