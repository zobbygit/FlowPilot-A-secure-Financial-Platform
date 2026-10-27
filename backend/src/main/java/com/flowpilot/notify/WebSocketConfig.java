package com.flowpilot.notify;

import com.flowpilot.config.AppProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final LiveUpdateHandler handler;
    private final AppProperties props;

    public WebSocketConfig(LiveUpdateHandler handler, AppProperties props) {
        this.handler = handler;
        this.props = props;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Explicit origin allow-list (blocks cross-site WebSocket hijacking); no SockJS fallback, no wildcard.
        registry.addHandler(handler, "/ws").setAllowedOrigins(props.cors().allowedOrigins().toArray(String[]::new));
    }
}
