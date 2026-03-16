# How the Marketplace App Works — Visual Guide

This document uses simple, friendly diagrams to explain every part of the application. No technical jargon — just pictures and plain language.

---

## The Big Picture

Our marketplace has three layers: what the user sees, the services that handle business logic, and the databases that store everything.

```mermaid
graph TB
    subgraph "🖥️ What the User Sees"
        SF["Storefront<br/>Browse products, add to cart,<br/>place orders, track status"]
        AD["Admin Dashboard<br/>View all orders, check inventory,<br/>cancel orders"]
    end

    subgraph "⚙️ The Services (the brains)"
        OS["Order Service<br/>Handles everything about orders<br/>Lives at port 8080"]
        IS["Inventory Service<br/>Handles everything about products & stock<br/>Lives at port 8081"]
    end

    subgraph "💾 Where Data Lives"
        PG["PostgreSQL<br/>Stores all orders"]
        MG["MongoDB<br/>Stores all products & stock"]
        KF["Kafka<br/>Passes messages between services"]
    end

    SF --> OS
    SF --> IS
    AD --> OS
    AD --> IS
    OS --> PG
    OS --> KF
    IS --> MG
    IS --> KF
    OS -.->|"gRPC (fast calls)"| IS

    style SF fill:#e3f2fd
    style AD fill:#e3f2fd
    style OS fill:#fff3e0
    style IS fill:#e8f5e9
    style PG fill:#f3e5f5
    style MG fill:#f3e5f5
    style KF fill:#fce4ec
```

---

## The Shopping Experience — What Happens When You Shop

### Step 1: Browsing Products

```mermaid
graph LR
    subgraph "1. You open the storefront"
        User["👤 You"] -->|"Open browser"| Browser["🌐 localhost:5173"]
    end

    subgraph "2. Frontend asks for products"
        Browser -->|"Hey, what products<br/>do you have?"| IS["📦 Inventory Service"]
    end

    subgraph "3. Inventory checks warehouse"
        IS -->|"Let me check<br/>the database"| MG["🗄️ MongoDB"]
        MG -->|"Here's everything!"| IS
    end

    subgraph "4. You see the catalog"
        IS -->|"5 products with<br/>prices and stock"| Browser
    end
```

```mermaid
graph TB
    subgraph "What you see on screen"
        P1["🖱️ Wireless Mouse<br/>$29.99<br/>500 in stock<br/>[Add to Cart]"]
        P2["⌨️ Mechanical Keyboard<br/>$79.99<br/>300 in stock<br/>[Add to Cart]"]
        P3["🔌 USB-C Hub<br/>$49.99<br/>200 in stock<br/>[Add to Cart]"]
        P4["🎧 Headphones<br/>$149.99<br/>150 in stock<br/>[Add to Cart]"]
        P5["📱 Phone Stand<br/>$19.99<br/>0 in stock<br/>[SOLD OUT]"]
    end

    style P1 fill:#e8f5e9
    style P2 fill:#e8f5e9
    style P3 fill:#e8f5e9
    style P4 fill:#e8f5e9
    style P5 fill:#ffcdd2
```

### Step 2: Adding Items to Cart

```mermaid
graph LR
    subgraph "Your cart (stored in browser only)"
        C1["🖱️ Wireless Mouse × 2<br/>$29.99 × 2 = $59.98"]
        C2["⌨️ Mechanical Keyboard × 1<br/>$79.99 × 1 = $79.99"]
        TOTAL["━━━━━━━━━━━━<br/>Total: $139.97"]
    end

    Note["Nothing has been sent to the<br/>server yet — the cart is just<br/>a list in your browser's memory"]

    style Note fill:#fff9c4,stroke:none
```

### Step 3: Placing the Order

This is where the magic happens. Here's a story version:

