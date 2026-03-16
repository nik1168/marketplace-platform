# The Inventory Service — Explained

## What is it?

Think of the Inventory Service as the **warehouse manager** of our marketplace. Its only job is to know **what products exist**, **how many we have**, and **who has claimed what**.

Just like a real warehouse has a person who tracks what's on the shelves, this service is the single source of truth for stock levels.

## What can it do?

The Inventory Service does 4 things:

1. **Show all products** — "Here's everything we sell and how much we have"
2. **Check stock** — "Do we have enough of product X to fill this order?"
3. **Reserve stock** — "Hold 3 units of product X for order #123 — don't let anyone else take them"
4. **Release stock** — "Order #123 was cancelled — put those 3 units back on the shelf"

```mermaid
graph LR
    subgraph "Inventory Service Capabilities"
        A["Show All Products"]
        B["Check Stock"]
        C["Reserve Stock"]
        D["Release Stock"]
    end

    Frontend["Storefront UI"] -->|"GET /api/products"| A
    OS["Order Service"] -->|"gRPC: CheckStock"| B
    OS -->|"gRPC: ReserveStock"| C
    OS -->|"Kafka: OrderCancelled"| D

    style A fill:#e3f2fd
    style B fill:#e8f5e9
    style C fill:#fff3e0
    style D fill:#fce4ec
```

## How Does the Storefront Use It?

When you open the storefront at `localhost:5173`, here's what happens step by step:

### Step 1 — Loading the Product Catalog

The frontend calls the Inventory Service and asks: "Give me all your products." The service responds with a list like:

| Product | Price | In Stock | Reserved | Available |
|---------|-------|----------|----------|-----------|
| Wireless Mouse | $29.99 | 500 | 0 | 500 |
| Mechanical Keyboard | $79.99 | 300 | 0 | 300 |
| USB-C Hub | $49.99 | 200 | 0 | 200 |

The frontend uses this to render the product cards you see on screen. If a product has 0 available, the "Add to Cart" button is disabled.

```mermaid
sequenceDiagram
    participant User as User (Browser)
    participant FE as Frontend (React)
    participant INV as Inventory Service

    User->>FE: Opens storefront page
    FE->>INV: GET /api/products
    INV->>INV: Query MongoDB for all products
    INV-->>FE: List of products with stock levels
    FE->>FE: Render product cards
    FE-->>User: Shows catalog with stock info

    Note over FE: Products with 0 available<br/>stock show disabled buttons
```

### Step 2 — Placing an Order

When you click "Place Order", the **Order Service** takes over — but it immediately calls the Inventory Service behind the scenes to ask two questions:

- **"Do we have enough?"** (Check Stock) — Like calling the warehouse before promising a customer their order. If the answer is no, the order is rejected immediately.

- **"Hold it for me"** (Reserve Stock) — If stock is available, the Order Service tells the warehouse: "Set aside 3 units of this product for order #123." This prevents the situation where two people buy the last item at the same time.

```mermaid
sequenceDiagram
    participant User as User (Browser)
    participant FE as Frontend (React)
    participant ORD as Order Service
    participant INV as Inventory Service
    participant DB as MongoDB

    User->>FE: Clicks "Place Order"
    FE->>ORD: POST /api/orders { items: [...] }

    rect rgb(232, 245, 233)
        Note over ORD,INV: Phase 1: "Do we have enough?"
        ORD->>INV: gRPC CheckStock(prod-1, qty: 3)
        INV->>DB: Find product, check available stock
        DB-->>INV: currentStock: 500, reserved: 0
        INV->>INV: 500 - 0 = 500 available >= 3 needed
        INV-->>ORD: Yes, stock available!
    end

    rect rgb(255, 243, 224)
        Note over ORD,INV: Phase 2: "Hold it for me"
        ORD->>INV: gRPC ReserveStock(prod-1, qty: 3, order #123)
        INV->>DB: Update: reservedStock 0 -> 3
        INV->>DB: Log: "Reserved 3 units for order #123"
        INV-->>ORD: Reserved successfully!
    end

    ORD->>ORD: Save order with status PENDING
    ORD-->>FE: 201 Created { status: PENDING }
    FE-->>User: Shows "Order Placed!" with PENDING badge
```

### Step 3 — Confirmation (the async part)

After the initial reservation, something clever happens. The Order Service drops a message into a mailbox (Kafka) saying "Hey, I placed an order." The Inventory Service picks up that message, double-checks the reservation, and drops a reply in another mailbox saying "All good, stock is secured." The Order Service reads that reply and changes the order status from PENDING to CONFIRMED.

This is like a warehouse manager sending a confirmation email after physically pulling items off the shelf — the customer's order page refreshes and shows "Confirmed."

