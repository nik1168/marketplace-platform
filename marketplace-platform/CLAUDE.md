# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

**Prerequisites:** Java 21, Maven, Docker

```bash
# Set Java 21 (macOS Homebrew)
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# Build all modules
cd marketplace-platform
mvn clean compile

# Start infrastructure
docker compose up -d

# Run Order Service (port 8080)
cd order-service && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Run Inventory Service (port 8081, gRPC on 9090)
cd inventory-service && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Testing

```bash
# All tests (uses Testcontainers — requires Docker)
mvn test

# Single module
mvn test -pl order-service
mvn test -pl inventory-service

# Single test class
mvn test -pl order-service -Dtest=OrderControllerTest

# Single test method
mvn test -pl order-service -Dtest=OrderControllerTest#shouldCreateOrder
```

**Testcontainers note:** Tests use `org.testcontainers.kafka.KafkaContainer` (not `org.testcontainers.containers.KafkaContainer`) because we use the `apache/kafka:3.7.0` image. Integration tests that load the full Spring context need both PostgreSQL/MongoDB and Kafka containers, plus `@MockBean InventoryGrpcClient` in order-service tests (since there's no real gRPC server in test).

## Architecture

Two Spring Boot microservices connected via Kafka (async) and gRPC (sync):

```
Client → REST → Order Service (8080) → gRPC → Inventory Service (8081/9090)
                      │                              ▲
                      └──── Kafka (order-events) ────┘
                                                     │
                      ┌──── Kafka (inventory-events) ┘
                      ▼
                Order Service (updates order status)
```

- **Order Service:** REST API (`/api/orders`), PostgreSQL, Kafka producer (order-events), Kafka consumer (inventory-events), gRPC client
- **Inventory Service:** MongoDB, gRPC server (CheckStock, ReserveStock), Kafka consumer (order-events), Kafka producer (inventory-events)

### Key flows
1. `POST /api/orders` → gRPC stock check → persist order → publish `OrderPlaced` to Kafka
2. Inventory Service consumes `OrderPlaced` → reserves stock → publishes `StockUpdated`
3. Order Service consumes `StockUpdated` → updates order to CONFIRMED or REJECTED

### Kafka topics
- `order-events` (3 partitions): OrderPlacedEvent, OrderCancelledEvent
- `inventory-events` (3 partitions): StockUpdatedEvent

### gRPC contract
Defined in `proto/inventory.proto`, generated stubs in `com.marketplace.inventory.grpc` package. Both services share the proto via `protoSourceRoot=${project.basedir}/../proto`.

## Key Configuration

- **Virtual threads:** `spring.threads.virtual.enabled=true` in both services
- **Kafka:** KRaft mode (no Zookeeper), external listener on port 29092
- **Observability:** Actuator endpoints at `/actuator/health,metrics,prometheus`. Custom Micrometer counters: `orders.created.total`, `orders.rejected.total`, `orders.creation.duration`
- **Dev profile:** Activates DataSeeder in Inventory Service (seeds 5 products)

## Project Structure

Multi-module Maven project. Parent POM at `marketplace-platform/pom.xml` manages versions for gRPC (1.64.0), protobuf (3.25.3), Testcontainers (1.20.1), and grpc-spring-boot-starter (3.1.0.RELEASE).
