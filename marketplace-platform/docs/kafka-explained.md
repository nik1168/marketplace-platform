# Kafka — Explained from Scratch

## What Problem Does Kafka Solve?

Imagine you run a restaurant. When a customer places an order, the waiter could walk to the kitchen, stand there until the food is ready, and then bring it back. But that's horribly inefficient — the waiter is stuck waiting and can't serve other customers.

Instead, the waiter writes the order on a ticket and puts it on a **ticket rail**. The kitchen picks up tickets when ready, prepares the food, and puts the plate on a **pickup rail**. The waiter checks the pickup rail periodically.

**Kafka is the ticket rail between our services.**

```mermaid
graph LR
    subgraph "Without Kafka (direct calls)"
        W1["📋 Order Service"] ==>|"'Reserve stock for<br/>order #123 please'<br/><br/>WAITS here until<br/>Inventory responds...<br/>Customer is blocked!"| K1["📦 Inventory Service"]
        K1 ==>|"'Done!'<br/>(finally)"| W1
    end

    subgraph "With Kafka (ticket rail)"
        W2["📋 Order Service"] -->|"Puts ticket on rail:<br/>'New order #123'<br/><br/>Immediately goes back<br/>to serving customers!"| RAIL["📬 Kafka<br/>(Ticket Rail)"]
        RAIL -->|"Kitchen picks up<br/>ticket when ready"| K2["📦 Inventory Service"]
        K2 -->|"Puts result<br/>on pickup rail"| RAIL2["📬 Kafka<br/>(Pickup Rail)"]
        RAIL2 -->|"Waiter checks<br/>pickup rail"| W3["📋 Order Service"]
    end

    style RAIL fill:#fce4ec
    style RAIL2 fill:#fce4ec
```

---

## The Key Concepts

Kafka has a few core concepts. Let's learn them one by one with real-world analogies.

### Topics — The Mailboxes

A **topic** is a named category of messages — like a labeled mailbox. Our project has two:

```mermaid
graph TB
    subgraph "Our Two Kafka Topics"
        T1["📬 order-events<br/>━━━━━━━━━━━━━━━<br/>Messages about orders:<br/>• 'Order #123 was placed'<br/>• 'Order #456 was cancelled'<br/><br/>Written by: Order Service<br/>Read by: Inventory Service"]

        T2["📬 inventory-events<br/>━━━━━━━━━━━━━━━<br/>Messages about stock:<br/>• 'Stock reserved for order #123'<br/>• 'Stock reservation failed for #789'<br/><br/>Written by: Inventory Service<br/>Read by: Order Service"]
    end

    style T1 fill:#fce4ec
    style T2 fill:#fce4ec
```

Think of topics like TV channels — Channel 1 broadcasts order news, Channel 2 broadcasts inventory news. Each service tunes into the channels it cares about.

### Messages — The Letters

Each message in Kafka has three parts:

```mermaid
graph TB
    subgraph "Anatomy of a Kafka Message"
        subgraph "1. Key (the label on the envelope)"
            KEY["'order-abc-123'<br/><br/>Used for routing — messages with<br/>the same key always go to the<br/>same partition (mailbox slot)"]
        end

        subgraph "2. Value (the letter inside)"
            VAL["{<br/>  orderId: 'abc-123',<br/>  customerId: 'john',<br/>  items: [...],<br/>  totalAmount: 139.97,<br/>  timestamp: '2026-03-14T14:30:00Z'<br/>}<br/><br/>The actual data, serialized as JSON"]
        end

        subgraph "3. Headers (metadata)"
            HDR["Content type, timestamp,<br/>source service, etc."]
        end
    end

    style KEY fill:#e3f2fd
    style VAL fill:#e8f5e9
    style HDR fill:#f3e5f5
```

### Partitions — The Sorting Slots

Each topic is divided into **partitions** — think of them as numbered slots in the mailbox. Our topics each have 3 partitions.