```mermaid
graph TB
    A["👤 You click 'Place Order'"] --> B["📨 Your cart is sent to<br/>the Order Service"]

    B --> C{"🤔 Order Service asks<br/>Inventory Service:<br/>'Do you have 2 mice<br/>and 1 keyboard?'"}

    C -->|"✅ Yes!"| D["📦 Inventory Service:<br/>'Setting aside 2 mice<br/>and 1 keyboard for you'"]
    C -->|"❌ No"| E["⚠️ 'Sorry, not enough<br/>stock available'"]

    D --> F["💾 Order Service saves<br/>the order to PostgreSQL<br/>Status: PENDING"]

    F --> G["📬 Order Service drops<br/>a message in Kafka:<br/>'New order placed!'"]

    G --> H["👤 You see: Order Placed!<br/>Status: 🟠 PENDING"]

    style A fill:#e3f2fd
    style E fill:#ffcdd2
    style H fill:#fff3e0
```

### Step 4: The Behind-the-Scenes Confirmation

While you're looking at the PENDING badge, things are happening in the background:

```mermaid
graph TB
    A["📬 Inventory Service picks up<br/>the 'order placed' message<br/>from Kafka"] --> B["📦 Inventory Service:<br/>'Let me double-check that<br/>reservation... yes, all good!'"]

    B --> C["📬 Inventory Service drops<br/>a reply in Kafka:<br/>'Stock confirmed for this order!'"]

    C --> D["📬 Order Service picks up<br/>the confirmation message"]

    D --> E["💾 Order Service updates<br/>the database:<br/>PENDING → CONFIRMED"]

    E --> F["🔄 Your browser checks<br/>for updates every 3 seconds..."]

    F --> G["👤 You see the badge<br/>change to: 🟢 CONFIRMED"]

    style A fill:#fce4ec
    style C fill:#fce4ec
    style G fill:#c8e6c9
```

### The Complete Journey in One Picture

```mermaid
graph LR
    subgraph "Phase 1: You're Waiting (~200ms)"
        A["👤 Click<br/>Place Order"] --> B["📋 Order<br/>Service"]
        B -->|"☎️ gRPC"| C["📦 Inventory<br/>Check Stock"]
        C -->|"✅"| D["📋 Save<br/>Order"]
        D -->|"☎️ gRPC"| E["📦 Reserve<br/>Stock"]
        E --> F["📬 Send<br/>Kafka Message"]
        F --> G["👤 See<br/>PENDING"]
    end

    subgraph "Phase 2: Background (~2 seconds)"
        G -.-> H["📦 Inventory<br/>Confirms"]
        H --> I["📬 Reply<br/>via Kafka"]
        I --> J["📋 Update to<br/>CONFIRMED"]
        J -.-> K["👤 See<br/>CONFIRMED"]
    end

    style A fill:#e3f2fd
    style G fill:#fff3e0
    style K fill:#c8e6c9
```

---

## The Admin Experience

### Viewing All Orders

```mermaid
graph TB
    subgraph "Admin Dashboard"
        subgraph "Orders Table (refreshes every 5 seconds)"
            O1["Order #abc-123<br/>Customer: john<br/>Total: $139.97<br/>🟢 CONFIRMED"]
            O2["Order #def-456<br/>Customer: jane<br/>Total: $29.99<br/>🟠 PENDING"]
            O3["Order #ghi-789<br/>Customer: bob<br/>Total: $299.98<br/>🔴 REJECTED"]
            O4["Order #jkl-012<br/>Customer: alice<br/>Total: $49.99<br/>⚫ CANCELLED"]
        end

        subgraph "Inventory Table (refreshes every 5 seconds)"
            I1["🖱️ Wireless Mouse<br/>Stock: 498 | Reserved: 2 | Available: 496"]
            I2["⌨️ Keyboard<br/>Stock: 299 | Reserved: 1 | Available: 298"]
            I3["🔌 USB-C Hub<br/>Stock: 200 | Reserved: 0 | Available: 200"]
            I4["🎧 Headphones<br/>Stock: 150 | Reserved: 0 | Available: 150<br/>⚠️ LOW STOCK"]
        end
    end

    style O1 fill:#c8e6c9
    style O2 fill:#fff3e0
    style O3 fill:#ffcdd2
    style O4 fill:#e0e0e0
    style I4 fill:#fff3e0
```

