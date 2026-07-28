package com.example.matchingengine.gateway.mapper;

import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.OrderStatus;
import com.example.matchingengine.domain.Trade;
import com.example.matchingengine.engine.MatchingResult;
import com.example.matchingengine.gateway.dto.OrderRequest;
import com.example.matchingengine.gateway.dto.OrderResponse;
import com.example.matchingengine.gateway.dto.TradeResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class OrderMapper {

    public OrderResponse resultToResponseMapper(MatchingResult result) {

        return new OrderResponse(
                result.resultingOrder().id(),
                result.resultingOrder().remainingQuantity(),
                result.resultingOrder().orderStatus(),
                this.tradesMapper(result.trades())
        );
    }

    public Order requestToOrderMapper(OrderRequest request) {

        return new Order(
                UUID.randomUUID(),
                "BTC-USD",
                request.side(),
                request.orderType(),
                request.price(),
                request.quantity(),
                request.quantity(),
                OrderStatus.NEW
        );
    }

    private List<TradeResponse> tradesMapper(List<Trade> trades) {

        List<TradeResponse> tradeResponses = new ArrayList<>();
        for (Trade trade : trades) {
            tradeResponses.add(new TradeResponse(
                    trade.id(),
                    trade.instrumentId(),
                    trade.buyOrderId(),
                    trade.sellOrderId(),
                    trade.price(),
                    trade.quantity(),
                    trade.timestamp()
            ));
        }
        return tradeResponses;
    }
}
