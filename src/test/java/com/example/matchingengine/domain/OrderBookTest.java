package com.example.matchingengine.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private static final String INSTRUMENT_ID = "BTC-USD";
    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("50000");
    private static final BigDecimal DEFAULT_QUANTITY = new BigDecimal("5");

    private OrderBook orderBook;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook(INSTRUMENT_ID);
    }

    private Order createOrder(UUID id, Side side) {
        return createOrder(id, side, DEFAULT_PRICE);
    }

    private Order createOrder(UUID id, Side side, BigDecimal price) {
        return new Order(
                id,
                INSTRUMENT_ID,
                side,
                OrderType.LIMIT,
                price,
                DEFAULT_QUANTITY,
                DEFAULT_QUANTITY,
                OrderStatus.NEW
        );
    }

    private Optional<BigDecimal> bestPrice(Side side) {
        return side == Side.BUY ? orderBook.bestBid() : orderBook.bestAsk();
    }

    private Optional<Order> peekBestOrder(Side side) {
        return side == Side.BUY ? orderBook.peekBestBidOrder() : orderBook.peekBestAskOrder();
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("addOrder кладёт заявку на правильную сторону и не трогает противоположную")
    void addOrder_addsToCorrectSide(Side side) {
        Order order = createOrder(UUID.randomUUID(), side);

        orderBook.addOrder(order);

        assertEquals(Optional.of(DEFAULT_PRICE), bestPrice(side));

        Side oppositeSide = side == Side.BUY ? Side.SELL : Side.BUY;
        assertTrue(bestPrice(oppositeSide).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("несколько заявок на одном уровне цены сохраняют FIFO порядок")
    void addOrder_multipleOrdersSamePrice_preservesFifoOrder(Side side) {
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        UUID orderId3 = UUID.randomUUID();

        orderBook.addOrder(createOrder(orderId1, side));
        orderBook.addOrder(createOrder(orderId2, side));
        orderBook.addOrder(createOrder(orderId3, side));

        assertEquals(orderId1, peekBestOrder(side).get().id());
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("удаление не последней заявки в очереди не трогает остальные")
    void removeOrder_notLastInDeque_removesOnlyThatOrder(Side side) {
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();

        Order order1 = createOrder(orderId1, side);
        orderBook.addOrder(order1);
        orderBook.addOrder(createOrder(orderId2, side));

        Order removed = orderBook.removeOrder(orderId1, side);

        assertEquals(order1, removed);
        assertEquals(orderId2, peekBestOrder(side).get().id());
        assertThrows(NoSuchElementException.class, () -> orderBook.removeOrder(orderId1, side));
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("удаление последней заявки на уровне убирает сам уровень цены")
    void removeOrder_lastInDeque_removesPriceLevelEntirely(Side side) {
        UUID orderId = UUID.randomUUID();
        orderBook.addOrder(createOrder(orderId, side));

        orderBook.removeOrder(orderId, side);

        assertTrue(bestPrice(side).isEmpty());
        assertTrue(peekBestOrder(side).isEmpty());
    }

    @Test
    @DisplayName("removeOrder с несуществующим id кидает NoSuchElementException")
    void removeOrder_unknownOrderId_throwsNoSuchElementException() {
        UUID unknownId = UUID.randomUUID();

        assertThrows(NoSuchElementException.class,
                () -> orderBook.removeOrder(unknownId, Side.BUY));
    }

    @Test
    @DisplayName("removeOrder с несовпадающим side кидает IllegalArgumentException")
    void removeOrder_sideMismatch_throwsIllegalArgumentException() {
        UUID orderId = UUID.randomUUID();
        orderBook.addOrder(createOrder(orderId, Side.BUY));

        assertThrows(IllegalArgumentException.class,
                () -> orderBook.removeOrder(orderId, Side.SELL));
    }

    @Test
    @DisplayName("пустой стакан — bestBid и bestAsk пустые")
    void emptyOrderBook_bestBidAndBestAsk_returnEmpty() {
        assertTrue(orderBook.bestBid().isEmpty());
        assertTrue(orderBook.bestAsk().isEmpty());
    }

    @Test
    @DisplayName("пустой стакан — peekBestBidOrder и peekBestAskOrder пустые")
    void emptyOrderBook_peekMethods_returnEmpty() {
        assertTrue(orderBook.peekBestBidOrder().isEmpty());
        assertTrue(orderBook.peekBestAskOrder().isEmpty());
    }

    @Test
    @DisplayName("bestBid возвращает самую высокую цену из нескольких уровней")
    void bestBid_returnsHighestPrice_whenMultipleLevels() {
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.BUY, new BigDecimal("64900")));
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.BUY, new BigDecimal("65000")));
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.BUY, new BigDecimal("64950")));

        assertEquals(Optional.of(new BigDecimal("65000")), orderBook.bestBid());
    }

    @Test
    @DisplayName("bestAsk возвращает самую низкую цену из нескольких уровней")
    void bestAsk_returnsLowestPrice_whenMultipleLevels() {
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.SELL, new BigDecimal("65200")));
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.SELL, new BigDecimal("65100")));
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.SELL, new BigDecimal("65150")));

        assertEquals(Optional.of(new BigDecimal("65100")), orderBook.bestAsk());
    }

    @Test
    @DisplayName("reduceOrderQuantity уменьшает остаток и сохраняет позицию первой в очереди")
    void reduceOrderQuantity_reducesRemainingQuantity_keepsPosition() {
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();

        orderBook.addOrder(createOrder(orderId1, Side.BUY));
        orderBook.addOrder(createOrder(orderId2, Side.BUY));

        orderBook.reduceOrderQuantity(Side.BUY, DEFAULT_PRICE, new BigDecimal("2"));

        Order updated = orderBook.peekBestBidOrder().get();
        assertEquals(orderId1, updated.id());
        assertEquals(new BigDecimal("3"), updated.remainingQuantity());
    }

    @Test
    @DisplayName("reduceOrderQuantity выставляет статус PARTIALLY_FILLED")
    void reduceOrderQuantity_setsPartiallyFilledStatus() {
        UUID orderId = UUID.randomUUID();
        orderBook.addOrder(createOrder(orderId, Side.BUY));

        orderBook.reduceOrderQuantity(Side.BUY, DEFAULT_PRICE, new BigDecimal("1"));

        assertEquals(OrderStatus.PARTIALLY_FILLED, orderBook.peekBestBidOrder().get().orderStatus());
    }

    @Test
    @DisplayName("reduceOrderQuantity синхронизирует индекс ordersById")
    void reduceOrderQuantity_updatesOrdersByIdIndex() {
        UUID orderId = UUID.randomUUID();
        orderBook.addOrder(createOrder(orderId, Side.BUY));

        orderBook.reduceOrderQuantity(Side.BUY, DEFAULT_PRICE, new BigDecimal("2"));
        Order removed = orderBook.removeOrder(orderId, Side.BUY);

        assertEquals(new BigDecimal("3"), removed.remainingQuantity());
    }

    @Test
    @DisplayName("reduceOrderQuantity с несуществующей ценой кидает NoSuchElementException")
    void reduceOrderQuantity_unknownPrice_throwsNoSuchElementException() {
        BigDecimal noSuchPrice = new BigDecimal("1");

        assertThrows(NoSuchElementException.class,
                () -> orderBook.reduceOrderQuantity(Side.BUY, noSuchPrice, new BigDecimal("1")));
    }

    @Test
    @DisplayName("pollFirstOrder удаляет и возвращает первую заявку в очереди")
    void pollFirstOrder_returnsAndRemovesFirstOrder() {
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();

        orderBook.addOrder(createOrder(orderId1, Side.BUY));
        orderBook.addOrder(createOrder(orderId2, Side.BUY));

        Order polled = orderBook.pollFirstOrder(DEFAULT_PRICE, Side.BUY);

        assertEquals(orderId1, polled.id());
        assertEquals(orderId2, orderBook.peekBestBidOrder().get().id());
    }

    @Test
    @DisplayName("pollFirstOrder последней заявки на уровне убирает сам уровень цены")
    void pollFirstOrder_lastOnLevel_removesPriceLevelEntirely() {
        orderBook.addOrder(createOrder(UUID.randomUUID(), Side.BUY));

        orderBook.pollFirstOrder(DEFAULT_PRICE, Side.BUY);

        assertTrue(orderBook.bestBid().isEmpty());
        assertTrue(orderBook.peekBestBidOrder().isEmpty());
    }

    @Test
    @DisplayName("pollFirstOrder синхронизирует индекс ordersById")
    void pollFirstOrder_removesFromOrdersByIdIndex() {
        UUID orderId = UUID.randomUUID();
        orderBook.addOrder(createOrder(orderId, Side.BUY));

        orderBook.pollFirstOrder(DEFAULT_PRICE, Side.BUY);

        assertThrows(NoSuchElementException.class,
                () -> orderBook.removeOrder(orderId, Side.BUY));
    }

    @Test
    @DisplayName("pollFirstOrder с несуществующей ценой кидает NoSuchElementException")
    void pollFirstOrder_unknownPrice_throwsNoSuchElementException() {
        BigDecimal noSuchPrice = new BigDecimal("1");

        assertThrows(NoSuchElementException.class,
                () -> orderBook.pollFirstOrder(noSuchPrice, Side.BUY));
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("pollFirstOrder работает симметрично для BUY и SELL")
    void pollFirstOrder_worksForBothSides(Side side) {
        UUID orderId = UUID.randomUUID();
        orderBook.addOrder(createOrder(orderId, side));

        Order polled = orderBook.pollFirstOrder(DEFAULT_PRICE, side);

        assertEquals(orderId, polled.id());
    }
}