```mermaid
sequenceDiagram
    participant FE as Frontend (React)
    participant ORD as Order Service
    participant K1 as Kafka Mailbox<br/>(order-events)
    participant INV as Inventory Service
    participant K2 as Kafka Mailbox<br/>(inventory-events)

    Note over ORD: Order #123 just saved as PENDING

    rect rgb(252, 228, 236)
        Note over ORD,K1: Step A: Order Service drops a message
        ORD->>K1: "Hey, order #123 was just placed"
    end

    rect rgb(232, 245, 233)
        Note over K1,INV: Step B: Inventory Service reads the message
        K1-->>INV: Picks up the OrderPlaced message
        INV->>INV: Double-checks stock reservation
        INV->>K2: "All good, stock secured for order #123"
    end

    rect rgb(227, 242, 253)
        Note over K2,ORD: Step C: Order Service reads the reply
        K2-->>ORD: Picks up the StockUpdated message
        ORD->>ORD: Update order #123: PENDING -> CONFIRMED
    end

    Note over FE,ORD: Meanwhile, the frontend is polling...
    FE->>ORD: GET /api/orders/123 (every 3 seconds)
    ORD-->>FE: { status: CONFIRMED }
    FE->>FE: Status badge changes to green!
```

## Why the "Reserved" vs "Available" Distinction?

This is a real-world problem. Imagine this scenario:

- We have **5 keyboards** in stock
- Customer A adds 3 keyboards to their cart and clicks "Place Order"
- At the **same instant**, Customer B tries to order 4 keyboards

Without reservations, both orders might go through (the system thinks there are 5 available for both), and we'd promise 7 keyboards when we only have 5.

```mermaid
graph TB
    subgraph "WITHOUT Reservations (Broken)"
        S1["Stock: 5 keyboards"]
        A1["Customer A: order 3"] -->|"Checks: 5 >= 3? Yes!"| S1
        B1["Customer B: order 4"] -->|"Checks: 5 >= 4? Yes!"| S1
        S1 --> R1["Both orders accepted!<br/>But we promised 7 keyboards<br/>when we only have 5"]
    end

    style R1 fill:#ffcdd2
```

```mermaid
graph TB
    subgraph "WITH Reservations (Correct)"
        S2["Stock: 5 total"]
        A2["Customer A: order 3"] -->|"Check: 5 available >= 3?"| S2
        S2 -->|"Yes! Reserve 3"| S3["Stock: 5 total<br/>3 reserved<br/>2 available"]
        B2["Customer B: order 4"] -->|"Check: 2 available >= 4?"| S3
        S3 --> R2["Rejected! Only 2 available"]
    end

    style S3 fill:#c8e6c9
    style R2 fill:#ffcdd2
```

This is the same system airlines use when you're booking a seat — it's "held" for you while you complete checkout.

## Where Does the Data Live?

The Inventory Service stores its data in **MongoDB** (a database optimized for document-style data). Each product looks like this:

```json
{
  "productId": "prod-1",
  "name": "Wireless Mouse",
  "currentStock": 500,
  "reservedStock": 3,
  "version": 5
}
```

It also keeps a **history log** (called Stock Events) — every time stock is reserved, released, or confirmed, it records it. Think of it as the warehouse's activity ledger:

```
"Reserved 3 units of prod-1 for order #123 at 2:30 PM"
"Released 3 units of prod-1 for order #123 at 2:45 PM" (order was cancelled)
```

```mermaid
erDiagram
    PRODUCT {
        string productId "Unique product code (prod-1)"
        string name "Human-readable name"
        int currentStock "Total units in warehouse"
        int reservedStock "Units claimed by pending orders"
        int availableStock "currentStock minus reservedStock"
        long version "Safety counter for concurrent updates"
    }

    STOCK_EVENT {
        string productId "Which product was affected"
        string eventType "RESERVED, RELEASED, or CONFIRMED"
        int quantity "How many units"
        string orderId "Which order triggered this"
        datetime timestamp "When it happened"
    }

    PRODUCT ||--o{ STOCK_EVENT : "has history of"
```

## What's the "version" Field About?

This solves another real-world problem: **two things happening at the exact same time**.

Imagine two warehouse workers both read that there are 500 mice in stock. Worker A reserves 3 (sets it to 497). Worker B, who still thinks there are 500, reserves 5 (sets it to 495). Worker A's reservation just got lost!

The `version` field prevents this. Every time someone updates a product, the version number goes up. Before saving, the system checks: "Is the version still what I read earlier?" If someone else changed it in between, the save fails and it retries with fresh data. This is called **optimistic locking** — it "optimistically" assumes no conflict will happen, but catches it if it does.

