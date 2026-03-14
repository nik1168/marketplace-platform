# Marketplace Platform — Architecture Diagrams

## 1. High-Level Architecture

```mermaid
graph TB
    Client["Client (curl / frontend)"]

    subgraph Order Service - Port 8080
        REST["REST API<br/>/api/orders"]
        OS["OrderService<br/>(Business Logic)"]
        OC["OrderController"]
        OP["OrderEventProducer<br/>(Kafka Producer)"]
        IC["InventoryEventConsumer<br/>(Kafka Consumer)"]
        GC["InventoryGrpcClient"]
        PG[("PostgreSQL<br/>orders, order_items")]
    end

    subgraph Inventory Service - Port 8081
        GS["InventoryGrpcServer<br/>(gRPC on port 9090)"]
        IS["InventoryStockService<br/>(Business Logic)"]
        OEC["OrderEventConsumer<br/>(Kafka Consumer)"]
        IP["InventoryEventProducer<br/>(Kafka Producer)"]
        MG[("MongoDB<br/>products, stock_events")]
    end

    subgraph Message Broker
        KF1[["Kafka Topic<br/>order-events"]]
        KF2[["Kafka Topic<br/>inventory-events"]]
    end

    Client -->|"HTTP POST/GET/PUT"| REST
    REST --> OC --> OS
    OS --> PG
    OS -->|"checkStock / reserveStock"| GC
    GC -->|"gRPC (protobuf over HTTP/2)"| GS
    GS --> IS --> MG
    OS --> OP -->|"OrderPlaced / OrderCancelled"| KF1
    KF1 -->|"consumes"| OEC --> IS
    IS --> IP -->|"StockUpdated"| KF2
    KF2 -->|"consumes"| IC --> OS

    style Client fill:#e1f5fe
    style PG fill:#fff3e0
    style MG fill:#e8f5e9
    style KF1 fill:#fce4ec
    style KF2 fill:#fce4ec
```

## 2. Order Creation Flow (Happy Path)

```mermaid
sequenceDiagram
    participant C as Client
    participant OC as OrderController
    participant OS as OrderService
    participant GC as InventoryGrpcClient
    participant GS as InventoryGrpcServer
    participant IS as InventoryStockService
    participant PG as PostgreSQL
    participant MG as MongoDB
    participant K1 as Kafka (order-events)
    participant K2 as Kafka (inventory-events)
    participant OEC as OrderEventConsumer
    participant IC as InventoryEventConsumer

    Note over C,IC: PHASE 1: Synchronous - Order Placement (REST + gRPC)

    C->>+OC: POST /api/orders<br/>{ customerId, items[] }
    OC->>+OS: createOrder(request)

    loop For each item in the order
        OS->>+GC: checkStock(productId, quantity)
        GC->>+GS: gRPC: CheckStock
        GS->>+IS: checkStock()
        IS->>MG: findByProductId()
        MG-->>IS: Product
        IS-->>-GS: available = true
        GS-->>-GC: CheckStockResponse
        GC-->>-OS: true
    end

    OS->>PG: save(order) [status = PENDING]
    PG-->>OS: saved order with UUID

    loop For each item in the order
        OS->>GC: reserveStock(productId, qty, orderId)
        GC->>GS: gRPC: ReserveStock
        GS->>IS: reserveStock()
        IS->>MG: update product (reservedStock += qty)
        IS->>MG: save StockEvent(RESERVED)
        GS-->>GC: success = true
        GC-->>OS: true
    end

    OS->>OS: ordersCreatedCounter.increment()
    OS-->>-OC: Order (PENDING)
    OC-->>-C: 201 Created { id, status: PENDING }

    Note over C,IC: PHASE 2: Asynchronous - Event-Driven Stock Confirmation (Kafka)

    OS->>K1: publish OrderPlacedEvent

    K1-->>OEC: consume OrderPlacedEvent
    OEC->>IS: reserveStock() [idempotent]
    IS->>MG: update stock
    OEC->>K2: publish StockUpdatedEvent(success=true)

    K2-->>IC: consume StockUpdatedEvent
    IC->>OS: updateOrderStatus(CONFIRMED)
    OS->>PG: update order status → CONFIRMED
```

## 3. Order Creation Flow (Insufficient Stock)

```mermaid
sequenceDiagram
    participant C as Client
    participant OC as OrderController
    participant OS as OrderService
    participant GC as InventoryGrpcClient
    participant GS as InventoryGrpcServer
    participant IS as InventoryStockService
    participant MG as MongoDB

    C->>+OC: POST /api/orders<br/>{ productId: "prod-1", qty: 9999 }
    OC->>+OS: createOrder(request)
    OS->>+GC: checkStock("prod-1", 9999)
    GC->>+GS: gRPC: CheckStock
    GS->>+IS: checkStock()
    IS->>MG: findByProductId("prod-1")
    MG-->>IS: Product (currentStock: 500)
    IS-->>-GS: available = false
    GS-->>-GC: CheckStockResponse(available=false)
    GC-->>-OS: false

    OS->>OS: ordersRejectedCounter.increment()
    OS-->>OC: throw InsufficientStockException
    OC-->>-C: 422 Unprocessable Entity<br/>"Insufficient stock for product: prod-1"

    Note over C,MG: No order is saved, no Kafka events are published
```

