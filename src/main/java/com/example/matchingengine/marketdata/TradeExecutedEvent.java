package com.example.matchingengine.marketdata;

import com.example.matchingengine.domain.Trade;

import java.util.List;

public record TradeExecutedEvent(List<Trade> trades) {
}