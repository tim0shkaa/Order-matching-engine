package com.example.matchingengine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MatchingEngineApplicationTests {

    @Test
    void contextLoads() {
        // Дымовой тест Phase 0: проверяет, что Spring-контекст поднимается без ошибок.
        // Больше здесь ничего не должно появиться — бизнес-логику тестируем
        // отдельно и без поднятия контекста (engine/domain не зависят от Spring).
    }
}
