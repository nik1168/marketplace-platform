# Marketplace Platform — Tasks

## Phase 1: Infrastructure & Project Setup

### Task 1: Initialize multi-module Maven project
- Create parent POM with dependency management (Spring Boot 3.3, Java 21)
- Create `order-service` and `inventory-service` modules
- Configure shared dependency versions: gRPC 1.64.0, Protobuf 3.25.3, Testcontainers 1.20.1
- Commit: `chore: initialize multi-module Maven project`

### Task 2: Docker Compose for local infrastructure
- PostgreSQL 16 on port 5432
- MongoDB 7 on port 27017
- Apache Kafka 3.7.0 (KRaft mode) on port 29092
- Commit: `chore: add docker-compose for local infrastructure`

### Task 3: Configure Spring Boot applications
- Order Service: `application.yml` with PostgreSQL datasource, Kafka producer/consumer, gRPC client, virtual threads, Actuator
- Inventory Service: `application.yml` with MongoDB connection, Kafka producer/consumer, gRPC server, virtual threads, Actuator
- Dev profile with data seeder for Inventory Service
- Commit: `chore: configure Spring Boot application properties`

---

## Phase 2: Inventory Service (MongoDB + gRPC Server)

### Task 4: Product data model
- Create `Product` document class with `@Document`, `@Version` for optimistic locking
- Fields: productId, name, description, price, stockQuantity, reservedQuantity, timestamps
- Add `hasAvailableStock()`, `reserveStock()`, `releaseStock()` domain methods
- Write integration test verifying optimistic locking behavior
- Commit: `feat: add Product document model with optimistic locking`

### Task 5: Product repository
- Create `ProductRepository` extending `MongoRepository`
- Add `findByProductId(String)` query method
- Write integration test with Testcontainers (MongoDB)
- Commit: `feat: add ProductRepository with MongoDB`

### Task 6: Inventory stock service
- Create `InventoryStockService` with `checkStock()`, `reserveStock()`, `releaseStock()`
- Handle `OptimisticLockingFailureException` — return false on conflict instead of throwing
- Create `StockEvent` document and `StockEventRepository` for audit trail
- Write unit tests for stock check, reservation success, reservation conflict, and release
- Commit: `feat: add InventoryStockService with optimistic locking`

### Task 7: gRPC contract definition
- Create `proto/inventory.proto` with `InventoryService` definition
- Define `CheckStockRequest/Response` and `ReserveStockRequest/Response` messages
- Configure protobuf Maven plugin for code generation in both services
- Commit: `feat: add gRPC proto contract for inventory`

### Task 8: gRPC server implementation
- Create `InventoryGrpcServer` extending generated `InventoryServiceImplBase`
- Implement `checkStock()` and `reserveStock()` methods delegating to `InventoryStockService`
- Write integration test verifying gRPC responses
- Commit: `feat: implement gRPC server for inventory queries`

### Task 9: Inventory REST controller
- Create `InventoryController` with CRUD endpoints for products
- `GET /api/products` (paginated), `GET /api/products/{id}`, `POST /api/products`, `PUT /api/products/{id}/stock`
- Add Bean Validation on request DTOs
- Write integration tests with Testcontainers
- Commit: `feat: add REST API for product management`

---

## Phase 3: Order Service (PostgreSQL + gRPC Client)

### Task 10: Order data model
- Create `Order` entity with `@Entity`, UUID primary key, status enum, timestamps
- Create `OrderItem` entity with `@ManyToOne` relationship
- Create `OrderStatus` enum: PENDING, CONFIRMED, REJECTED, CANCELLED
- Write test verifying entity relationships
- Commit: `feat: add Order and OrderItem JPA entities`

### Task 11: Order repository
- Create `OrderRepository` extending `JpaRepository`
- Add `findByCustomerId()` and `findByStatus()` with pagination
- Add `@EntityGraph(attributePaths = "items")` on `findAll()` to avoid LazyInitializationException
- Write integration test with Testcontainers (PostgreSQL)
- Commit: `feat: add OrderRepository with eager item loading`

