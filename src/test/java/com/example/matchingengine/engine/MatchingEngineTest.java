package com.example.matchingengine.engine;

import com.example.matchingengine.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {

    private static final String INSTRUMENT_ID = "BTC-USD";

    private OrderBook orderBook;
    private MatchingEngine matchingEngine;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook(INSTRUMENT_ID);
        matchingEngine = new MatchingEngine(orderBook);
    }

    private Order createOrder(UUID id, Side side, BigDecimal price, BigDecimal quantity) {
        return new Order(id, INSTRUMENT_ID, side, price, quantity, quantity, OrderStatus.NEW);
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("заявка в пустой стакан не создаёт сделок и остаётся NEW")
    void submitOrder_emptyBook_noTradesAndStaysNew(Side side) {
        Order order = createOrder(UUID.randomUUID(), side, BigDecimal.valueOf(50000), BigDecimal.valueOf(5));

        MatchingResult result = matchingEngine.submitOrder(order);

        assertTrue(result.trades().isEmpty());
        assertEquals(OrderStatus.NEW, result.resultingOrder().orderStatus());
    }

    @Test
    @DisplayName("цены не пересекаются — обе заявки остаются в стакане без сделок")
    void submitOrder_pricesDoNotCross_noTrades() {
        UUID orderIdSell = UUID.randomUUID();
        Order orderSell = createOrder(orderIdSell, Side.SELL, BigDecimal.valueOf(60000), BigDecimal.valueOf(5));
        orderBook.addOrder(orderSell);

        Order orderBuy = createOrder(UUID.randomUUID(), Side.BUY, BigDecimal.valueOf(50000), BigDecimal.valueOf(5));
        MatchingResult result = matchingEngine.submitOrder(orderBuy);

        assertTrue(result.trades().isEmpty());
        assertEquals(OrderStatus.NEW, result.resultingOrder().orderStatus());

        assertTrue(orderBook.peekBestBidOrder().isPresent());
        assertEquals(BigDecimal.valueOf(5), orderBook.peekBestBidOrder().get().remainingQuantity());

        assertTrue(orderBook.peekBestAskOrder().isPresent());
        assertEquals(orderIdSell, orderBook.peekBestAskOrder().get().id());
        assertEquals(BigDecimal.valueOf(5), orderBook.peekBestAskOrder().get().remainingQuantity());
    }

    @Test
    @DisplayName("новая заявка полностью исполняется одной встречной заявкой")
    void submitOrder_fullyMatchedBySingleRestingOrder_returnsFilled() {
        UUID orderIdSell = UUID.randomUUID();
        orderBook.addOrder(createOrder(orderIdSell, Side.SELL, BigDecimal.valueOf(40000), BigDecimal.valueOf(5)));

        UUID orderIdBuy = UUID.randomUUID();
        Order orderBuy = createOrder(orderIdBuy, Side.BUY, BigDecimal.valueOf(50000), BigDecimal.valueOf(5));
        MatchingResult result = matchingEngine.submitOrder(orderBuy);

        assertTrue(orderBook.peekBestBidOrder().isEmpty());
        assertTrue(orderBook.peekBestAskOrder().isEmpty());

        assertEquals(1, result.trades().size());
        assertEquals(orderIdBuy, result.trades().get(0).buyOrderId());
        assertEquals(orderIdSell, result.trades().get(0).sellOrderId());
        assertEquals(BigDecimal.valueOf(5), result.trades().get(0).quantity());
        assertEquals(OrderStatus.FILLED, result.resultingOrder().orderStatus());
    }

    @Test
    @DisplayName("новая заявка полностью исполняется несколькими встречными заявками подряд")
    void submitOrder_fullyMatchedByMultipleRestingOrders_loopsCorrectly() {
        UUID sellId1 = UUID.randomUUID();
        UUID sellId2 = UUID.randomUUID();
        orderBook.addOrder(createOrder(sellId1, Side.SELL, BigDecimal.valueOf(65000), BigDecimal.valueOf(2)));
        orderBook.addOrder(createOrder(sellId2, Side.SELL, BigDecimal.valueOf(65100), BigDecimal.valueOf(3)));

        Order orderBuy = createOrder(UUID.randomUUID(), Side.BUY, BigDecimal.valueOf(65100), BigDecimal.valueOf(4));
        MatchingResult result = matchingEngine.submitOrder(orderBuy);

        assertEquals(2, result.trades().size());

        assertEquals(sellId1, result.trades().get(0).sellOrderId());
        assertEquals(BigDecimal.valueOf(65000), result.trades().get(0).price());
        assertEquals(BigDecimal.valueOf(2), result.trades().get(0).quantity());

        assertEquals(sellId2, result.trades().get(1).sellOrderId());
        assertEquals(BigDecimal.valueOf(65100), result.trades().get(1).price());
        assertEquals(BigDecimal.valueOf(2), result.trades().get(1).quantity());

        assertEquals(OrderStatus.FILLED, result.resultingOrder().orderStatus());

        assertTrue(orderBook.peekBestAskOrder().isPresent());
        assertEquals(sellId2, orderBook.peekBestAskOrder().get().id());
        assertEquals(BigDecimal.valueOf(1), orderBook.peekBestAskOrder().get().remainingQuantity());
    }

    @Test
    @DisplayName("встречная заявка исполняется частично и остаётся в стакане с уменьшенным остатком")
    void submitOrder_partiallyFillsRestingOrder_restingStaysInBookWithReducedQuantity() {
        UUID sellId = UUID.randomUUID();
        orderBook.addOrder(createOrder(sellId, Side.SELL, BigDecimal.valueOf(50000), BigDecimal.valueOf(5)));

        Order orderBuy = createOrder(UUID.randomUUID(), Side.BUY, BigDecimal.valueOf(50000), BigDecimal.valueOf(2));
        MatchingResult result = matchingEngine.submitOrder(orderBuy);

        assertEquals(1, result.trades().size());
        assertEquals(BigDecimal.valueOf(2), result.trades().get(0).quantity());
        assertEquals(OrderStatus.FILLED, result.resultingOrder().orderStatus());

        assertTrue(orderBook.peekBestAskOrder().isPresent());
        assertEquals(sellId, orderBook.peekBestAskOrder().get().id());
        assertEquals(BigDecimal.valueOf(3), orderBook.peekBestAskOrder().get().remainingQuantity());
        assertEquals(OrderStatus.PARTIALLY_FILLED, orderBook.peekBestAskOrder().get().orderStatus());
    }

    @Test
    @DisplayName("новой заявке не хватает ликвидности — остаток уходит в стакан со статусом PARTIALLY_FILLED")
    void submitOrder_notEnoughLiquidity_remainderRestsInBookAsPartiallyFilled() {
        UUID sellId = UUID.randomUUID();
        orderBook.addOrder(createOrder(sellId, Side.SELL, BigDecimal.valueOf(50000), BigDecimal.valueOf(2)));

        UUID buyId = UUID.randomUUID();
        Order orderBuy = createOrder(buyId, Side.BUY, BigDecimal.valueOf(50000), BigDecimal.valueOf(5));
        MatchingResult result = matchingEngine.submitOrder(orderBuy);

        assertEquals(1, result.trades().size());
        assertEquals(BigDecimal.valueOf(2), result.trades().get(0).quantity());
        assertEquals(OrderStatus.PARTIALLY_FILLED, result.resultingOrder().orderStatus());
        assertEquals(BigDecimal.valueOf(3), result.resultingOrder().remainingQuantity());

        assertTrue(orderBook.peekBestAskOrder().isEmpty());
        assertTrue(orderBook.peekBestBidOrder().isPresent());
        assertEquals(buyId, orderBook.peekBestBidOrder().get().id());
        assertEquals(BigDecimal.valueOf(3), orderBook.peekBestBidOrder().get().remainingQuantity());
    }

    @Test
    @DisplayName("цена сделки берётся из resting-заявки, а не из входящей")
    void submitOrder_tradePrice_equalsRestingOrderPrice_notIncomingPrice() {
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.SELL, BigDecimal.valueOf(50000), BigDecimal.valueOf(5)));

        Order orderBuy = createOrder(UUID.randomUUID(), Side.BUY, BigDecimal.valueOf(65100), BigDecimal.valueOf(5));
        MatchingResult result = matchingEngine.submitOrder(orderBuy);

        assertEquals(BigDecimal.valueOf(50000), result.trades().get(0).price());
    }

    @Test
    @DisplayName("сделка на равных объёмах — обе заявки исполняются полностью")
    void submitOrder_exactQuantityMatch_bothOrdersFullyFilled() {
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.SELL, BigDecimal.valueOf(50000), BigDecimal.valueOf(5)));

        Order orderBuy = createOrder(UUID.randomUUID(), Side.BUY, BigDecimal.valueOf(50000), BigDecimal.valueOf(5));
        MatchingResult result = matchingEngine.submitOrder(orderBuy);

        assertEquals(OrderStatus.FILLED, result.resultingOrder().orderStatus());
        assertTrue(orderBook.peekBestAskOrder().isEmpty());
        assertTrue(orderBook.peekBestBidOrder().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("после полного исполнения заявка не попадает в стакан")
    void submitOrder_fullyFilled_notAddedToOrderBook(Side side) {
        Side oppositeSide = side == Side.BUY ? Side.SELL : Side.BUY;
        orderBook.addOrder(createOrder(UUID.randomUUID(), oppositeSide, BigDecimal.valueOf(50000), BigDecimal.valueOf(5)));

        Order order = createOrder(UUID.randomUUID(), side, BigDecimal.valueOf(50000), BigDecimal.valueOf(5));
        matchingEngine.submitOrder(order);

        assertTrue(orderBook.peekBestBidOrder().isEmpty());
        assertTrue(orderBook.peekBestAskOrder().isEmpty());
    }
}