```mermaid
graph TB
    subgraph "order-events topic (3 partitions)"
        P0["Slot 0<br/>━━━━━━━━<br/>Order #A<br/>Order #D<br/>Order #G"]
        P1["Slot 1<br/>━━━━━━━━<br/>Order #B<br/>Order #E<br/>Order #H"]
        P2["Slot 2<br/>━━━━━━━━<br/>Order #C<br/>Order #F<br/>Order #I"]
    end

    PROD["📋 Order Service<br/>(Producer)"] -->|"key = orderId<br/>hash(orderId) % 3<br/>determines which slot"| P0
    PROD --> P1
    PROD --> P2

    style P0 fill:#fce4ec
    style P1 fill:#fce4ec
    style P2 fill:#fce4ec
```

**Why partitions matter:**

1. **Parallelism** — 3 partitions means 3 consumers can read simultaneously, tripling throughput
2. **Ordering** — Messages within a partition are strictly ordered. By using `orderId` as the key, ALL events for the same order land in the same partition, guaranteeing they're processed in order

```mermaid
graph TB
    subgraph "Why Key-Based Routing Matters"
        subgraph "Without keys (random routing)"
            R1["Partition 0: OrderPlaced #123"]
            R2["Partition 1: OrderCancelled #123"]
            R3["Consumer 0 processes: Place order"]
            R4["Consumer 1 processes: Cancel order"]
            R1 --> R3
            R2 --> R4
            BAD["Consumer 1 tries to cancel order #123<br/>but Consumer 0 hasn't created it yet!<br/>💥 RACE CONDITION"]
        end

        subgraph "With orderId as key (deterministic routing)"
            K1["Partition 0: OrderPlaced #123, OrderCancelled #123"]
            K3["Consumer 0 processes both in order:<br/>1. Create order #123 ✅<br/>2. Cancel order #123 ✅"]
            K1 --> K3
            GOOD["Same partition = same consumer = correct order!"]
        end
    end

    style BAD fill:#ffcdd2
    style GOOD fill:#c8e6c9
```

### Producers — The Writers

A **producer** is any service that writes messages to a Kafka topic. In our project:

| Producer | Topic | What it writes |
|----------|-------|---------------|
| Order Service | `order-events` | OrderPlacedEvent, OrderCancelledEvent |
| Inventory Service | `inventory-events` | StockUpdatedEvent |

### Consumers — The Readers

A **consumer** is any service that reads messages from a Kafka topic.

| Consumer | Topic | What it reads | What it does with it |
|----------|-------|-------------|---------------------|
| Inventory Service | `order-events` | OrderPlacedEvent | Reserves stock for the order |
| Order Service | `inventory-events` | StockUpdatedEvent | Updates order to CONFIRMED or REJECTED |

### Consumer Groups — Taking Turns

A **consumer group** is a set of consumers that share the work. If you have 3 partitions and 3 consumers in the same group, each consumer gets 1 partition — no message is processed twice.

```mermaid
graph TB
    subgraph "Consumer Group: inventory-service-group"
        direction TB
        P0["Partition 0"] --> C0["Consumer 0<br/>(Inventory Instance 1)"]
        P1["Partition 1"] --> C1["Consumer 1<br/>(Inventory Instance 2)"]
        P2["Partition 2"] --> C2["Consumer 2<br/>(Inventory Instance 3)"]
    end

    Note["Each partition is assigned to exactly<br/>one consumer in the group.<br/>No message is processed twice!<br/><br/>If you scale to 3 Inventory Service<br/>instances, each handles 1/3 of the orders."]

    style Note fill:#fff9c4,stroke:none
    style P0 fill:#fce4ec
    style P1 fill:#fce4ec
    style P2 fill:#fce4ec
```

---

## How Kafka Works in Our Project

### The Three Message Types

Our project has 3 types of Kafka messages (events). Each is a Java **record** — an immutable data class:

