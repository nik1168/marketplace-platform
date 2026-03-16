# The Order Service — Explained

## What is it?

If the Inventory Service is the **warehouse manager**, the Order Service is the **cashier and customer service desk**. It's the part of the system that customers interact with. Its job is to:

- Take new orders from customers
- Keep track of every order and its current status
- Handle cancellations
- Report on how the business is doing (metrics)

The Order Service is the **front door** of the marketplace. Every request from the storefront or admin dashboard goes through it first.

---

## What can it do?

The Order Service exposes 4 actions through its REST API:

| Action | HTTP Method | URL | What it does |
|--------|------------|-----|-------------|
| Place an order | `POST` | `/api/orders` | Customer submits their cart, gets back an order |
| View an order | `GET` | `/api/orders/{id}` | Look up a specific order by its ID |
| List all orders | `GET` | `/api/orders` | See all orders (with pagination) |
| Cancel an order | `PUT` | `/api/orders/{id}/cancel` | Cancel a pending or confirmed order |

```mermaid
graph LR
    subgraph "Order Service — The Cashier"
        A["Place Order<br/>POST /api/orders"]
        B["View Order<br/>GET /api/orders/{id}"]
        C["List Orders<br/>GET /api/orders"]
        D["Cancel Order<br/>PUT /api/orders/{id}/cancel"]
    end

    Customer["Customer<br/>(Storefront)"] --> A
    Customer --> B
    Admin["Admin<br/>(Dashboard)"] --> C
    Admin --> D
    Customer --> D

    style A fill:#c8e6c9
    style B fill:#e3f2fd
    style C fill:#e3f2fd
    style D fill:#ffcdd2
```

---

## The Life of an Order

Every order goes through a series of **statuses**, like a package being tracked in the mail. Here are all the possible statuses:

| Status | Color | Meaning |
|--------|-------|---------|
| **PENDING** | Orange | "We got your order, checking with the warehouse..." |
| **CONFIRMED** | Green | "The warehouse confirmed your items are reserved!" |
| **REJECTED** | Red | "Sorry, we don't have enough stock" |
| **SHIPPED** | Blue | "Your order is on the way!" (future feature) |
| **CANCELLED** | Grey | "This order was cancelled" |

Not every transition is allowed. You can't go from SHIPPED back to PENDING, for example. Here are the valid paths:

```mermaid
stateDiagram-v2
    [*] --> PENDING: Customer clicks<br/>"Place Order"

    PENDING --> CONFIRMED: Warehouse confirms<br/>stock is reserved
    PENDING --> REJECTED: Warehouse says<br/>"not enough stock"
    PENDING --> CANCELLED: Customer cancels<br/>before confirmation

    CONFIRMED --> SHIPPED: Items shipped<br/>(future feature)
    CONFIRMED --> CANCELLED: Customer cancels<br/>after confirmation

    REJECTED --> [*]: Terminal state<br/>(order is done)
    CANCELLED --> [*]: Terminal state<br/>(order is done)
    SHIPPED --> [*]: Terminal state<br/>(order is done)

    note right of PENDING: Most orders spend<br/>only 1-3 seconds here
    note right of CANCELLED: Cancelling releases<br/>reserved stock
```

Think of it like ordering food at a restaurant:
- **PENDING** = You placed your order, the waiter is checking with the kitchen
- **CONFIRMED** = The kitchen says "yes, we can make that!"
- **REJECTED** = "Sorry, we're out of that dish"
- **CANCELLED** = You changed your mind and cancelled
- **SHIPPED** = Your food is being brought to your table

---

## How Placing an Order Works — Step by Step

This is the most complex operation in the system. Here's what happens when a customer clicks "Place Order":

### The Short Version

1. Customer sends their cart to the Order Service
2. Order Service calls the warehouse (Inventory Service) to check stock
3. If stock is available, reserve it and save the order as PENDING
4. Drop a message in the mailbox (Kafka) — "new order placed!"
5. Warehouse picks up the message, confirms, drops a reply — "all good!"
6. Order Service reads the reply, updates order to CONFIRMED
7. Customer's screen refreshes and shows green "Confirmed" badge

### The Detailed Version

