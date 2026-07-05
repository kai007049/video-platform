package com.kai.videoplatform.config;

import com.kai.videoplatform.ws.DanmuWebSocketHandler;
import com.kai.videoplatform.ws.MessageWebSocketAuthInterceptor;
import com.kai.videoplatform.ws.MessageWebSocketServer;
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
    private final SecurityProperties securityProperties;

    public WebSocketConfig(MessageWebSocketServer messageWebSocketServer,
                           DanmuWebSocketHandler danmuWebSocketHandler,
                           MessageWebSocketAuthInterceptor messageWebSocketAuthInterceptor,
                           SecurityProperties securityProperties) {
        this.messageWebSocketServer = messageWebSocketServer;
        this.danmuWebSocketHandler = danmuWebSocketHandler;
        this.messageWebSocketAuthInterceptor = messageWebSocketAuthInterceptor;
        this.securityProperties = securityProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(messageWebSocketServer, "/ws/message")
                .addInterceptors(messageWebSocketAuthInterceptor)
                .setAllowedOrigins(securityProperties.normalizedAllowedWsOrigins());

        registry.addHandler(danmuWebSocketHandler, "/ws/danmu/{videoId}")
                .setAllowedOrigins(securityProperties.normalizedAllowedWsOrigins());
    }
}
