# Marketplace Platform

A microservices-based marketplace built with Java 21, Spring Boot, Kafka, gRPC, and React — demonstrating modern distributed systems patterns.

## Architecture

```
                    ┌─────────────────┐
                    │  React Frontend │
                    │   (Vite + MUI)  │
                    └────────┬────────┘
                             │ REST
              ┌──────────────┴──────────────┐
              ▼                             ▼
   ┌─────────────────┐          ┌─────────────────────┐
   │  Order Service   │──gRPC──▶│  Inventory Service   │
   │  (Port 8080)     │         │  (Port 8081 / 9090)  │
   │  PostgreSQL      │         │  MongoDB              │
   └────────┬─────────┘         └──────────┬───────────┘
            │                              │
            └──────── Kafka ───────────────┘
              order-events ──▶
              ◀── inventory-events
```

**Order Service** handles customer orders with PostgreSQL for transactional data. **Inventory Service** manages products and stock with MongoDB and optimistic locking. They communicate synchronously via gRPC (stock checks) and asynchronously via Kafka (order confirmation saga).

## Tech Stack

| Technology | Purpose |
|---|---|
| Java 21 | Virtual threads for high-throughput concurrent I/O |
| Spring Boot 3.3 | Application framework with auto-configuration |
| Apache Kafka | Async event-driven communication (KRaft mode) |
| gRPC | Synchronous inter-service calls with Protocol Buffers |
| PostgreSQL 16 | Order storage (ACID transactions, JPA/Hibernate) |
| MongoDB 7 | Product/inventory storage (optimistic locking via `@Version`) |
| Micrometer | Business metrics (counters, timers) exposed via Prometheus |
| Testcontainers | Integration tests with real infrastructure |
| React + TypeScript | Admin dashboard and storefront (Vite, Material UI) |
| Docker Compose | Local development infrastructure |

## Prerequisites

- Java 21
- Maven
- Docker
- Node.js 18+

## Quick Start

```bash
# 1. Start infrastructure
docker compose up -d

# 2. Start Inventory Service (terminal 1)
cd inventory-service
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Start Order Service (terminal 2)
cd order-service
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 4. Start Frontend (terminal 3)
cd marketplace-frontend
npm install
npm run dev
```

Open http://localhost:5173 — the storefront and admin dashboard are ready.

## API Endpoints

### Order Service (port 8080)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/orders` | Place a new order |
| `GET` | `/api/orders` | List all orders (paginated) |
| `GET` | `/api/orders/{id}` | Get order details |
| `PUT` | `/api/orders/{id}/cancel` | Cancel an order |

### Inventory Service (port 8081)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/products` | List all products (paginated) |
| `GET` | `/api/products/{id}` | Get product details |
| `POST` | `/api/products` | Create a product |
| `PUT` | `/api/products/{id}/stock` | Update stock quantity |

### Health & Metrics

- `GET /actuator/health` — Health check (both services)
- `GET /actuator/prometheus` — Prometheus metrics (both services)

## Key Patterns

- **Choreography Saga** — Order placement spans two services via Kafka events (PENDING → CONFIRMED/REJECTED)
- **Optimistic Locking** — MongoDB `@Version` prevents overselling during concurrent stock reservations
- **Polyglot Persistence** — PostgreSQL for relational order data, MongoDB for flexible product documents
- **Virtual Threads** — Java 21 lightweight threads handle blocking I/O without reactive complexity
- **Key-Based Partitioning** — Kafka message keys ensure ordering guarantees per order

## Order Lifecycle

```
POST /api/orders
       │
       ▼
  gRPC: Check stock ──▶ Inventory Service
       │
       ▼
  gRPC: Reserve stock ──▶ Inventory Service
       │
       ▼
  Save order (PENDING)
       │
       ▼
  Kafka: OrderPlaced ──────────▶ Inventory Service
                                     │
                                     ▼
                              Confirm reservation
                                     │
                                     ▼
  Kafka: StockUpdated ◀────────── success/fail
       │
       ▼
  Update order → CONFIRMED or REJECTED
```

## Testing

```bash
# Run all tests (requires Docker for Testcontainers)
mvn test

# Single module
mvn test -pl order-service
mvn test -pl inventory-service

# Single test
mvn test -pl order-service -Dtest=OrderControllerTest#shouldCreateOrder
```

## Project Structure

```
marketplace-platform/
├── order-service/          # Order management microservice
├── inventory-service/      # Product & stock microservice
├── marketplace-frontend/   # React admin dashboard + storefront
├── proto/                  # Shared gRPC .proto definitions
├── docker-compose.yml      # PostgreSQL, MongoDB, Kafka
├── .specify/               # Spec Kit spec-driven development files
└── docs/                   # Explanation documents & study materials
```

## Documentation

| Document | Description |
|---|---|
| [Spring Boot Explained](docs/springboot-explained.md) | DI, annotations, layered architecture, design patterns |
| [gRPC Explained](docs/grpc-explained.md) | Protocol Buffers, stubs, server/client flow |
| [Kafka Explained](docs/kafka-explained.md) | Topics, partitions, consumers, KRaft mode |
| [Virtual Threads Explained](docs/virtual-threads-explained.md) | Park/unmount, throughput, vs reactive |
| [Concurrency Explained](docs/concurrency-explained.md) | Race conditions, optimistic locking, saga |
| [Spec Kit Explained](docs/speckit-explained.md) | AI-first spec-driven development |
| [How the App Works](docs/how-the-app-works.md) | Visual guide with diagrams |
| [Order Service Explained](docs/order-service-explained.md) | Detailed service walkthrough |
| [Inventory Service Explained](docs/inventory-service-explained.md) | Detailed service walkthrough |