```mermaid
sequenceDiagram
    participant User as Customer
    participant FE as Frontend
    participant OC as Order Controller<br/>(REST API)
    participant OS as Order Service<br/>(Business Logic)
    participant GC as gRPC Client
    participant INV as Inventory Service
    participant DB as PostgreSQL
    participant KP as Kafka Producer
    participant K as Kafka

    Note over User,K: PHASE 1: The customer is waiting (synchronous, ~200ms)

    User->>FE: Clicks "Place Order"
    FE->>OC: POST /api/orders<br/>{ customerId, items[] }
    OC->>OC: Validate request<br/>(are fields filled in?)
    OC->>OS: createOrder(request)

    rect rgb(232, 245, 233)
        Note over OS,INV: Step 1: Ask the warehouse "Do you have this?"
        loop For each item in the cart
            OS->>GC: checkStock(productId, quantity)
            GC->>INV: gRPC call (binary, fast)
            INV-->>GC: Yes, available!
            GC-->>OS: true
        end
    end

    rect rgb(227, 242, 253)
        Note over OS,DB: Step 2: Save the order
        OS->>OS: Create Order object<br/>Add items, calculate total
        OS->>DB: INSERT INTO orders (...)<br/>INSERT INTO order_items (...)
        DB-->>OS: Saved! ID = abc-123
    end

    rect rgb(255, 243, 224)
        Note over OS,INV: Step 3: Tell the warehouse "Hold these for me"
        loop For each item in the cart
            OS->>GC: reserveStock(productId, qty, orderId)
            GC->>INV: gRPC call
            INV-->>GC: Reserved!
            GC-->>OS: true
        end
    end

    rect rgb(252, 228, 236)
        Note over OS,K: Step 4: Drop message in Kafka mailbox
        OS->>KP: publishOrderPlaced(order)
        KP->>K: Send to "order-events" topic
    end

    OS->>OS: Increment metrics counter
    OS-->>OC: Order (status: PENDING)
    OC-->>FE: 201 Created { id, status: PENDING }
    FE-->>User: "Order placed!" with orange PENDING badge
```

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant ORD as Order Service
    participant K1 as Kafka<br/>(order-events)
    participant INV as Inventory Service
    participant K2 as Kafka<br/>(inventory-events)
    participant IC as Inventory Consumer<br/>(in Order Service)

    Note over FE,IC: PHASE 2: Behind the scenes (asynchronous, ~1-3 seconds)

    K1-->>INV: Inventory Service picks up<br/>OrderPlaced message
    INV->>INV: Confirms stock reservation
    INV->>K2: Publishes StockUpdated<br/>(success=true)

    K2-->>IC: Order Service picks up<br/>StockUpdated message
    IC->>ORD: updateOrderStatus(CONFIRMED)
    ORD->>ORD: UPDATE orders<br/>SET status='CONFIRMED'

    Note over FE,ORD: Meanwhile, the frontend is checking every 3 seconds...
    loop Every 3 seconds
        FE->>ORD: GET /api/orders/abc-123
        ORD-->>FE: { status: PENDING }
    end
    FE->>ORD: GET /api/orders/abc-123
    ORD-->>FE: { status: CONFIRMED }
    FE->>FE: Badge turns green!
```

---

## What Happens When Things Go Wrong?

The Order Service handles several failure scenarios gracefully:

### Scenario 1: Not Enough Stock

```mermaid
sequenceDiagram
    participant User as Customer
    participant OS as Order Service
    participant INV as Inventory Service

    User->>OS: "I want 9999 keyboards"
    OS->>INV: checkStock("keyboard", 9999)
    INV-->>OS: Not available! (only 300 in stock)
    OS->>OS: Throw InsufficientStockException
    OS->>OS: Increment "rejected" counter
    OS-->>User: 422 Error:<br/>"Insufficient stock for product: prod-2"

    Note over User,INV: No order is saved<br/>No stock is reserved<br/>No Kafka messages sent
```

### Scenario 2: Order Not Found

```mermaid
sequenceDiagram
    participant User as Customer
    participant OS as Order Service
    participant DB as PostgreSQL

    User->>OS: GET /api/orders/nonexistent-id
    OS->>DB: SELECT * FROM orders WHERE id = ?
    DB-->>OS: No rows found
    OS->>OS: Throw OrderNotFoundException
    OS-->>User: 404 Error:<br/>"Order not found: nonexistent-id"
```

### Scenario 3: Cancelling a Shipped Order

```mermaid
sequenceDiagram
    participant User as Customer
    participant OS as Order Service
    participant DB as PostgreSQL

    User->>OS: PUT /api/orders/abc-123/cancel
    OS->>DB: SELECT * FROM orders WHERE id = ?
    DB-->>OS: Order (status: SHIPPED)
    OS->>OS: Check: is status PENDING or CONFIRMED?
    OS->>OS: No! It's SHIPPED.<br/>Throw IllegalStateException
    OS-->>User: 409 Conflict:<br/>"Cannot cancel order in status: SHIPPED"
