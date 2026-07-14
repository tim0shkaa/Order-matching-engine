package com.example.matchingengine.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record Order(
        UUID id,
        String instrumentId,
        Side side,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal remainingQuantity,
        OrderStatus orderStatus
) {
}