### Cancelling an Order

```mermaid
graph TB
    A["👤 Admin clicks 'Cancel'<br/>on order #abc-123"] --> B{"📋 Order Service checks:<br/>What status is it?"}

    B -->|"🟠 PENDING or<br/>🟢 CONFIRMED"| C["✅ Allowed!<br/>Status → ⚫ CANCELLED"]
    B -->|"🔵 SHIPPED"| D["❌ Too late!<br/>'Cannot cancel shipped order'"]
    B -->|"🔴 REJECTED or<br/>⚫ CANCELLED"| E["❌ Already done!<br/>'Cannot cancel this order'"]

    C --> F["📬 Message to Kafka:<br/>'Order cancelled!'"]
    F --> G["📦 Inventory Service:<br/>'Putting those items<br/>back on the shelf'"]
    G --> H["Stock is restored!<br/>Other customers can<br/>now buy those items"]

    style C fill:#c8e6c9
    style D fill:#ffcdd2
    style E fill:#ffcdd2
    style H fill:#e8f5e9
```

---

## How the Two Services Talk to Each Other

The Order Service and Inventory Service talk in two different ways, depending on the situation:

```mermaid
graph TB
    subgraph "Method 1: gRPC — The Phone Call ☎️"
        direction LR
        OS1["📋 Order<br/>Service"] ==>|"'Do you have 3 mice?'<br/>Binary data, super fast<br/>Gets an answer instantly"| IS1["📦 Inventory<br/>Service"]
        IS1 ==>|"'Yes, 500 available!'"| OS1
    end

    subgraph "Method 2: Kafka — The Mailbox 📬"
        direction LR
        OS2["📋 Order<br/>Service"] -->|"Drops a letter:<br/>'Order #123 was placed'"| K["📬 Kafka<br/>(Mailbox)"]
        K -->|"Picks up letter<br/>when ready"| IS2["📦 Inventory<br/>Service"]
        IS2 -->|"Drops a reply:<br/>'Stock confirmed!'"| K2["📬 Kafka<br/>(Mailbox)"]
        K2 -->|"Picks up reply<br/>when ready"| OS3["📋 Order<br/>Service"]
    end

    style K fill:#fce4ec
    style K2 fill:#fce4ec
```

### When do we use each method?

```mermaid
graph TB
    Q{"Do we need an<br/>answer RIGHT NOW?"}

    Q -->|"YES — customer is<br/>waiting on screen"| GRPC["☎️ Use gRPC<br/><br/>Examples:<br/>• Check stock availability<br/>• Reserve stock<br/><br/>Like a phone call —<br/>you wait for the answer"]

    Q -->|"NO — it can happen<br/>in the background"| KAFKA["📬 Use Kafka<br/><br/>Examples:<br/>• Confirm stock reservation<br/>• Release stock on cancel<br/><br/>Like a letter —<br/>delivered when ready"]

    style GRPC fill:#e8f5e9
    style KAFKA fill:#fce4ec
```

---

## What Happens When Two People Want the Same Item?

This is one of the trickiest problems in any online store. Here's how we solve it:

```mermaid
graph TB
    subgraph "The Problem"
        START["Only 2 keyboards left in stock"]
        A["👤 Alice wants 2 keyboards"]
        B["👤 Bob wants 2 keyboards"]
        A --> AT["Both check at the same time..."]
        B --> AT
        AT --> BAD["Without protection: both get approved!<br/>We promised 4 keyboards<br/>but only have 2! 💥"]
    end

    style BAD fill:#ffcdd2
```

