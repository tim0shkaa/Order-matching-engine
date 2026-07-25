package com.example.matchingengine.gateway.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeResponse(
        UUID id,
        String instrumentId,
        UUID buyOrderId,
        UUID sellOrderId,
        BigDecimal price,
        BigDecimal quantity,
        Instant timestamp
) {
}