```mermaid
graph TB
    subgraph "Message Type 1: OrderPlacedEvent"
        E1["📨 'A new order was just created!'<br/>━━━━━━━━━━━━━━━<br/>orderId: 'abc-123'<br/>customerId: 'john'<br/>items: [mouse × 2, keyboard × 1]<br/>totalAmount: $139.97<br/>timestamp: 2026-03-14T14:30:00Z<br/><br/>Sent by: Order Service<br/>Read by: Inventory Service<br/>Action: Reserve stock for these items"]
    end

    subgraph "Message Type 2: OrderCancelledEvent"
        E2["📨 'An order was cancelled!'<br/>━━━━━━━━━━━━━━━<br/>orderId: 'abc-123'<br/>items: [mouse × 2, keyboard × 1]<br/>timestamp: 2026-03-14T15:00:00Z<br/><br/>Sent by: Order Service<br/>Read by: Inventory Service<br/>Action: Release reserved stock"]
    end

    subgraph "Message Type 3: StockUpdatedEvent"
        E3["📨 'Stock reservation result!'<br/>━━━━━━━━━━━━━━━<br/>orderId: 'abc-123'<br/>productId: 'prod-1'<br/>success: true<br/>message: 'Stock reserved'<br/>timestamp: 2026-03-14T14:30:02Z<br/><br/>Sent by: Inventory Service<br/>Read by: Order Service<br/>Action: Update order to CONFIRMED or REJECTED"]
    end

    style E1 fill:#e8f5e9
    style E2 fill:#ffcdd2
    style E3 fill:#e3f2fd
```

### The Complete Message Flow

Here's the full journey of messages through Kafka when you place an order:

```mermaid
sequenceDiagram
    participant OS as Order Service
    participant K1 as Kafka Topic<br/>(order-events)
    participant IS as Inventory Service
    participant K2 as Kafka Topic<br/>(inventory-events)

    Note over OS: Customer places an order

    rect rgb(232, 245, 233)
        Note over OS,K1: Step 1: Order Service produces a message
        OS->>OS: Save order to PostgreSQL (PENDING)
        OS->>K1: Publish OrderPlacedEvent<br/>key="abc-123"<br/>value={orderId, items, total...}
        Note over K1: Message is now stored in<br/>partition hash("abc-123") % 3
        OS->>OS: Return to customer immediately<br/>"Order placed! Status: PENDING"
    end

    rect rgb(252, 228, 236)
        Note over K1,IS: Step 2: Inventory Service consumes the message
        K1-->>IS: @KafkaListener picks up<br/>the OrderPlacedEvent
        IS->>IS: For each item in the order:<br/>reserve stock in MongoDB
    end

    rect rgb(227, 242, 253)
        Note over IS,K2: Step 3: Inventory Service produces a reply
        IS->>K2: Publish StockUpdatedEvent<br/>key="abc-123"<br/>value={orderId, success: true}
    end

    rect rgb(255, 243, 224)
        Note over K2,OS: Step 4: Order Service consumes the reply
        K2-->>OS: @KafkaListener picks up<br/>the StockUpdatedEvent
        OS->>OS: Update order status:<br/>PENDING → CONFIRMED
    end

    Note over OS: Customer's browser polls and<br/>sees status changed to CONFIRMED
```

---

## How the Code Works

### Sending Messages (Producers)

The producer uses `KafkaTemplate` — Spring's helper for sending messages. Think of it as the mail service:

```mermaid
graph TB
    subgraph "How KafkaTemplate.send() works"
        A["Your code calls:<br/>kafkaTemplate.send(topic, key, event)"]
        B["Spring's JsonSerializer converts<br/>your Java object to JSON bytes"]
        C["Kafka client determines which<br/>partition to use: hash(key) % 3"]
        D["Message is sent over the network<br/>to the Kafka broker"]
        E["Kafka broker stores the message<br/>on disk (it's persistent!)"]
        F["whenComplete() callback fires:<br/>success or failure"]

        A --> B --> C --> D --> E --> F
    end

    style A fill:#e8f5e9
    style B fill:#e3f2fd
    style C fill:#fff3e0
    style D fill:#f3e5f5
    style E fill:#fce4ec
    style F fill:#e8f5e9
```