```mermaid
graph TB
    subgraph "Our Solution: Reserve + Version Lock"
        START2["2 keyboards in stock, version=1"]

        A2["👤 Alice: 'I want 2'"] --> CHECK_A["Check: 2 available >= 2? ✅"]
        CHECK_A --> RES_A["Reserve 2 for Alice<br/>Save with version=1"]
        RES_A --> SUCCESS_A["✅ Version matched!<br/>Stock: 2 total, 2 reserved, 0 available<br/>Version bumped to 2"]

        B2["👤 Bob: 'I want 2'<br/>(arrives 1ms later)"] --> CHECK_B["Check: 0 available >= 2? ❌"]
        CHECK_B --> FAIL_B["❌ Sorry Bob!<br/>Not enough stock"]
    end

    style SUCCESS_A fill:#c8e6c9
    style FAIL_B fill:#ffcdd2
```

---

## How Data is Stored

### Order Service — PostgreSQL (Tables)

Think of it like a spreadsheet with rows and columns:

```mermaid
graph TB
    subgraph "Orders Table (like a spreadsheet)"
        OH["id | customer | status | total | created_at"]
        O1["abc-123 | john | CONFIRMED | $139.97 | Mar 14, 2:30pm"]
        O2["def-456 | jane | PENDING | $29.99 | Mar 14, 2:35pm"]
    end

    subgraph "Order Items Table (linked to orders)"
        IH["id | order_id | product | qty | price"]
        I1["item-1 | abc-123 | Wireless Mouse | 2 | $29.99"]
        I2["item-2 | abc-123 | Keyboard | 1 | $79.99"]
        I3["item-3 | def-456 | Wireless Mouse | 1 | $29.99"]
    end

    O1 ---|"abc-123 links<br/>to its items"| I1
    O1 --- I2

    style OH fill:#e3f2fd,stroke:none
    style IH fill:#e3f2fd,stroke:none
```

### Inventory Service — MongoDB (Documents)

Think of it like index cards — each card has all the info for one product:

```mermaid
graph TB
    subgraph "Products Collection (like index cards)"
        P1["📇 Card: Wireless Mouse<br/>━━━━━━━━━━━━━━━<br/>productId: prod-1<br/>name: Wireless Mouse<br/>sku: SKU-001<br/>category: electronics<br/>currentStock: 500<br/>reservedStock: 2<br/>version: 3"]

        P2["📇 Card: Mechanical Keyboard<br/>━━━━━━━━━━━━━━━<br/>productId: prod-2<br/>name: Mechanical Keyboard<br/>sku: SKU-002<br/>category: electronics<br/>currentStock: 300<br/>reservedStock: 1<br/>version: 2"]
    end

    subgraph "Stock Events (activity log)"
        E1["📝 Reserved 2 units of prod-1<br/>for order abc-123<br/>at Mar 14, 2:30pm"]
        E2["📝 Reserved 1 unit of prod-2<br/>for order abc-123<br/>at Mar 14, 2:30pm"]
    end

    style P1 fill:#e8f5e9
    style P2 fill:#e8f5e9
    style E1 fill:#fff3e0
    style E2 fill:#fff3e0
```

---

## Order Status — The Journey of an Order

Every order is like a package — it goes through stages:

```mermaid
graph LR
    A["📦 PENDING<br/>🟠<br/><br/>'We got your order,<br/>checking with<br/>the warehouse...'"] --> B["📦 CONFIRMED<br/>🟢<br/><br/>'Warehouse says<br/>your items are<br/>reserved!'"]

    B --> C["📦 SHIPPED<br/>🔵<br/><br/>'Your order<br/>is on its way!'<br/>(future feature)"]

    A -->|"Not enough stock"| D["📦 REJECTED<br/>🔴<br/><br/>'Sorry, we don't<br/>have enough<br/>of that item'"]

    A -->|"Customer cancels"| E["📦 CANCELLED<br/>⚫<br/><br/>'You changed<br/>your mind —<br/>no problem!'"]

    B -->|"Customer cancels"| E

    style A fill:#fff3e0
    style B fill:#c8e6c9
    style C fill:#bbdefb
    style D fill:#ffcdd2
    style E fill:#e0e0e0
```

