package com.example.matchingengine.config;

import com.example.matchingengine.marketdata.MarketDataWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MarketDataWebSocketHandler marketDataWebSocketHandler;

    public WebSocketConfig(MarketDataWebSocketHandler marketDataWebSocketHandler) {
        this.marketDataWebSocketHandler = marketDataWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(marketDataWebSocketHandler, "/ws/market-data").setAllowedOrigins("*");
    }
}