Here's the actual code pattern used in our project:

```java
// 1. Build the event (a Java record — immutable data)
var event = new OrderPlacedEvent(orderId, customerId, items, total, Instant.now());

// 2. Send it to Kafka
//    - "order-events" = which topic (mailbox)
//    - orderId = the key (determines partition)
//    - event = the message (automatically serialized to JSON)
kafkaTemplate.send("order-events", orderId, event)
    .whenComplete((result, ex) -> {
        if (ex != null) {
            log.error("Failed to send!", ex);  // Message was NOT delivered
        } else {
            log.info("Sent to partition {}", result.getRecordMetadata().partition());
        }
    });
```

**Important:** The `.send()` method returns **immediately** — it doesn't wait for Kafka to confirm. The `whenComplete` callback fires later when Kafka acknowledges receipt. This is why Kafka is "fire-and-forget" — the producer doesn't block.

### Receiving Messages (Consumers)

The consumer uses `@KafkaListener` — Spring's annotation that automatically subscribes to a topic and calls your method for every message:

```mermaid
graph TB
    subgraph "How @KafkaListener works"
        A["Spring Boot starts up"]
        B["Sees @KafkaListener on a method"]
        C["Creates a Kafka consumer<br/>subscribed to the specified topic"]
        D["Consumer polls Kafka continuously:<br/>'Any new messages for me?'"]
        E["New message arrives!"]
        F["Spring's JsonDeserializer converts<br/>JSON bytes → Java object"]
        G["Your method is called with<br/>the deserialized event object"]
        H["You process it<br/>(reserve stock, update order, etc.)"]
        I["Kafka is told 'I processed that one'<br/>(offset committed)"]

        A --> B --> C --> D --> E --> F --> G --> H --> I
        I -->|"Loop back"| D
    end

    style A fill:#e3f2fd
    style G fill:#e8f5e9
    style H fill:#e8f5e9
    style I fill:#fff3e0
```

Here's the actual code pattern:

```java
// Spring Kafka calls this method automatically for every message
// on the "order-events" topic
@KafkaListener(
    topics = "order-events",                          // Which mailbox to watch
    groupId = "inventory-service-group",               // Consumer group name
    properties = {
        "spring.json.value.default.type=...OrderPlacedEvent",  // What Java class to deserialize into
        "spring.json.use.type.headers=false"                   // Use default type, ignore producer headers
    }
)
public void handleOrderPlaced(OrderPlacedEvent event) {
    // This method is called automatically — you just write the business logic
    log.info("Received OrderPlaced for order {}", event.orderId());

    for (var item : event.items()) {
        boolean success = stockService.reserveStock(item.productId(), item.quantity(), event.orderId());
        // Publish the result back to Kafka
        eventProducer.publishStockUpdated(event.orderId(), item.productId(), success, ...);
    }
}
```

**The magic:** You write a regular Java method, slap `@KafkaListener` on it, and Spring handles all the networking, polling, deserialization, and error handling. Your method just receives a nice Java object.

---

## Topic Configuration

Each service creates its topic at startup using a `@Bean`:

```mermaid
graph TB
    subgraph "Topic Configuration"
        subgraph "Order Service creates:"
            T1["Topic: 'order-events'<br/>Partitions: 3<br/>Replicas: 1"]
        end

        subgraph "Inventory Service creates:"
            T2["Topic: 'inventory-events'<br/>Partitions: 3<br/>Replicas: 1"]
        end
    end

    subgraph "What these settings mean"
        S1["Partitions = 3<br/>━━━━━━━━━━━━<br/>3 parallel lanes for messages.<br/>More partitions = more throughput.<br/>3 consumers can read simultaneously."]

        S2["Replicas = 1<br/>━━━━━━━━━━━━<br/>Only 1 copy of each message.<br/>Fine for development.<br/>In production, use 3 for safety<br/>(survives 2 broker failures)."]
    end

    style T1 fill:#fce4ec
    style T2 fill:#fce4ec
    style S1 fill:#e3f2fd
    style S2 fill:#fff3e0
```

