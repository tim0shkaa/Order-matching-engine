package com.example.matchingengine.marketdata;

import com.example.matchingengine.domain.OrderBook;
import com.example.matchingengine.domain.PriceLevel;
import com.example.matchingengine.domain.Side;
import com.example.matchingengine.domain.Trade;
import com.example.matchingengine.marketdata.dto.OrderBookSnapshotDto;
import com.example.matchingengine.marketdata.dto.TradeEventDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class MarketDataWebSocketHandler extends TextWebSocketHandler{

    private final OrderBook orderBook;
    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    public MarketDataWebSocketHandler(OrderBook orderBook, ObjectMapper objectMapper) {
        this.orderBook = orderBook;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        sessions.add(session);
        sendSnapshot(session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session,
                                      @NonNull CloseStatus status) {
        sessions.remove(session);
    }

    @EventListener
    public void onOrderBookChanged(OrderBookChangedEvent event) {
        broadcastSnapshot();
    }

    @EventListener
    public void onTradeExecuted(TradeExecutedEvent event) {
        broadcastTrades(event);
    }

    private void broadcastSnapshot() {
        OrderBookSnapshotDto snapshot = buildSnapshot();
        for (WebSocketSession session : sessions) {
            sendToSession(session, snapshot);
        }
    }

    private void broadcastTrades(TradeExecutedEvent event) {
        List<TradeEventDto> tradeDto = event.trades().stream()
                .map(this::toTradeEventDto)
                .toList();

        for (WebSocketSession session : sessions) {
            sendToSession(session, tradeDto);
        }
    }

    private void sendSnapshot(WebSocketSession session) {
        OrderBookSnapshotDto snapshotDto = buildSnapshot();
        sendToSession(session, snapshotDto);
    }

    private OrderBookSnapshotDto buildSnapshot() {
        List<OrderBookSnapshotDto.PriceLevelDto> bids = toPriceLevelDto(orderBook.getPriceLevel(Side.BUY));
        List<OrderBookSnapshotDto.PriceLevelDto> asks = toPriceLevelDto(orderBook.getPriceLevel(Side.SELL));

        return new OrderBookSnapshotDto(orderBook.getInstrumentId(), bids, asks);
    }

    private List<OrderBookSnapshotDto.PriceLevelDto> toPriceLevelDto(List<PriceLevel> levels) {
        return levels.stream()
                .map(priceLevel ->
                        new OrderBookSnapshotDto.PriceLevelDto(priceLevel.price(), priceLevel.totalQuantity()))
                .toList();
    }

    private TradeEventDto toTradeEventDto(Trade trade) {
        return new TradeEventDto(trade.price(), trade.quantity(), trade.timestamp());
    }

    private void sendToSession(WebSocketSession session, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