```

### Scenario 4: Successful Cancellation

```mermaid
sequenceDiagram
    participant User as Customer
    participant OS as Order Service
    participant DB as PostgreSQL
    participant K as Kafka
    participant INV as Inventory Service

    User->>OS: PUT /api/orders/abc-123/cancel
    OS->>DB: SELECT * FROM orders WHERE id = ?
    DB-->>OS: Order (status: CONFIRMED)
    OS->>OS: Status is CONFIRMED — cancellation allowed!
    OS->>DB: UPDATE orders SET status = 'CANCELLED'
    OS->>K: Publish OrderCancelledEvent
    OS-->>User: 200 OK { status: CANCELLED }

    Note over K,INV: Later, asynchronously...
    K-->>INV: Inventory picks up cancellation
    INV->>INV: Release reserved stock<br/>(put items back on shelf)
```

---

## Where Does the Data Live?

The Order Service uses **PostgreSQL**, a relational database. Relational databases are great for orders because:
- Orders have a clear structure (ID, customer, items, status)
- We need **transactions** — if saving an order item fails, the whole order should be rolled back
- We need to query orders by customer, status, date, etc.

The data is stored in two tables:

```mermaid
erDiagram
    ORDERS ||--o{ ORDER_ITEMS : "contains"

    ORDERS {
        UUID id PK "Unique identifier (auto-generated)"
        String customer_id "Who placed the order"
        String status "PENDING, CONFIRMED, etc."
        Decimal total_amount "Sum of all item subtotals"
        Timestamp created_at "When the order was placed"
        Timestamp updated_at "Last time the order changed"
    }

    ORDER_ITEMS {
        UUID id PK "Unique identifier (auto-generated)"
        UUID order_id FK "Which order this belongs to"
        String product_id "Which product (references Inventory)"
        int quantity "How many units"
        Decimal unit_price "Price per unit"
    }
```

**Example data:**

**Orders table:**
| id | customer_id | status | total_amount | created_at |
|----|------------|--------|-------------|-----------|
| abc-123 | customer-1 | CONFIRMED | $159.97 | 2026-03-14 14:30:00 |
| def-456 | customer-2 | PENDING | $29.99 | 2026-03-14 14:35:00 |

**Order Items table:**
| id | order_id | product_id | quantity | unit_price |
|----|---------|-----------|----------|-----------|
| item-1 | abc-123 | prod-1 | 2 | $29.99 |
| item-2 | abc-123 | prod-3 | 2 | $49.99 |
| item-3 | def-456 | prod-1 | 1 | $29.99 |

Notice how `order_id` in the items table points back to the orders table — this is how we know which items belong to which order. This is called a **foreign key** relationship.

---

## Why PostgreSQL and Not MongoDB?

Good question! The Inventory Service uses MongoDB, so why does the Order Service use PostgreSQL?

| Feature | PostgreSQL (Order Service) | MongoDB (Inventory Service) |
|---------|--------------------------|---------------------------|
| **Data shape** | Structured, predictable | Flexible, document-based |
| **Transactions** | Strong ACID transactions | Limited transaction support |
| **Relationships** | Great for orders + items | Less natural for relationships |
| **Best for** | "I need to make sure these 3 things all happen together or none of them do" | "I need to store and retrieve documents quickly" |

Orders **must** be transactional. When you place an order, the system needs to save the order AND all its items together. If saving item #3 fails, items #1 and #2 should also be rolled back — you don't want a half-saved order. PostgreSQL guarantees this with **ACID transactions**.

The Inventory Service uses MongoDB because products are self-contained documents — you rarely need to join products with other tables, and MongoDB's flexible schema makes it easy to add new product fields without database migrations.

**Using both databases in one project is called polyglot persistence** — choosing the right database for each job rather than forcing everything into one.

---

## How the Order Service Watches Itself (Observability)

The Order Service tracks three metrics to monitor how the business and the system are performing:

```mermaid
graph TB
    subgraph "Metrics — The Dashboard Gauges"
        C1["orders.created.total<br/>━━━━━━━━━━━━━━<br/>How many orders<br/>have been placed?<br/><br/>A counter that only goes up.<br/>Currently: 47 orders"]
        C2["orders.rejected.total<br/>━━━━━━━━━━━━━━<br/>How many orders<br/>were rejected?<br/><br/>A sudden spike here means<br/>something is wrong with stock.<br/>Currently: 3 rejections"]
        C3["orders.creation.duration<br/>━━━━━━━━━━━━━━<br/>How long does it take<br/>to place an order?<br/><br/>If this number climbs,<br/>the system is getting slow.<br/>Currently: avg 180ms"]
    end

    style C1 fill:#c8e6c9
    style C2 fill:#ffcdd2
    style C3 fill:#e3f2fd
```

Think of these like the dashboard of a car:
- **Orders created** = odometer (total distance traveled)
- **Orders rejected** = warning light (something might be wrong)
- **Creation duration** = speedometer (how fast things are running)

These metrics are available at `http://localhost:8080/actuator/prometheus` and can be plugged into monitoring tools like Grafana to create real-time dashboards and alerts.

---

## The Role of Transactions

One of the most important concepts in the Order Service is **database transactions**. Here's a non-technical way to understand them:

Imagine you're at a bank transferring $100 from checking to savings. Two things need to happen:
1. Subtract $100 from checking
2. Add $100 to savings

What if the system crashes after step 1 but before step 2? You'd lose $100! A transaction prevents this — it says: "Either BOTH of these things happen, or NEITHER of them does."

In our Order Service, creating an order involves:
1. Insert a row in the `orders` table
2. Insert rows in the `order_items` table (one per item)
3. Call the Inventory Service to reserve stock

If step 2 fails halfway through, the transaction rolls back step 1 automatically. No half-created orders.

```mermaid
graph TB
    subgraph "Without Transaction (Dangerous)"
        A1["1. Save order"] -->|"Success"| A2["2. Save item 1"]
        A2 -->|"Success"| A3["3. Save item 2"]
        A3 -->|"CRASH!"| A4["Item 2 not saved!<br/>Order exists with only 1 item<br/>Customer is charged wrong amount"]
    end

    subgraph "With Transaction (Safe)"
        B1["1. BEGIN TRANSACTION"] --> B2["2. Save order"]
        B2 --> B3["3. Save item 1"]
        B3 --> B4["4. Save item 2"]
        B4 -->|"CRASH!"| B5["ROLLBACK!<br/>Everything is undone.<br/>Database is exactly as before."]
        B4 -->|"Success"| B6["5. COMMIT<br/>All changes saved at once"]
    end

    style A4 fill:#ffcdd2
    style B5 fill:#fff3e0
    style B6 fill:#c8e6c9
```

---

## Virtual Threads — Why They Matter

The Order Service uses **Java 21 Virtual Threads**, a modern feature that makes the service handle many more requests at the same time.

### The Problem (without virtual threads)

When the Order Service calls the Inventory Service via gRPC, it has to **wait** for the response. During that wait, the thread (think of it as a worker) is doing nothing — just sitting idle.

Traditional Java servers have a limited number of threads (typically 200). If 200 customers place orders at the same time, all threads are busy waiting for gRPC responses, and customer #201 gets an error: "Server too busy."

### The Solution (with virtual threads)

Virtual threads are lightweight — Java can create **millions** of them instead of just 200. When a virtual thread is waiting for a gRPC response, Java automatically **parks** it and lets another virtual thread use the same underlying resources. It's like having a waiting room instead of blocking the doorway.

```mermaid
graph TB
    subgraph "Without Virtual Threads"
        T1["Thread 1: Processing order...<br/>waiting for gRPC response...<br/>BLOCKED (doing nothing)"]
        T2["Thread 2: Processing order...<br/>waiting for DB query...<br/>BLOCKED (doing nothing)"]
        T3["Thread 3: Processing order...<br/>waiting for Kafka send...<br/>BLOCKED (doing nothing)"]
        T200["... Thread 200: BLOCKED"]
        T201["Customer #201: Sorry,<br/>all threads busy!<br/>503 Service Unavailable"]
    end

    subgraph "With Virtual Threads (Java 21)"
        VT1["VThread 1: waiting for gRPC?<br/>→ Parked, costs almost nothing"]
        VT2["VThread 2: waiting for DB?<br/>→ Parked, costs almost nothing"]
        VT3["VThreads 3-10000:<br/>All running concurrently!<br/>No customer turned away"]
    end

    style T201 fill:#ffcdd2
    style VT3 fill:#c8e6c9
```

The best part? We get this for free with a single line of configuration:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

No code changes needed. Spring Boot automatically uses virtual threads for handling every request.

---

## How It Talks to Other Services

The Order Service is the **orchestrator** — it coordinates between the customer, the warehouse, and the message system:

```mermaid
graph TB
    subgraph "Order Service — The Orchestrator"
        CTRL["Controller<br/>(REST API)<br/>━━━━━━━━━━<br/>Receives HTTP requests<br/>from the frontend"]
        SVC["Service<br/>(Business Logic)<br/>━━━━━━━━━━<br/>Decides what to do<br/>with the request"]
        REPO["Repository<br/>(Database Access)<br/>━━━━━━━━━━<br/>Reads and writes<br/>orders in PostgreSQL"]
        GRPC["gRPC Client<br/>━━━━━━━━━━<br/>Calls the Inventory<br/>Service directly"]
        KAFKA_P["Kafka Producer<br/>━━━━━━━━━━<br/>Sends messages to<br/>other services"]
        KAFKA_C["Kafka Consumer<br/>━━━━━━━━━━<br/>Receives messages<br/>from other services"]
    end

    FE["Frontend"] -->|"HTTP/JSON"| CTRL
    CTRL --> SVC
    SVC --> REPO
    SVC --> GRPC
    SVC --> KAFKA_P
    KAFKA_C --> SVC

    REPO -->|"SQL"| PG["PostgreSQL"]
    GRPC -->|"gRPC (binary)"| INV["Inventory Service"]
    KAFKA_P -->|"Async message"| K1["Kafka<br/>(order-events)"]
    K2["Kafka<br/>(inventory-events)"] -->|"Async message"| KAFKA_C

    style CTRL fill:#e3f2fd
    style SVC fill:#fff3e0
    style REPO fill:#f3e5f5
    style GRPC fill:#e8f5e9
    style KAFKA_P fill:#fce4ec
    style KAFKA_C fill:#fce4ec
```

Notice how the **Service** layer is the brain — it coordinates everything. The Controller just receives requests and returns responses. The Repository, gRPC Client, and Kafka Producer/Consumer are the tools the Service uses to get work done.

---

## The Layered Architecture

The Order Service follows a common pattern called **layered architecture**. Each layer has a specific responsibility:

```mermaid
graph TB
    subgraph "Layer 1: Controller (the front desk)"
        L1["Receives HTTP requests<br/>Validates input<br/>Returns HTTP responses<br/>Handles errors (404, 422, 409)"]
    end

    subgraph "Layer 2: Service (the brain)"
        L2["Contains business rules<br/>Coordinates between layers<br/>Manages transactions<br/>Tracks metrics"]
    end

    subgraph "Layer 3: Repository (the filing cabinet)"
        L3["Reads/writes to the database<br/>Translates between Java objects and SQL<br/>Provides query methods"]
    end

    subgraph "Layer 3: External Clients (the phone/mailbox)"
        L4["gRPC Client: calls Inventory Service<br/>Kafka Producer: sends messages<br/>Kafka Consumer: receives messages"]
    end

    L1 -->|"Calls"| L2
    L2 -->|"Calls"| L3
    L2 -->|"Calls"| L4

    style L1 fill:#e3f2fd
    style L2 fill:#fff3e0
    style L3 fill:#f3e5f5
    style L4 fill:#e8f5e9
```

Why layers? **Separation of concerns.** Each layer only knows about the layer directly below it. The Controller doesn't know about PostgreSQL. The Service doesn't know about HTTP status codes. This makes the code easier to understand, test, and change.

---

## Key Source Files

| File | What it does |
|------|-------------|
| `order-service/controller/OrderController.java` | REST API endpoints — receives HTTP requests, returns responses |
| `order-service/service/OrderService.java` | Core business logic — create, get, cancel, update orders |
| `order-service/model/Order.java` | The order data structure — maps to the `orders` database table |
| `order-service/model/OrderItem.java` | The order item data structure — maps to `order_items` table |
| `order-service/model/OrderStatus.java` | The 5 possible order statuses (PENDING, CONFIRMED, etc.) |
| `order-service/repository/OrderRepository.java` | Database access — Spring auto-generates SQL queries |
| `order-service/grpc/InventoryGrpcClient.java` | Makes gRPC calls to the Inventory Service |
| `order-service/kafka/OrderEventProducer.java` | Sends messages to Kafka (OrderPlaced, OrderCancelled) |
| `order-service/kafka/InventoryEventConsumer.java` | Receives messages from Kafka (StockUpdated) |
| `order-service/config/MetricsConfig.java` | Defines the 3 monitoring metrics |
| `order-service/config/KafkaTopicConfig.java` | Creates the "order-events" Kafka topic |
| `order-service/config/WebConfig.java` | CORS configuration — allows the frontend to call the API |
