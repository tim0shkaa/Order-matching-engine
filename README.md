# Matching Engine

Пет-проект: биржевой движок сведения заявок (order matching engine) на Java 21 / Spring Boot.

```bash
gradle bootRun
```

Приложение поднимется на `http://localhost:8080`.
Проверка: `GET /actuator/health`.

## Тесты

```bash
gradle test
```

## Структура проекта

- `domain` — доменные объекты (Order, Trade, OrderBook), без зависимостей от Spring
- `engine` — ядро matching engine, чистая Java
- `gateway` — REST API (приём заявок)
- `marketdata` — WebSocket market data feed (реализация в Phase 2)
- `persistence` — WAL и recovery (реализация в Phase 4)
- `config` — Spring-конфигурация

## Статус

- [x] Phase 0 — инфраструктура
- [ ] Phase 1 — MVP: один инструмент, LIMIT-заявки, single-threaded
- [ ] Phase 2 — Order types + Market Data Feed
- [ ] Phase 3 — Concurrency upgrade (LMAX Disruptor)
- [ ] Phase 4 — Persistence & Recovery (WAL)
- [ ] Phase 5 — Stretch goals
