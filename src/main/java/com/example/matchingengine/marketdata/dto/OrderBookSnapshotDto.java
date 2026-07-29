package com.example.matchingengine.marketdata.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderBookSnapshotDto(
        String instrumentId,
        List<PriceLevelDto> bids,
        List<PriceLevelDto> asks
) {
    public record PriceLevelDto(BigDecimal price, BigDecimal totalQuantity) {
    }
}
