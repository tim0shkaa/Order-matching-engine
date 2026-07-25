package com.example.matchingengine.gateway.dto;

import com.example.matchingengine.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        BigDecimal remainingQuantity,
        OrderStatus orderStatus,
        List<TradeResponse> trades
) {
}
