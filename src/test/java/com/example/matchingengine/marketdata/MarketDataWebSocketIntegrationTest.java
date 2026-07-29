package com.example.matchingengine.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketDataWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();
    private final StandardWebSocketClient webSocketClient = new StandardWebSocketClient();

    private BlockingQueue<String> receivedMessages;

    @BeforeEach
    void setUp() throws Exception {
        receivedMessages = new ArrayBlockingQueue<>(10);

        WebSocketHandler handler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(@NonNull WebSocketSession session, TextMessage message) {
                receivedMessages.add(message.getPayload());
            }
        };

        WebSocketSession session = webSocketClient.execute(handler, "ws://localhost:" + port + "/ws/market-data").get();
    }

    @Test
    @DisplayName("при подключении клиент сразу получает снапшот стакана")
    void onConnect_clientReceivesInitialSnapshot() throws Exception {
        String firstMessage = receivedMessages.poll(2, TimeUnit.SECONDS);

        assertNotNull(firstMessage);
        assertTrue(firstMessage.contains("instrumentId"));
    }

    @Test
    @DisplayName("сделка через REST приводит к рассылке обновлений по WebSocket")
    void tradeViaRest_triggersWebSocketBroadcast() throws Exception {
        receivedMessages.poll(2, TimeUnit.SECONDS);

        submitOrder("""
                {"side": "SELL", "orderType": "LIMIT", "price": 50000, "quantity": 5}
                """);

        String afterFirstOrder = receivedMessages.poll(2, TimeUnit.SECONDS);
        assertNotNull(afterFirstOrder);

        submitOrder("""
                {"side": "BUY", "orderType": "LIMIT", "price": 50000, "quantity": 5}
                """);

        boolean tradeMessageReceived = false;
        for (int i = 0; i < 3; i++) {
            String message = receivedMessages.poll(2, TimeUnit.SECONDS);
            if (message != null && message.contains("quantity") && !message.contains("bids")) {
                tradeMessageReceived = true;
                break;
            }
        }

        assertTrue(tradeMessageReceived);
    }

    private void submitOrder(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/orders",
                new HttpEntity<>(json, headers),
                String.class
        );
    }
}