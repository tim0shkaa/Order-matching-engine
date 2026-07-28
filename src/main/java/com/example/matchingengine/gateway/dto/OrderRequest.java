package com.example.matchingengine.gateway.dto;

import com.example.matchingengine.domain.OrderType;
import com.example.matchingengine.domain.Side;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OrderRequest(
        @NotNull
        Side side,

        @NotNull
        OrderType orderType,

        BigDecimal price,

        @NotNull
        @Positive
        BigDecimal quantity
) {
        @AssertTrue(message = "price обязателен для LIMIT/IOC/FOK заявок")
        public boolean isPriceValid() {
                if (orderType == OrderType.MARKET) {
                        return true;
                }
                return price != null && price.signum() > 0;
        }
}
