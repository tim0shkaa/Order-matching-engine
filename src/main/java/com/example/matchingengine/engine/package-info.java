/**
 * Ядро matching engine: алгоритм сведения заявок по правилу price-time priority.
 * <p>
 * Чистая Java, без Spring-зависимостей. Это важно для Phase 3: изолированные JMH-бенчмарки
 * должны гонять только этот код, без оверхеда поднятия Spring-контекста.
 */
package com.example.matchingengine.engine;
