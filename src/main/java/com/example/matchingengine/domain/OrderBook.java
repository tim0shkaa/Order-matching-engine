package com.example.matchingengine.domain;

import java.math.BigDecimal;
import java.util.*;

public class OrderBook {
    private final String instrumentId;
    private final TreeMap<BigDecimal, Deque<Order>> bids;
    private final TreeMap<BigDecimal, Deque<Order>> asks;
    private final Map<UUID, Order> ordersById;

    public OrderBook(String instrumentId) {
        this.instrumentId = instrumentId;
        bids = new TreeMap<>(Comparator.reverseOrder());
        asks = new TreeMap<>();
        ordersById = new HashMap<>();
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public void addOrder(Order order) {
        ordersById.put(order.id(), order);
        if (order.side() == Side.BUY) {
            if (bids.containsKey(order.price())) {
                bids.get(order.price()).addLast(order);
            } else {
                Deque<Order> deque = new ArrayDeque<>();
                deque.addLast(order);
                bids.put(order.price(), deque);
            }
        } else {
            if (asks.containsKey(order.price())) {
                asks.get(order.price()).addLast(order);
            } else {
                Deque<Order> deque = new ArrayDeque<>();
                deque.addLast(order);
                asks.put(order.price(), deque);
            }
        }

    }

    public Order pollFirstOrder(BigDecimal price, Side side) {
        Deque<Order> deque = side == Side.BUY ? bids.get(price) : asks.get(price);
        if (deque == null) {
            throw new NoSuchElementException("Заявки с ценой: " + price + " не существует");
        }

        Order deleteOrder = deque.pollFirst();
        ordersById.remove(deleteOrder.id());

        if (deque.isEmpty()) {
            if (side == Side.BUY) {
                bids.remove(price);
            } else {
                asks.remove(price);
            }
        }

        return deleteOrder;
    }

    public Order removeOrder(UUID orderId, Side side) {
        Order deleteOrder = ordersById.get(orderId);
        if (deleteOrder == null) {
            throw new NoSuchElementException("orderId points to null");
        }
        if (deleteOrder.side() != side) {
            throw new IllegalArgumentException("Order side doesn't match argument side");
        }
        ordersById.remove(orderId);
        if (side == Side.BUY) {
            Deque<Order> deque = bids.get(deleteOrder.price());
            deque.remove(deleteOrder);
            if (deque.isEmpty()) {
                bids.remove(deleteOrder.price());
            }
        } else {
            Deque<Order> deque = asks.get(deleteOrder.price());
            deque.remove(deleteOrder);
            if (deque.isEmpty()) {
                asks.remove(deleteOrder.price());
            }
        }
        return  deleteOrder;
    }

    public Optional<BigDecimal> bestBid() {
        if (bids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(bids.firstKey());
    }

    public Optional<BigDecimal> bestAsk() {
        if (asks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(asks.firstKey());
    }

    public Optional<Order> peekBestBidOrder() {
        if (bids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(bids.get(bids.firstKey()).getFirst());
    }

    public Optional<Order> peekBestAskOrder() {
        if (asks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(asks.get(asks.firstKey()).getFirst());
    }

    public void reduceOrderQuantity(Side side, BigDecimal price, BigDecimal fillingQuantity) {
        Deque<Order> currentDeque = side == Side.BUY ? bids.get(price) : asks.get(price);
        if (currentDeque == null) {
            throw new NoSuchElementException("Заявки с ценой: " + price + " не существует");
        }

        Order oldOrder = currentDeque.pollFirst();

        Order newOrder = new Order(
                oldOrder.id(),
                oldOrder.instrumentId(),
                oldOrder.side(),
                oldOrder.price(),
                oldOrder.quantity(),
                oldOrder.remainingQuantity().subtract(fillingQuantity),
                OrderStatus.PARTIALLY_FILLED);

        currentDeque.addFirst(newOrder);
        ordersById.put(newOrder.id(), newOrder);
    }

    void clear() {
        bids.clear();
        asks.clear();
        ordersById.clear();
    }
}
