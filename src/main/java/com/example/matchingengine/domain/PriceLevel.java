package com.example.matchingengine.domain;

import java.math.BigDecimal;

public record PriceLevel(BigDecimal price, BigDecimal totalQuantity) {
}
