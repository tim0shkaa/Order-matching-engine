package com.example.matchingengine.config;

import com.example.matchingengine.domain.OrderBook;
import com.example.matchingengine.engine.EventPublisher;
import com.example.matchingengine.engine.MatchingEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {

    @Bean
    public OrderBook orderBook() {
        return new OrderBook("BTC-USD");
    }

    @Bean
    public MatchingEngine matchingEngine(OrderBook orderBook, EventPublisher eventPublisher) {
        return new MatchingEngine(orderBook, eventPublisher);
    }
}
