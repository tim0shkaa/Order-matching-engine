package com.example.matchingengine.engine;

import com.example.matchingengine.domain.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MatchingEngine {

    private final OrderBook orderBook;

    public MatchingEngine(OrderBook orderBook) {
        this.orderBook = orderBook;
    }

    public MatchingResult submitOrder(Order order) {
        List<Trade> trades = new ArrayList<>();
        Side oppositeSide = order.side() == Side.BUY ? Side.SELL : Side.BUY;

        while (order.remainingQuantity().signum() > 0) {
            Optional<Order> restingOpt = oppositeSide == Side.SELL
                    ? orderBook.peekBestAskOrder()
                    : orderBook.peekBestBidOrder();

            if (restingOpt.isEmpty() || !pricesCross(order, restingOpt.get())) {
                break;
            }

            Order resting = restingOpt.get();

            var matchedQuantity = order.remainingQuantity().min(resting.remainingQuantity());

            Trade trade = new Trade(
                    UUID.randomUUID(),
                    order.instrumentId(),
                    order.side() == Side.BUY ? order.id() : resting.id(),
                    order.side() == Side.BUY ? resting.id() : order.id(),
                    resting.price(),
                    matchedQuantity,
                    Instant.now()
            );
            trades.add(trade);

            if (matchedQuantity.compareTo(resting.remainingQuantity()) == 0) {
                orderBook.pollFirstOrder(resting.price(), oppositeSide);
            } else {
                orderBook.reduceOrderQuantity(oppositeSide, resting.price(), matchedQuantity);
            }

            order = new Order(
                    order.id(),
                    order.instrumentId(),
                    order.side(),
                    order.price(),
                    order.quantity(),
                    order.remainingQuantity().subtract(matchedQuantity),
                    OrderStatus.PARTIALLY_FILLED
            );
        }

        order = finalizeStatus(order);

        if (order.remainingQuantity().signum() > 0) {
            orderBook.addOrder(order);
        }

        return new MatchingResult(trades, order);
    }

    private boolean pricesCross(Order incoming, Order resting) {
        return incoming.side() == Side.BUY
                ? incoming.price().compareTo(resting.price()) >= 0
                : incoming.price().compareTo(resting.price()) <= 0;
    }

    private Order finalizeStatus(Order order) {
        OrderStatus status = order.remainingQuantity().signum() == 0
                ? OrderStatus.FILLED
                : (order.remainingQuantity().compareTo(order.quantity()) == 0
                   ? OrderStatus.NEW
                   : OrderStatus.PARTIALLY_FILLED);

        return new Order(
                order.id(), order.instrumentId(), order.side(), order.price(),
                order.quantity(), order.remainingQuantity(), status
        );
    }
}