---

## The Monitoring Dashboard — How We Know Everything Is Healthy

The application tracks its own health, like a car dashboard:

```mermaid
graph TB
    subgraph "🏥 Health Checks"
        H1["localhost:8080/actuator/health<br/>━━━━━━━━━━━━━━━<br/>Order Service: ✅ UP<br/>PostgreSQL: ✅ Connected<br/>Kafka: ✅ Connected"]
        H2["localhost:8081/actuator/health<br/>━━━━━━━━━━━━━━━<br/>Inventory Service: ✅ UP<br/>MongoDB: ✅ Connected<br/>Kafka: ✅ Connected"]
    end

    subgraph "📊 Business Metrics"
        M1["Orders Created: 47<br/>📈 How many orders today?"]
        M2["Orders Rejected: 3<br/>⚠️ Spike = stock problem!"]
        M3["Avg Creation Time: 180ms<br/>🐢 Climbing = performance issue!"]
    end

    style H1 fill:#c8e6c9
    style H2 fill:#c8e6c9
    style M1 fill:#e3f2fd
    style M2 fill:#fff3e0
    style M3 fill:#e3f2fd
```

---

## The Infrastructure — What Runs Where

```mermaid
graph TB
    subgraph "🐳 Docker Containers (started with docker compose up)"
        PG["🐘 PostgreSQL 16<br/>Port 5432<br/>Stores orders"]
        MG["🍃 MongoDB 7<br/>Port 27017<br/>Stores products"]
        KF["📬 Kafka 3.7<br/>Port 29092<br/>Passes messages"]
    end

    subgraph "☕ Java Applications (started with mvn spring-boot:run)"
        OS["📋 Order Service<br/>Port 8080 (HTTP)<br/>Java 21 + Spring Boot"]
        IS["📦 Inventory Service<br/>Port 8081 (HTTP)<br/>Port 9090 (gRPC)<br/>Java 21 + Spring Boot"]
    end

    subgraph "⚛️ Frontend (started with npm run dev)"
        FE["🖥️ React App<br/>Port 5173<br/>TypeScript + Material UI"]
    end

    FE -->|"HTTP"| OS
    FE -->|"HTTP"| IS
    OS -->|"SQL"| PG
    OS -->|"Messages"| KF
    IS -->|"Queries"| MG
    IS -->|"Messages"| KF
    OS -.->|"gRPC"| IS

    style PG fill:#f3e5f5
    style MG fill:#e8f5e9
    style KF fill:#fce4ec
    style OS fill:#fff3e0
    style IS fill:#e8f5e9
    style FE fill:#e3f2fd
```

---

## How to Start Everything

```mermaid
graph TB
    subgraph "Step 1: Start the infrastructure"
        S1["docker compose up -d<br/>━━━━━━━━━━━━━━━<br/>Starts PostgreSQL, MongoDB, Kafka<br/>Wait ~10 seconds for them to be ready"]
    end

    subgraph "Step 2: Start the backend services"
        S2a["cd inventory-service<br/>mvn spring-boot:run -Dspring-boot.run.profiles=dev<br/>━━━━━━━━━━━━━━━<br/>Start this first — the Order Service needs it<br/>Wait until you see 'Started' in the logs"]

        S2b["cd order-service<br/>mvn spring-boot:run -Dspring-boot.run.profiles=dev<br/>━━━━━━━━━━━━━━━<br/>Start second — connects to Inventory via gRPC<br/>Wait until you see 'Started' in the logs"]
    end

    subgraph "Step 3: Start the frontend"
        S3["cd marketplace-frontend<br/>npm run dev<br/>━━━━━━━━━━━━━━━<br/>Open http://localhost:5173 in your browser"]
    end

    S1 --> S2a --> S2b --> S3

    style S1 fill:#f3e5f5
    style S2a fill:#e8f5e9
    style S2b fill:#fff3e0
    style S3 fill:#e3f2fd
```
