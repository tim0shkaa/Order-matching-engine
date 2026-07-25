package com.example.matchingengine.gateway.dto;

import com.example.matchingengine.domain.Side;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OrderRequest(
        @NotNull
        Side side,

        @NotNull
        @Positive
        BigDecimal price,

        @NotNull
        @Positive
        BigDecimal quantity
) {
}
