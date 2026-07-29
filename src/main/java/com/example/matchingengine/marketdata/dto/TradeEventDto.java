package com.example.matchingengine.marketdata.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeEventDto(
        BigDecimal price,
        BigDecimal quantity,
        Instant timestamp
) {
}