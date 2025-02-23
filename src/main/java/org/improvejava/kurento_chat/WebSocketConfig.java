package org.improvejava.kurento_chat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final CallHandler callHandler;

    @Autowired
    public WebSocketConfig(CallHandler callHandler) {
        this.callHandler = callHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(callHandler, "")
                .setAllowedOrigins("https://localhost:3000", "http://localhost:3000");  // 프론트엔드 도메인 허용
    }
}

