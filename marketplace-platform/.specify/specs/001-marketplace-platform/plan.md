# Marketplace Platform — Technical Plan

## Architecture Decision: Microservices

Two independent Spring Boot microservices, each with its own database, communicating via Kafka (async) and gRPC (sync).

**Why two services instead of a monolith?**
- Order management and inventory management have different data models, scaling needs, and update patterns
- Demonstrates real-world microservices patterns (service discovery, event-driven architecture, polyglot persistence)

## Services

### Order Service (port 8080)
- **Responsibility:** Manage customer orders (create, cancel, track status)
- **Database:** PostgreSQL — orders are relational data (order → items) with transactional requirements
- **Communication:**
  - REST API for external clients
  - gRPC client to query inventory stock
  - Kafka producer for order events (OrderPlaced, OrderCancelled)
  - Kafka consumer for inventory events (StockUpdated)

### Inventory Service (port 8081, gRPC on 9090)
- **Responsibility:** Manage product catalog and stock levels
- **Database:** MongoDB — products are self-contained documents, flexible schema, optimistic locking via `@Version`
- **Communication:**
  - REST API for admin product management
  - gRPC server for stock queries (CheckStock, ReserveStock)
  - Kafka consumer for order events (OrderPlaced, OrderCancelled)
  - Kafka producer for inventory events (StockUpdated)

## Technology Choices

| Technology | Purpose | Why This Choice |
|---|---|---|
| Java 21 | Runtime | Virtual threads for concurrent I/O without reactive complexity |
| Spring Boot 3.3 | Framework | Mature ecosystem, auto-configuration, extensive integrations |
| PostgreSQL | Order DB | ACID transactions for financial data, relational model fits orders+items |
| MongoDB | Inventory DB | Flexible documents, built-in optimistic locking, fast reads |
| Apache Kafka | Messaging | Durable event log, partition-based ordering, consumer groups for scaling |
| gRPC | Sync communication | Binary protocol, typed contracts, faster than REST for internal calls |
| Micrometer | Observability | Spring-native metrics, Prometheus-compatible |
| Testcontainers | Testing | Real infrastructure in tests, no mocks for databases/Kafka |
| Docker Compose | Infrastructure | Single-command local dev environment |
| React + TypeScript | Frontend | Admin dashboard with Material UI |

## Data Models

### Order (PostgreSQL)
```
orders
├── id: UUID (PK)
├── customer_id: VARCHAR
├── status: ENUM (PENDING, CONFIRMED, REJECTED, CANCELLED)
├── total_amount: DECIMAL
├── created_at: TIMESTAMP
└── updated_at: TIMESTAMP

order_items
├── id: UUID (PK)
├── order_id: UUID (FK → orders)
├── product_id: VARCHAR
├── quantity: INTEGER
└── unit_price: DECIMAL
```

### Product (MongoDB)
```
products
├── _id: ObjectId
├── productId: String (unique business key)
├── name: String
├── description: String
├── price: Double
├── stockQuantity: Integer (total physical stock)
├── reservedQuantity: Integer (units reserved for pending orders)
├── version: Long (optimistic locking)
├── createdAt: Instant
└── updatedAt: Instant

Available stock = stockQuantity - reservedQuantity
```

## Communication Patterns

### Synchronous (gRPC) — Stock Queries
```
Order Service → gRPC → Inventory Service
  CheckStock(productId, quantity) → available: bool
  ReserveStock(productId, quantity, orderId) → success: bool
```
Used during order placement to verify and reserve stock before saving the order.

### Asynchronous (Kafka) — State Changes

**Topic: order-events (3 partitions)**
- OrderPlacedEvent: {orderId, customerId, items[]}
- OrderCancelledEvent: {orderId, items[]}

**Topic: inventory-events (3 partitions)**
- StockUpdatedEvent: {orderId, productId, success, message}

Key-based partitioning: orderId as message key ensures all events for the same order are processed in order.

## Concurrency Strategy

1. **gRPC stock check** — fast synchronous verification (can pass for both concurrent requests)
2. **gRPC stock reserve** — attempts reservation with optimistic locking
3. **Optimistic locking** — MongoDB `@Version` field detects concurrent modifications
4. **On conflict** — `OptimisticLockingFailureException` caught, reservation returns `false`, order gets REJECTED
5. **Result** — one order succeeds, the other is safely rejected. No overselling.

## Order Lifecycle (Saga)

```
1. Client → POST /api/orders
2. Order Service → gRPC checkStock (sync)
3. Order Service → gRPC reserveStock (sync)
4. Order Service → save order as PENDING (PostgreSQL)
5. Order Service → publish OrderPlaced (Kafka)
6. Inventory Service → consume OrderPlaced → confirm reservation
7. Inventory Service → publish StockUpdated(success=true) (Kafka)
8. Order Service → consume StockUpdated → update order to CONFIRMED
```

**Compensation (cancellation):**
```
1. Client → PUT /api/orders/{id}/cancel
2. Order Service → update order to CANCELLED (PostgreSQL)
3. Order Service → publish OrderCancelled (Kafka)
4. Inventory Service → consume OrderCancelled → releaseStock
```

## Infrastructure (Docker Compose)

| Service | Port | Image |
|---|---|---|
| PostgreSQL | 5432 | postgres:16 |
| MongoDB | 27017 | mongo:7 |
| Kafka (KRaft) | 29092 | apache/kafka:3.7.0 |

KRaft mode — no ZooKeeper dependency.

## Observability

- **Health:** `/actuator/health` on both services
- **Metrics:** `/actuator/prometheus` — custom counters for orders created, rejected, and processing duration
- **Logging:** SLF4J with order IDs in all log messages for traceability