```mermaid
sequenceDiagram
    participant WA as Worker A
    participant DB as Database
    participant WB as Worker B

    Note over DB: Product: stock=500, version=1

    WA->>DB: Read product (gets version=1)
    WB->>DB: Read product (gets version=1)

    WA->>DB: Update stock=497<br/>WHERE version=1
    DB->>DB: Version matches! Save it.<br/>Bump version to 2
    DB-->>WA: Success!

    WB->>DB: Update stock=495<br/>WHERE version=1
    DB->>DB: Version is 2 now, not 1!<br/>Someone else changed it!
    DB-->>WB: Rejected! Please retry.

    WB->>DB: Re-read product (gets version=2, stock=497)
    WB->>DB: Update stock=492<br/>WHERE version=2
    DB-->>WB: Success!

    Note over DB: Final: stock=492, version=3<br/>Both reservations accounted for!
```

## How Does It Talk to the Order Service?

The Inventory Service communicates with the Order Service in **two different ways**, each for a different purpose:

| Method | When | Why this method |
|--------|------|-----------------|
| **gRPC** (direct call) | Stock check & reservation during order placement | The Order Service needs an answer **right now** — the customer is waiting. gRPC is fast because it uses binary data instead of text. |
| **Kafka** (message queue) | Confirmation after order is placed | The customer already got their "Order Placed" response. The confirmation can happen in the background — no one is waiting. If the Inventory Service is temporarily down, the message waits in the queue and gets processed when it comes back. |

Think of it like this:
- **gRPC** = a phone call ("I need to know right now, is this in stock?")
- **Kafka** = a letter in a mailbox ("When you get a chance, confirm this reservation")

```mermaid
graph TB
    subgraph "Communication Methods"
        subgraph "gRPC — The Phone Call"
            ORD1["Order Service"] -->|"Binary data, very fast<br/>Customer is waiting!"| INV1["Inventory Service"]
            INV1 -->|"Instant response"| ORD1
            U1["Used for: CheckStock, ReserveStock"]
        end

        subgraph "Kafka — The Mailbox"
            ORD2["Order Service"] -->|"Drops a message"| K["Kafka<br/>(Message Queue)"]
            K -->|"Picks up when ready"| INV2["Inventory Service"]
            INV2 -->|"Drops a reply"| K2["Kafka<br/>(Message Queue)"]
            K2 -->|"Picks up when ready"| ORD3["Order Service"]
            U2["Used for: OrderPlaced, StockUpdated events"]
        end
    end

    style K fill:#fce4ec
    style K2 fill:#fce4ec
    style U1 fill:#fff9c4,stroke:none
    style U2 fill:#fff9c4,stroke:none
```

## The Complete Picture — Full Order Lifecycle

Here's everything that happens from the moment you click "Place Order" to the moment you see "Confirmed":

```mermaid
graph TB
    A["User clicks Place Order"] --> B["Frontend sends POST /api/orders<br/>to Order Service"]
    B --> C{"Order Service calls Inventory Service<br/>via gRPC: Is stock available?"}
    C -->|"No"| D["Order rejected immediately<br/>User sees error message"]
    C -->|"Yes"| E["Inventory Service reserves stock<br/>in MongoDB"]
    E --> F["Order Service saves order<br/>to PostgreSQL as PENDING"]
    F --> G["Order Service drops message<br/>in Kafka: OrderPlaced"]
    G --> H["Inventory Service picks up<br/>the message from Kafka"]
    H --> I["Inventory Service confirms<br/>the reservation is solid"]
    I --> J["Inventory Service drops reply<br/>in Kafka: StockUpdated"]
    J --> K["Order Service picks up the reply"]
    K --> L["Order Service updates order<br/>PENDING -> CONFIRMED"]
    L --> M["Frontend polls and sees<br/>status changed to CONFIRMED"]

    style A fill:#e3f2fd
    style D fill:#ffcdd2
    style F fill:#fff3e0
    style L fill:#c8e6c9
    style M fill:#c8e6c9
    style G fill:#fce4ec
    style J fill:#fce4ec
```

## Key Source Files

If you want to look at the actual code, here are the main files:

| File | What it does |
|------|-------------|
| `inventory-service/model/Product.java` | The product data structure with stock management methods |
| `inventory-service/model/StockEvent.java` | The activity ledger entries |
| `inventory-service/service/InventoryStockService.java` | Core business logic — check, reserve, release stock |
| `inventory-service/grpc/InventoryGrpcServer.java` | Handles "phone calls" (gRPC) from Order Service |
| `inventory-service/kafka/OrderEventConsumer.java` | Reads "letters" (Kafka messages) from Order Service |
| `inventory-service/kafka/InventoryEventProducer.java` | Sends "reply letters" back via Kafka |
| `inventory-service/controller/ProductController.java` | REST API for the frontend to list products |
| `inventory-service/config/DataSeeder.java` | Seeds 5 sample products on startup (dev mode only) |