## 4. Order Cancellation Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant OC as OrderController
    participant OS as OrderService
    participant PG as PostgreSQL
    participant K1 as Kafka (order-events)
    participant OEC as OrderEventConsumer
    participant IS as InventoryStockService
    participant MG as MongoDB
    participant K2 as Kafka (inventory-events)
    participant IC as InventoryEventConsumer

    C->>+OC: PUT /api/orders/{id}/cancel
    OC->>+OS: cancelOrder(orderId)
    OS->>PG: findById(orderId)
    PG-->>OS: Order (status = CONFIRMED)
    OS->>OS: validate: status is PENDING or CONFIRMED
    OS->>PG: save(order) [status → CANCELLED]
    OS->>K1: publish OrderCancelledEvent
    OS-->>-OC: Order (CANCELLED)
    OC-->>-C: 200 OK { status: CANCELLED }

    Note over K1,MG: Async: Inventory releases reserved stock

    K1-->>OEC: consume OrderCancelledEvent
    OEC->>IS: releaseStock(productId, qty, orderId)
    IS->>MG: update product (reservedStock -= qty)
    IS->>MG: save StockEvent(RELEASED)
    OEC->>K2: publish StockUpdatedEvent
```

## 5. Data Models

```mermaid
erDiagram
    ORDERS ||--o{ ORDER_ITEMS : "has many"
    ORDERS {
        UUID id PK
        String customer_id
        OrderStatus status
        BigDecimal total_amount
        Instant created_at
        Instant updated_at
    }
    ORDER_ITEMS {
        UUID id PK
        UUID order_id FK
        String product_id
        int quantity
        BigDecimal unit_price
    }

    PRODUCTS ||--o{ STOCK_EVENTS : "has many"
    PRODUCTS {
        String id PK
        String product_id UK
        String name
        String sku
        String category
        int current_stock
        int reserved_stock
        Instant last_updated
        Long version
    }
    STOCK_EVENTS {
        String id PK
        String product_id
        StockEventType event_type
        int quantity
        String order_id
        Instant timestamp
    }
```

## 6. Order Status State Machine

```mermaid
stateDiagram-v2
    [*] --> PENDING: Order created<br/>(POST /api/orders)

    PENDING --> CONFIRMED: StockUpdated event<br/>(success = true)
    PENDING --> REJECTED: StockUpdated event<br/>(success = false)
    PENDING --> CANCELLED: Cancel request<br/>(PUT /orders/{id}/cancel)

    CONFIRMED --> SHIPPED: Future: shipping integration
    CONFIRMED --> CANCELLED: Cancel request<br/>(PUT /orders/{id}/cancel)

    REJECTED --> [*]
    CANCELLED --> [*]
    SHIPPED --> [*]
```

## 7. Virtual Threads — Why They Matter Here

```mermaid
graph LR
    subgraph "Without Virtual Threads (Platform Threads)"
        PT1["Thread-1: REST request"] -->|"BLOCKED waiting<br/>for gRPC response"| PT1B["...idle..."]
        PT2["Thread-2: REST request"] -->|"BLOCKED waiting<br/>for DB query"| PT2B["...idle..."]
        PT3["Thread-3: REST request"] -->|"BLOCKED waiting<br/>for Kafka send"| PT3B["...idle..."]
        PTN["Thread pool exhausted!<br/>New requests rejected ❌"]
    end

    subgraph "With Virtual Threads (Java 21)"
        VT1["VThread-1: REST request"] -->|"Waiting for gRPC?<br/>Unmounts from carrier"| VT1B["Carrier thread<br/>picks up VThread-4"]
        VT2["VThread-2: REST request"] -->|"Waiting for DB?<br/>Unmounts from carrier"| VT2B["Carrier thread<br/>picks up VThread-5"]
        VT3["1000+ virtual threads<br/>all running concurrently ✅"]
    end

    style PTN fill:#ffcdd2
    style VT3 fill:#c8e6c9
```

## 8. Kafka Topics and Partitioning

```mermaid
graph TB
    subgraph "order-events topic (3 partitions)"
        P0["Partition 0<br/>Orders: A, D, G..."]
        P1["Partition 1<br/>Orders: B, E, H..."]
        P2["Partition 2<br/>Orders: C, F, I..."]
    end

    OP["OrderEventProducer<br/>(Order Service)"]
    OP -->|"key = orderId<br/>(same order always<br/>goes to same partition)"| P0
    OP --> P1
    OP --> P2

    subgraph "inventory-service-group (3 consumers)"
        C0["Consumer 0"]
        C1["Consumer 1"]
        C2["Consumer 2"]
    end

    P0 --> C0
    P1 --> C1
    P2 --> C2

    Note["Key-based partitioning ensures<br/>all events for one order are<br/>processed in order (no race conditions)"]

    style Note fill:#fff9c4
```

## 9. gRPC vs REST vs Kafka — When to Use Each

```mermaid
graph TB
    subgraph "Communication Patterns in This Project"
        REST["REST (HTTP/JSON)<br/>━━━━━━━━━━━━━<br/>Client → Order Service<br/><br/>WHY: Standard web API,<br/>easy to test with curl,<br/>human-readable JSON"]

        GRPC["gRPC (HTTP/2 + Protobuf)<br/>━━━━━━━━━━━━━<br/>Order Service → Inventory Service<br/>(CheckStock, ReserveStock)<br/><br/>WHY: Internal service-to-service,<br/>binary = faster than JSON,<br/>typed contract via .proto"]

        KAFKA["Kafka (Async Events)<br/>━━━━━━━━━━━━━<br/>OrderPlaced → Stock reservation<br/>StockUpdated → Order confirmation<br/><br/>WHY: Decoupled, resilient,<br/>order doesn't wait for<br/>inventory processing"]
    end

    REST ---|"Sync,<br/>external"| GRPC
    GRPC ---|"Sync,<br/>internal"| KAFKA

    style REST fill:#e3f2fd
    style GRPC fill:#f3e5f5
    style KAFKA fill:#fce4ec
```
