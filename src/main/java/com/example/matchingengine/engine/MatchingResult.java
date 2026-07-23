package com.example.matchingengine.engine;

import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.Trade;

import java.util.List;

public record MatchingResult(
        List<Trade> trades,
        Order resultingOrder
) {
}
