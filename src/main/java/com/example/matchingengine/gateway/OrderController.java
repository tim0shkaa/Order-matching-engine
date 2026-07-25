package com.example.matchingengine.gateway;

import com.example.matchingengine.engine.MatchingEngine;
import com.example.matchingengine.gateway.dto.OrderRequest;
import com.example.matchingengine.gateway.dto.OrderResponse;
import com.example.matchingengine.gateway.mapper.OrderMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderMapper orderMapper;
    private final MatchingEngine matchingEngine;

    public OrderController(OrderMapper orderMapper, MatchingEngine matchingEngine) {
        this.orderMapper = orderMapper;
        this.matchingEngine = matchingEngine;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderMapper.resultToResponseMapper(
                        matchingEngine.submitOrder(
                                orderMapper.requestToOrderMapper(request))));
    }
}