---

## Serialization — How Java Objects Become Kafka Messages

Kafka doesn't understand Java objects — it only deals with bytes. Serialization is the process of converting objects to bytes (and back).

```mermaid
graph LR
    subgraph "Producer Side (Sending)"
        OBJ1["Java Object<br/>OrderPlacedEvent(<br/>  orderId='abc',<br/>  items=[...]<br/>)"] --> SER["JsonSerializer<br/>(Spring Kafka)"]
        SER --> BYTES1["JSON Bytes<br/>{\"orderId\":\"abc\",<br/>\"items\":[...]}"]
        BYTES1 --> KF["Kafka Broker<br/>(stores bytes)"]
    end

    subgraph "Consumer Side (Receiving)"
        KF2["Kafka Broker<br/>(sends bytes)"] --> BYTES2["JSON Bytes<br/>{\"orderId\":\"abc\",<br/>\"items\":[...]}"]
        BYTES2 --> DESER["JsonDeserializer<br/>(Spring Kafka)"]
        DESER --> OBJ2["Java Object<br/>OrderPlacedEvent(<br/>  orderId='abc',<br/>  items=[...]<br/>)"]
    end

    KF -.->|"bytes travel<br/>over the network"| KF2

    style SER fill:#fff3e0
    style DESER fill:#fff3e0
```

**A tricky detail in our project:** The Order Service serializes as `com.marketplace.order.kafka.event.OrderPlacedEvent`, but the Inventory Service's class is `com.marketplace.inventory.kafka.event.OrderPlacedEvent` — different package! We solve this with `spring.json.use.type.headers=false`, which tells the consumer: "Ignore what the producer says the type is — use the default type I specified."

```mermaid
graph TB
    subgraph "The Type Header Problem"
        PROD["Producer sends:<br/>type = com.marketplace.ORDER.kafka.event.OrderPlacedEvent<br/>(Order Service's class name)"]
        CONS["Consumer receives:<br/>'I don't have that class!<br/>My class is com.marketplace.INVENTORY.kafka.event.OrderPlacedEvent'<br/>💥 ClassNotFoundException"]
    end

    subgraph "The Solution"
        FIX["spring.json.use.type.headers=false<br/>spring.json.value.default.type=...inventory...OrderPlacedEvent<br/><br/>'Ignore the type header from the producer.<br/>Always deserialize into MY local class.'"]
    end

    style CONS fill:#ffcdd2
    style FIX fill:#c8e6c9
```

---

## Kafka Configuration in application.yml

```mermaid
graph TB
    subgraph "Order Service application.yml"
        subgraph "Connection"
            C1["bootstrap-servers: localhost:29092<br/>━━━━━━━━━━━━<br/>Where Kafka is running.<br/>29092 is the 'external' listener<br/>for apps outside Docker."]
        end

        subgraph "Producer Settings"
            P1["key-serializer: StringSerializer<br/>━━━━━━━━━━━━<br/>Keys (orderId) are plain strings"]
            P2["value-serializer: JsonSerializer<br/>━━━━━━━━━━━━<br/>Values (events) are JSON objects"]
        end

        subgraph "Consumer Settings"
            C2["group-id: order-service-group<br/>━━━━━━━━━━━━<br/>Consumer group name"]
            C3["auto-offset-reset: earliest<br/>━━━━━━━━━━━━<br/>'If I join fresh, read from<br/>the beginning — don't skip any'"]
            C4["key-deserializer: StringDeserializer<br/>value-deserializer: JsonDeserializer<br/>━━━━━━━━━━━━<br/>Reverse of the serializers"]
            C5["trusted.packages: com.marketplace.*<br/>━━━━━━━━━━━━<br/>'Allow deserializing classes from<br/>this package (security measure)'"]
        end
    end

    style C1 fill:#e3f2fd
    style P1 fill:#e8f5e9
    style P2 fill:#e8f5e9
    style C2 fill:#fff3e0
    style C3 fill:#fff3e0
    style C4 fill:#fff3e0
    style C5 fill:#fff3e0
```

