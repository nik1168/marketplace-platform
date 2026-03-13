# Marketplace Platform — Design Document

## Purpose

A Spring Boot learning project designed to cover the technical requirements of a Senior Software Engineer (AI-First Development) role at AssureSoft. The project demonstrates: Java 21 virtual threads, Kafka event-driven architecture, gRPC inter-service communication, REST APIs, PostgreSQL, MongoDB, observability, and microservices patterns.

## Architecture Overview

Two microservices connected via Kafka (async events) and gRPC (sync calls):

```
Client (REST) → Order Service → gRPC → Inventory Service
                     │                        ▲
                     └──── Kafka ─────────────┘
```

### Order Service
- **Tech:** Spring Boot 3.3+, Java 21, PostgreSQL, Maven
- **Responsibilities:** Accept orders via REST API, validate stock via gRPC, persist orders, publish order events to Kafka, consume inventory events to update order status.
- **REST Endpoints:** POST/GET /api/orders, PUT /api/orders/{id}/cancel, GET /actuator/health
- **PostgreSQL tables:** `orders` (id, customer_id, status, total_amount, created_at), `order_items` (id, order_id, product_id, quantity, unit_price)
- **Order lifecycle:** PENDING → CONFIRMED / REJECTED, CONFIRMED → SHIPPED / CANCELLED
- **Kafka:** Produces to `order-events` topic (OrderPlaced, OrderCancelled). Consumes from `inventory-events` topic (StockUpdated, StockReleased).

### Inventory Service
- **Tech:** Spring Boot 3.3+, Java 21, MongoDB, Maven
- **Responsibilities:** Manage product stock, serve gRPC stock queries, consume order events, update inventory, publish inventory events.
- **MongoDB collections:** `products` (product_id, name, sku, category, current_stock, reserved_stock, last_updated), `stock_events` (event_id, product_id, event_type, quantity, timestamp, order_id)
- **gRPC endpoints:** CheckStock(product_id, quantity) → (available, current_stock), ReserveStock(product_id, quantity, order_id) → (success, message)
- **Kafka:** Consumes from `order-events` topic. Produces to `inventory-events` topic (StockUpdated, StockReleased).
- **Concurrency:** Optimistic concurrency via MongoDB atomic findAndModify with version check.

## Project Structure

Multi-module Maven project:

```
marketplace-platform/
├── pom.xml                          (parent POM)
├── order-service/
│   └── src/main/java/com/marketplace/order/
│       ├── controller/              (REST)
│       ├── service/                 (business logic)
│       ├── repository/              (JPA)
│       ├── model/                   (entities)
│       ├── dto/                     (request/response)
│       ├── grpc/                    (client stubs)
│       ├── kafka/                   (producers)
│       └── config/
├── inventory-service/
│   └── src/main/java/com/marketplace/inventory/
│       ├── service/
│       ├── repository/              (MongoDB)
│       ├── model/                   (documents)
│       ├── grpc/                    (server impl)
│       ├── kafka/                   (consumers + producers)
│       └── config/
├── proto/
│   └── inventory.proto              (shared gRPC contract)
└── docker-compose.yml               (PostgreSQL, MongoDB, Kafka KRaft)
```

## Key Technology Decisions

| Decision | Rationale |
|----------|-----------|
| Virtual threads (Java 21) | Handle 1000+ concurrent requests without thread pool exhaustion on blocking I/O (DB, gRPC) |
| Kafka for async events | Decouples order placement from inventory processing; provides replay, backpressure, and resilience |
| gRPC for sync stock checks | Binary protobuf + HTTP/2 multiplexing is faster than REST for internal service-to-service calls; strong typing via .proto |
| PostgreSQL for orders | ACID transactions for order lifecycle integrity |
| MongoDB for inventory | High-write append-only stock events, flexible product schemas per category |
| Kafka KRaft mode | No Zookeeper dependency, simpler local setup |
| Testcontainers | Integration tests against real PostgreSQL, MongoDB, Kafka — no mocks for infrastructure |

## Observability (Phase 1)

- Spring Boot Actuator (health, info, metrics endpoints)
- Micrometer metrics (order counts, latency, Kafka consumer lag)
- Structured logging with SLF4J/Logback
- Future: Prometheus + Grafana + OpenTelemetry tracing

## Design Scale Target

- ~1,000-5,000 orders/second
- Multiple concurrent supplier inventory updates
- Virtual threads justify themselves at this concurrency level

## Future Phases

- **Phase 2:** Spec-driven development with spec-kit, OpenAPI contracts
- **Phase 3:** Full observability stack (Prometheus, Grafana, OpenTelemetry)
- **Phase 4:** Docker/Kubernetes deployment, AWS EKS
- **Phase 5:** CQRS read model, saga patterns for multi-step order workflows
