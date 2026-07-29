package com.example.matchingengine.engine;

import com.example.matchingengine.domain.*;
import com.example.matchingengine.marketdata.OrderBookChangedEvent;
import com.example.matchingengine.marketdata.TradeExecutedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {

    private static final String INSTRUMENT_ID = "BTC-USD";

    private OrderBook orderBook;
    private MatchingEngine matchingEngine;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook(INSTRUMENT_ID);
        EventPublisher noOpEventPublisher = event -> {};
        matchingEngine = new MatchingEngine(orderBook, noOpEventPublisher);
    }

    private Order createOrder(UUID id, Side side, BigDecimal price, BigDecimal quantity, OrderType orderType) {
        return new Order(id, INSTRUMENT_ID, side, orderType, price, quantity, quantity, OrderStatus.NEW);
    }

    private Order createOrder(UUID id, Side side, BigDecimal price, BigDecimal quantity) {
        return createOrder(id, side, price, quantity, OrderType.LIMIT);
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

    // --- MARKET ---

    @Test
    @DisplayName("MARKET заявка исполняется по любой доступной цене независимо от уровня")
    void submitOrder_marketOrder_matchesAnyAvailablePrice() {
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.SELL, BigDecimal.valueOf(40000), BigDecimal.valueOf(2)));
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.SELL, BigDecimal.valueOf(90000), BigDecimal.valueOf(3)));

        Order marketBuy = createOrder(UUID.randomUUID(), Side.BUY, BigDecimal.ZERO, BigDecimal.valueOf(4), OrderType.MARKET);
        MatchingResult result = matchingEngine.submitOrder(marketBuy);

        assertEquals(2, result.trades().size());
        assertEquals(OrderStatus.FILLED, result.resultingOrder().orderStatus());
    }

    @Test
    @DisplayName("MARKET заявка с недостаточной ликвидностью — остаток отменяется, не уходит в стакан")
    void submitOrder_marketOrder_notEnoughLiquidity_remainderCancelled() {
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.SELL, BigDecimal.valueOf(90000), BigDecimal.valueOf(3)));

        Order marketBuy = createOrder(UUID.randomUUID(), Side.BUY, BigDecimal.ZERO, BigDecimal.valueOf(6), OrderType.MARKET);
        MatchingResult result = matchingEngine.submitOrder(marketBuy);

        assertEquals(1, result.trades().size());
        assertEquals(BigDecimal.valueOf(3), result.trades().getFirst().quantity());
        assertEquals(OrderStatus.PARTIALLY_FILLED, result.resultingOrder().orderStatus());

        assertTrue(orderBook.peekBestAskOrder().isEmpty());
        assertTrue(orderBook.peekBestBidOrder().isEmpty());
    }

    @Test
    @DisplayName("MARKET заявка в пустой стакан — отменяется целиком")
    void submitOrder_marketOrder_emptyBook_cancelledEntirely() {
        Order marketBuy = createOrder(UUID.randomUUID(), Side.BUY, BigDecimal.ZERO, BigDecimal.valueOf(6), OrderType.MARKET);
        MatchingResult result = matchingEngine.submitOrder(marketBuy);

        assertTrue(result.trades().isEmpty());
        assertEquals(OrderStatus.CANCELLED, result.resultingOrder().orderStatus());
        assertTrue(orderBook.peekBestBidOrder().isEmpty());
    }

    // --- IOC ---

    @Test
    @DisplayName("IOC заявка исполняется в пределах своего лимита цены")
    void submitOrder_iocOrder_matchesWithinPriceLimit() {
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.SELL, BigDecimal.valueOf(50000), BigDecimal.valueOf(5)));

        Order iocBuy = createOrder(UUID.randomUUID(), Side.BUY, BigDecimal.valueOf(50000), BigDecimal.valueOf(3), OrderType.IOC);
        MatchingResult result = matchingEngine.submitOrder(iocBuy);

        assertEquals(1, result.trades().size());
        assertEquals(BigDecimal.valueOf(3), result.trades().getFirst().quantity());
        assertEquals(OrderStatus.FILLED, result.resultingOrder().orderStatus());
    }

    @Test
    @DisplayName("IOC заявка с частичным исполнением — остаток отменяется, не уходит в стакан")
    void submitOrder_iocOrder_partialFill_remainderCancelledNotRested() {
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.SELL, BigDecimal.valueOf(50000), BigDecimal.valueOf(2)));

        Order iocBuy = createOrder(UUID.randomUUID(), Side.BUY, BigDecimal.valueOf(50000), BigDecimal.valueOf(5), OrderType.IOC);
        MatchingResult result = matchingEngine.submitOrder(iocBuy);

        assertEquals(1, result.trades().size());
        assertEquals(BigDecimal.valueOf(2), result.trades().getFirst().quantity());
        assertEquals(OrderStatus.PARTIALLY_FILLED, result.resultingOrder().orderStatus());
        assertTrue(orderBook.peekBestBidOrder().isEmpty());
    }

    @Test
    @DisplayName("IOC заявка без пересечения цен — отменяется, не ложится в стакан")
    void submitOrder_iocOrder_noPriceCross_cancelledNotRested() {
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.SELL, BigDecimal.valueOf(60000), BigDecimal.valueOf(5)));

        Order iocBuy = createOrder(UUID.randomUUID(), Side.BUY, BigDecimal.valueOf(50000), BigDecimal.valueOf(5), OrderType.IOC);
        MatchingResult result = matchingEngine.submitOrder(iocBuy);

        assertTrue(result.trades().isEmpty());
        assertEquals(OrderStatus.CANCELLED, result.resultingOrder().orderStatus());
        assertTrue(orderBook.peekBestBidOrder().isEmpty());
    }

    // --- FOK ---

    @Test
    @DisplayName("FOK заявка с достаточной ликвидностью исполняется полностью")
    void submitOrder_fokOrder_enoughLiquidity_fullyFilled() {
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.SELL, BigDecimal.valueOf(50000), BigDecimal.valueOf(5)));

        Order fokBuy = createOrder(UUID.randomUUID(), Side.BUY, BigDecimal.valueOf(50000), BigDecimal.valueOf(3), OrderType.FOK);
        MatchingResult result = matchingEngine.submitOrder(fokBuy);

        assertEquals(1, result.trades().size());
        assertEquals(OrderStatus.FILLED, result.resultingOrder().orderStatus());
    }

    @Test
    @DisplayName("FOK заявка с недостаточной ликвидностью — не исполняется вообще, стакан не тронут")
    void submitOrder_fokOrder_notEnoughLiquidity_cancelledBookUntouched() {
        UUID sellId = UUID.randomUUID();
        orderBook.addOrder(createOrder(sellId, Side.SELL, BigDecimal.valueOf(50000), BigDecimal.valueOf(2)));

        Order fokBuy = createOrder(UUID.randomUUID(), Side.BUY, BigDecimal.valueOf(50000), BigDecimal.valueOf(5), OrderType.FOK);
        MatchingResult result = matchingEngine.submitOrder(fokBuy);

        assertTrue(result.trades().isEmpty());
        assertEquals(OrderStatus.CANCELLED, result.resultingOrder().orderStatus());

        assertTrue(orderBook.peekBestAskOrder().isPresent());
        assertEquals(sellId, orderBook.peekBestAskOrder().get().id());
        assertEquals(BigDecimal.valueOf(2), orderBook.peekBestAskOrder().get().remainingQuantity());
    }

    @Test
    @DisplayName("FOK заявка — граничный случай, ликвидности ровно впритык")
    void submitOrder_fokOrder_exactLiquidityMatch_fullyFilled() {
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.SELL, BigDecimal.valueOf(65000), BigDecimal.valueOf(2)));
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.SELL, BigDecimal.valueOf(65100), BigDecimal.valueOf(3)));

        Order fokBuy = createOrder(UUID.randomUUID(), Side.BUY, BigDecimal.valueOf(65100), BigDecimal.valueOf(5), OrderType.FOK);
        MatchingResult result = matchingEngine.submitOrder(fokBuy);

        assertEquals(2, result.trades().size());
        assertEquals(OrderStatus.FILLED, result.resultingOrder().orderStatus());
    }

    // --- Event ---

    @Test
    @DisplayName("после сделки публикуется TradeExecutedEvent с правильным списком сделок")
    void submitOrder_afterTrade_publishesTradeExecutedEvent() {
        List<Object> publishedEvents = new ArrayList<>();
        MatchingEngine engineWithRecording = new MatchingEngine(orderBook, publishedEvents::add);

        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.SELL, BigDecimal.valueOf(50000), BigDecimal.valueOf(5)));
        Order buy = createOrder(UUID.randomUUID(), Side.BUY, BigDecimal.valueOf(50000), BigDecimal.valueOf(5));

        engineWithRecording.submitOrder(buy);

        assertTrue(publishedEvents.stream().anyMatch(e -> e instanceof TradeExecutedEvent));
        assertTrue(publishedEvents.stream().anyMatch(e -> e instanceof OrderBookChangedEvent));
    }
}