---

## KRaft Mode — Kafka Without Zookeeper

Our Kafka runs in **KRaft mode** — a newer way to run Kafka without a separate coordination service called Zookeeper.

```mermaid
graph TB
    subgraph "The Old Way (Kafka + Zookeeper)"
        ZK["Zookeeper<br/>━━━━━━━━━━<br/>Tracks which brokers<br/>are alive, who is the<br/>leader for each partition"]
        KB1["Kafka Broker 1"]
        KB2["Kafka Broker 2"]
        KB3["Kafka Broker 3"]
        ZK --> KB1
        ZK --> KB2
        ZK --> KB3
        PROBLEM["2 systems to operate,<br/>monitor, and debug.<br/>Zookeeper adds complexity."]
    end

    subgraph "The New Way (KRaft Mode)"
        KR1["Kafka Broker<br/>(also the controller)<br/>━━━━━━━━━━<br/>Handles messages AND<br/>coordinates the cluster"]
        BENEFIT["1 system to operate.<br/>Simpler, faster startup,<br/>fewer moving parts."]
    end

    style PROBLEM fill:#ffcdd2
    style BENEFIT fill:#c8e6c9
```

In our `docker-compose.yml`, this line enables KRaft:
```yaml
KAFKA_PROCESS_ROLES: broker,controller  # This node is BOTH broker and controller
```

---

## Why Not Just Use REST for Everything?

Here's why Kafka is better than REST for certain situations:

```mermaid
graph TB
    subgraph "Scenario: Inventory Service goes down for 5 minutes"
        subgraph "With REST (direct calls)"
            R1["Order Service tries to call<br/>Inventory Service..."]
            R2["Connection refused! 💥<br/>Inventory is down!"]
            R3["Order fails.<br/>Customer gets an error.<br/>Order is lost forever."]
            R1 --> R2 --> R3
        end

        subgraph "With Kafka (message queue)"
            K1["Order Service puts message<br/>in Kafka..."]
            K2["Message is safely stored<br/>on disk. It can wait."]
            K3["5 minutes later, Inventory<br/>Service comes back online..."]
            K4["Picks up the message<br/>and processes it!<br/>Nothing was lost. ✅"]
            K1 --> K2 --> K3 --> K4
        end
    end

    style R3 fill:#ffcdd2
    style K4 fill:#c8e6c9
```

```mermaid
graph TB
    subgraph "Feature Comparison"
        subgraph "REST"
            RF1["✅ Simple and familiar"]
            RF2["✅ Instant response"]
            RF3["❌ Sender waits for receiver"]
            RF4["❌ If receiver is down, message is lost"]
            RF5["❌ Sender must know about receiver"]
        end

        subgraph "Kafka"
            KF1["✅ Sender never waits"]
            KF2["✅ Messages survive outages (stored on disk)"]
            KF3["✅ Sender doesn't need to know who reads"]
            KF4["✅ Multiple readers can process same messages"]
            KF5["❌ More complex to set up"]
            KF6["❌ Not instant (slight delay)"]
        end
    end

    style RF1 fill:#c8e6c9
    style RF2 fill:#c8e6c9
    style RF3 fill:#ffcdd2
    style RF4 fill:#ffcdd2
    style RF5 fill:#ffcdd2
    style KF1 fill:#c8e6c9
    style KF2 fill:#c8e6c9
    style KF3 fill:#c8e6c9
    style KF4 fill:#c8e6c9
    style KF5 fill:#ffcdd2
    style KF6 fill:#ffcdd2
```

---

## Kafka vs gRPC — When We Use Each

```mermaid
graph TB
    Q{"Is someone waiting<br/>for the answer?"}

    Q -->|"YES<br/>Customer is on screen<br/>waiting for 'Order Placed'"| GRPC["☎️ Use gRPC<br/><br/>Order Service calls Inventory:<br/>'Check stock NOW'<br/>'Reserve stock NOW'<br/><br/>~5-10ms response time"]

    Q -->|"NO<br/>Customer already got<br/>their response"| KAFKA["📬 Use Kafka<br/><br/>Order Service publishes:<br/>'Order was placed'<br/>'Order was cancelled'<br/><br/>Processed in 1-3 seconds"]

    style GRPC fill:#e8f5e9
    style KAFKA fill:#fce4ec
```

