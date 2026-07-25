package com.example.matchingengine.gateway;

import com.example.matchingengine.domain.OrderBook;
import com.example.matchingengine.engine.MatchingEngine;
import com.example.matchingengine.gateway.mapper.OrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerIntegrationTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderBook orderBook = new OrderBook("BTC-USD");
        MatchingEngine matchingEngine = new MatchingEngine(orderBook);
        OrderController controller = new OrderController(new OrderMapper(), matchingEngine);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    @DisplayName("валидная заявка в пустой стакан возвращает 201 и корректное тело ответа")
    void createOrder_validRequest_returnsCreatedWithExpectedBody() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"side": "BUY", "price": 50000, "quantity": 5}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderStatus").value("NEW"))
                .andExpect(jsonPath("$.remainingQuantity").value(5))
                .andExpect(jsonPath("$.trades").isEmpty());
    }

    @Test
    @DisplayName("заявка с отрицательной ценой возвращает 400")
    void createOrder_negativePrice_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"side": "BUY", "price": -100, "quantity": 5}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("заявка без quantity возвращает 400")
    void createOrder_missingQuantity_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"side": "SELL", "price": 50000}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("две встречные заявки подряд сквозь весь стек дают сделку")
    void createOrder_twoMatchingOrders_producesTradeAcrossFullStack() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"side": "SELL", "price": 50000, "quantity": 5}
                        """));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"side": "BUY", "price": 50000, "quantity": 5}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderStatus").value("FILLED"))
                .andExpect(jsonPath("$.remainingQuantity").value(0))
                .andExpect(jsonPath("$.trades").isArray())
                .andExpect(jsonPath("$.trades[0].price").value(50000))
                .andExpect(jsonPath("$.trades[0].quantity").value(5));
    }
}