### Task 12: gRPC client
- Create `InventoryGrpcClient` using `@GrpcClient` and `InventoryServiceBlockingStub`
- Implement `checkStock()` and `reserveStock()` methods wrapping gRPC calls
- Write unit test with mocked stub
- Commit: `feat: add gRPC client for inventory communication`

### Task 13: Order service
- Create `OrderService` with `createOrder()`, `getOrder()`, `listOrders()`, `cancelOrder()`
- Order placement flow: gRPC stock check → gRPC reserve → save as PENDING → publish Kafka event
- Add Micrometer counters (orders created, rejected) and timer (creation duration)
- Write integration tests with mocked gRPC client and Testcontainers
- Commit: `feat: add OrderService with stock verification and metrics`

### Task 14: Order REST controller
- Create `OrderController` with endpoints: `POST /api/orders`, `GET /api/orders`, `GET /api/orders/{id}`, `PUT /api/orders/{id}/cancel`
- Add Bean Validation, exception handlers for OrderNotFound and InsufficientStock
- Write integration tests
- Commit: `feat: add REST API for order management`

---

## Phase 4: Kafka Event-Driven Communication

### Task 15: Kafka topic configuration
- Create `KafkaTopicConfig` in both services
- Define `order-events` (3 partitions) and `inventory-events` (3 partitions)
- Commit: `feat: configure Kafka topics`

### Task 16: Order event producer
- Create `OrderEventProducer` using `KafkaTemplate`
- Publish `OrderPlacedEvent` and `OrderCancelledEvent` to `order-events` topic
- Use orderId as message key for partition ordering
- Write unit test verifying event publishing
- Commit: `feat: add Kafka producer for order events`

### Task 17: Inventory event consumer + producer
- Create `OrderEventConsumer` in Inventory Service listening to `order-events`
- On OrderPlaced: reserve stock → publish StockUpdated to `inventory-events`
- On OrderCancelled: release stock
- Configure `spring.json.use.type.headers=false` to handle cross-service package differences
- Create `InventoryEventProducer` publishing to `inventory-events`
- Write integration tests with Testcontainers (Kafka)
- Commit: `feat: add Kafka consumer/producer for inventory events`

### Task 18: Order event consumer (confirmation)
- Create `InventoryEventConsumer` in Order Service listening to `inventory-events`
- On StockUpdated(success=true): update order to CONFIRMED
- On StockUpdated(success=false): update order to REJECTED
- Configure `spring.json.use.type.headers=false`
- Write integration test verifying order status transitions
- Commit: `feat: add Kafka consumer for order confirmation`

---

## Phase 5: Frontend

### Task 19: React project setup
- Initialize Vite + React + TypeScript project in `marketplace-frontend/`
- Install Material UI, Axios
- Configure API proxy to Order Service (8080) and Inventory Service (8081)
- Commit: `chore: initialize React frontend project`

### Task 20: Storefront page
- Product grid showing available products with stock levels
- Order form with quantity selection
- Order placement with success/error feedback
- Polling for order status updates (PENDING → CONFIRMED)
- Commit: `feat: add storefront page with order placement`

### Task 21: Admin dashboard
- Orders table with pagination, sorting, status badges
- Products table with stock management
- System metrics display (orders created, rejected)
- Commit: `feat: add admin dashboard`

---

## Phase 6: Testing & Observability

### Task 22: Integration tests with Testcontainers
- Order Service: PostgreSQL + Kafka containers, mocked gRPC client
- Inventory Service: MongoDB + Kafka containers
- Test complete flows: order creation, cancellation, stock reservation
- Commit: `test: add integration tests with Testcontainers`

### Task 23: Observability setup
- Micrometer counters: `orders.created.total`, `orders.rejected.total`
- Micrometer timer: `orders.creation.duration`
- Actuator endpoints: health, metrics, prometheus
- Verify metrics increment correctly in tests
- Commit: `feat: add observability metrics and health endpoints`

### Task 24: Data seeder (dev profile)
- Create `DataSeeder` in Inventory Service activated by `dev` profile
- Seed 5 sample products with realistic data
- Commit: `feat: add dev data seeder for inventory`