---

## The Complete Kafka Architecture in Our Project

```mermaid
graph TB
    subgraph "Order Service"
        OC["OrderController<br/>POST /api/orders"]
        OS["OrderService<br/>(creates order, calls gRPC)"]
        OP["OrderEventProducer<br/>(sends to Kafka)"]
        IC["InventoryEventConsumer<br/>(@KafkaListener)"]
        OC --> OS --> OP
        IC --> OS
    end

    subgraph "Kafka Broker (Docker)"
        T1["📬 order-events<br/>(3 partitions)"]
        T2["📬 inventory-events<br/>(3 partitions)"]
    end

    subgraph "Inventory Service"
        OEC["OrderEventConsumer<br/>(@KafkaListener)"]
        ISS["InventoryStockService<br/>(reserves/releases stock)"]
        IP["InventoryEventProducer<br/>(sends to Kafka)"]
        OEC --> ISS --> IP
    end

    OP -->|"OrderPlacedEvent<br/>OrderCancelledEvent"| T1
    T1 -->|"consumes"| OEC
    IP -->|"StockUpdatedEvent"| T2
    T2 -->|"consumes"| IC

    style T1 fill:#fce4ec
    style T2 fill:#fce4ec
    style OP fill:#fff3e0
    style IP fill:#fff3e0
    style IC fill:#e3f2fd
    style OEC fill:#e3f2fd
```

---

## Key Source Files

| File | What it does |
|------|-----------|
| `order-service/.../kafka/OrderEventProducer.java` | Sends OrderPlaced and OrderCancelled events |
| `order-service/.../kafka/InventoryEventConsumer.java` | Receives StockUpdated events, updates order status |
| `order-service/.../kafka/event/OrderPlacedEvent.java` | Message structure for new orders |
| `order-service/.../kafka/event/OrderCancelledEvent.java` | Message structure for cancelled orders |
| `order-service/.../kafka/event/StockUpdatedEvent.java` | Message structure for stock results |
| `order-service/.../config/KafkaTopicConfig.java` | Creates the "order-events" topic (3 partitions) |
| `inventory-service/.../kafka/OrderEventConsumer.java` | Receives OrderPlaced events, reserves stock |
| `inventory-service/.../kafka/InventoryEventProducer.java` | Sends StockUpdated events |
| `inventory-service/.../config/KafkaTopicConfig.java` | Creates the "inventory-events" topic (3 partitions) |
| `order-service/src/main/resources/application.yml` | Kafka connection and serializer config |
| `inventory-service/src/main/resources/application.yml` | Kafka connection and serializer config |
| `docker-compose.yml` (lines 26-42) | Kafka container in KRaft mode |

---

## Summary — Kafka in One Picture

```mermaid
graph LR
    subgraph "The Story of a Kafka Message"
        A["📋 Order Service<br/>creates an order"] --> B["📨 Builds an event<br/>(Java record)"]
        B --> C["📤 KafkaTemplate<br/>serializes to JSON<br/>and sends to Kafka"]
        C --> D["📬 Kafka stores it<br/>on disk in a partition<br/>(safe, persistent)"]
        D --> E["📥 @KafkaListener<br/>picks it up and<br/>deserializes from JSON"]
        E --> F["📦 Inventory Service<br/>processes it<br/>(reserves stock)"]
    end

    style A fill:#e3f2fd
    style D fill:#fce4ec
    style F fill:#e8f5e9
```

**The key insight:** Kafka decouples services in time and space. The producer doesn't know (or care) who reads its messages or when. The consumer doesn't need to be running when the message is sent. And if you need to add a third service that also cares about orders (like a notification service), just add another consumer — zero changes to the producer.
