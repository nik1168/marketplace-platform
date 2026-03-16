# Virtual Threads — Explained from Scratch

## What Problem Do Virtual Threads Solve?

To understand virtual threads, you first need to understand what a **thread** is and why traditional threads have a problem.

### What's a Thread?

Think of a thread as a **worker** in a restaurant. Each worker can handle one customer at a time. When a customer places an order, the worker:

1. Takes the order
2. Walks to the kitchen and **waits** for the food
3. Brings the food back

The waiting part is the problem.

```mermaid
graph TB
    subgraph "A Thread Handling an HTTP Request"
        A["Receive request from customer"] --> B["Call Inventory Service via gRPC<br/>━━━━━━━━━━━━<br/>WAITING... doing nothing...<br/>just standing here...<br/>for 5-10 milliseconds"]
        B --> C["Save order to PostgreSQL<br/>━━━━━━━━━━━━<br/>WAITING... doing nothing...<br/>for 2-5 milliseconds"]
        C --> D["Send message to Kafka<br/>━━━━━━━━━━━━<br/>WAITING... doing nothing...<br/>for 1-2 milliseconds"]
        D --> E["Return response to customer"]
    end

    Note["Out of ~20ms total, the thread<br/>spends ~15ms doing NOTHING.<br/>It's just waiting for network responses."]

    style B fill:#ffcdd2
    style C fill:#ffcdd2
    style D fill:#ffcdd2
    style Note fill:#fff9c4,stroke:none
```

### The Traditional Thread Problem

A traditional Java server (Tomcat) creates a **pool of platform threads** — typically around 200. Each thread is backed by an operating system thread, which is expensive: each one takes about 1MB of memory and OS resources.

```mermaid
graph TB
    subgraph "Traditional Thread Pool (200 threads)"
        T1["Thread 1: handling request<br/>⏳ waiting for gRPC..."]
        T2["Thread 2: handling request<br/>⏳ waiting for database..."]
        T3["Thread 3: handling request<br/>⏳ waiting for Kafka..."]
        T4["..."]
        T200["Thread 200: handling request<br/>⏳ waiting for gRPC..."]
    end

    NEW["Customer #201 arrives!"]
    REJECT["❌ 503 Service Unavailable<br/>'All threads busy, try again later'<br/><br/>But the threads aren't DOING anything!<br/>They're just waiting!"]

    NEW --> REJECT

    style REJECT fill:#ffcdd2
```

**The irony:** All 200 threads are "busy" but none of them are actually doing work. They're all just **waiting** for network responses. The CPU is practically idle, but the server can't accept new requests.

This is like a restaurant with 200 waiters, all standing in the kitchen waiting for food, while new customers are turned away at the door.

---

## The Solution: Virtual Threads

Java 21 introduced **virtual threads** — lightweight threads that are managed by the JVM instead of the operating system. Here's the key difference:

```mermaid
graph TB
    subgraph "Platform Threads (Old Way)"
        PT["200 threads<br/>Each one: ~1MB of memory<br/>Backed by an OS thread<br/>Total: ~200MB just for threads<br/><br/>When waiting: thread is BLOCKED<br/>(still consuming resources)"]
    end

    subgraph "Virtual Threads (Java 21)"
        VT["Millions of virtual threads<br/>Each one: ~1KB of memory<br/>Backed by a small pool of<br/>carrier (OS) threads<br/>Total: a few MB for millions<br/><br/>When waiting: thread is PARKED<br/>(costs almost nothing!)"]
    end

    style PT fill:#ffcdd2
    style VT fill:#c8e6c9
```

### How Virtual Threads Work — The Hotel Analogy

Think of **carrier threads** as hotel rooms and **virtual threads** as guests.

**Old way (platform threads):** Each guest gets their own room for the entire stay. If they leave to go sightseeing for 8 hours, the room sits empty — but it's still "theirs." With 200 rooms, you can only have 200 guests.

**New way (virtual threads):** Guests share rooms. When a guest leaves to go sightseeing (= waiting for a network response), they check out and the room becomes available for another guest. When they return, they check into any available room. With 200 rooms, you can serve **thousands** of guests because most are out sightseeing at any given time.

```mermaid
graph TB
    subgraph "Platform Threads = 1 room per guest"
        R1["Room 1: Guest A<br/>(out sightseeing — room empty!)"]
        R2["Room 2: Guest B<br/>(out sightseeing — room empty!)"]
        R3["Room 3: Guest C<br/>(actually sleeping)"]
        R200["Room 200: Guest 200<br/>(out sightseeing — room empty!)"]
        FULL["Hotel FULL!<br/>Guest 201 turned away!<br/><br/>But 199 rooms are empty..."]
    end

    subgraph "Virtual Threads = shared rooms"
        SR1["Room 1: Guest C (sleeping)"]
        SR2["Room 2: Guest F (sleeping)"]
        SR3["Room 3: available"]
        SR4["Room 4: available"]
        GUESTS["Guests A, B, D, E, G, H...<br/>10,000 guests all sightseeing.<br/>When any comes back, they get<br/>the next available room.<br/><br/>10,000 guests, 4 rooms. ✅"]
    end

    style FULL fill:#ffcdd2
    style GUESTS fill:#c8e6c9
```

---

## What Happens Under the Hood

When a virtual thread hits a blocking operation (like a network call), here's what actually happens:

```mermaid
sequenceDiagram
    participant VT as Virtual Thread<br/>(your request handler)
    participant CT as Carrier Thread<br/>(OS thread)
    participant NET as Network
    participant VT2 as Another Virtual Thread<br/>(different request)

    Note over VT,CT: Virtual Thread starts running on Carrier Thread

    VT->>CT: Execute: receive HTTP request
    VT->>CT: Execute: build gRPC request

    VT->>NET: Send gRPC request to Inventory Service

    Note over VT,CT: BLOCKING CALL! What happens now?

    rect rgb(255, 243, 224)
        Note over VT: Virtual Thread PARKS itself<br/>(unmounts from Carrier Thread)<br/>Costs: ~0 resources
    end

    rect rgb(232, 245, 233)
        Note over CT: Carrier Thread is FREE!<br/>It can run another virtual thread now!
        CT->>VT2: Pick up another virtual thread
        VT2->>CT: Execute: process different request
    end

    NET-->>VT: gRPC response arrives!

    rect rgb(227, 242, 253)
        Note over VT: Virtual Thread UNPARKS<br/>(gets scheduled on a carrier thread)
        VT->>CT: Resume: process gRPC response
        VT->>CT: Execute: save to database...
    end
```

The key insight: **the carrier thread is never wasted.** When one virtual thread parks, the carrier immediately picks up another virtual thread that has work to do.

---

## Virtual Threads in Our Order Service

Here's specifically what happens in our Order Service when virtual threads are enabled:

```mermaid
graph TB
    subgraph "Processing Order #123 (Virtual Thread 1)"
        A1["1. Receive POST /api/orders<br/>▶️ Running on Carrier-1"]
        A2["2. gRPC: checkStock()<br/>⏸️ PARK — Carrier-1 is freed"]
        A3["3. gRPC response arrives<br/>▶️ Resume on Carrier-3"]
        A4["4. Save to PostgreSQL<br/>⏸️ PARK — Carrier-3 is freed"]
        A5["5. DB response arrives<br/>▶️ Resume on Carrier-1"]
        A6["6. gRPC: reserveStock()<br/>⏸️ PARK — Carrier-1 is freed"]
        A7["7. gRPC response arrives<br/>▶️ Resume on Carrier-2"]
        A8["8. Kafka: send event<br/>⏸️ PARK — Carrier-2 is freed"]
        A9["9. Kafka confirms<br/>▶️ Resume on Carrier-1"]
        A10["10. Return HTTP response"]
        A1 --> A2 --> A3 --> A4 --> A5 --> A6 --> A7 --> A8 --> A9 --> A10
    end

    Note["Notice: the virtual thread bounces<br/>between different carrier threads!<br/>It doesn't 'own' any carrier thread.<br/><br/>Each ⏸️ PARK moment lets the<br/>carrier serve another request."]

    style A2 fill:#fff3e0
    style A4 fill:#fff3e0
    style A6 fill:#fff3e0
    style A8 fill:#fff3e0
    style Note fill:#fff9c4,stroke:none
```

### How many requests can we handle?

```mermaid
graph TB
    subgraph "With Platform Threads"
        PT_CALC["200 threads<br/>Each request takes ~20ms<br/>Each thread handles 50 requests/second<br/>━━━━━━━━━━━━<br/>Max: 200 × 50 = 10,000 requests/second<br/><br/>If requests slow down (gRPC takes 100ms),<br/>throughput drops to 2,000 requests/second!"]
    end

    subgraph "With Virtual Threads"
        VT_CALC["Unlimited virtual threads<br/>Each request still takes ~20ms<br/>But blocking doesn't consume resources<br/>━━━━━━━━━━━━<br/>Bottleneck moves from threads<br/>to CPU, database, or network<br/><br/>If gRPC slows to 100ms, throughput<br/>stays high — more virtual threads<br/>just park, not consuming resources"]
    end

    style PT_CALC fill:#ffcdd2
    style VT_CALC fill:#c8e6c9
```

---

## How to Enable Virtual Threads

This is the best part — it takes exactly **two lines of configuration:**

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

That's it. No code changes. Spring Boot automatically:
1. Configures Tomcat to use virtual threads for handling HTTP requests
2. Every incoming request gets its own virtual thread
3. All blocking operations (gRPC, database, Kafka) automatically park instead of blocking

```mermaid
graph TB
    subgraph "What that one config line changes"
        subgraph "Before (enabled: false)"
            B1["Tomcat creates a pool of 200<br/>platform (OS) threads"]
            B2["Each request gets one platform thread"]
            B3["Thread is occupied until<br/>the request completes"]
            B4["Thread 201: 'Sorry, pool exhausted'"]
            B1 --> B2 --> B3 --> B4
        end

        subgraph "After (enabled: true)"
            A1["Tomcat creates virtual threads<br/>on demand (no limit)"]
            A2["Each request gets its own<br/>virtual thread (~1KB)"]
            A3["Virtual thread parks during waits<br/>carrier thread serves others"]
            A4["Thread 10,001: 'No problem,<br/>here's another virtual thread!'"]
            A1 --> A2 --> A3 --> A4
        end
    end

    style B4 fill:#ffcdd2
    style A4 fill:#c8e6c9
```

---

## When Do Virtual Threads Help the Most?

Virtual threads shine when your application does a lot of **I/O waiting** — which is exactly what our Order Service does:

```mermaid
graph TB
    subgraph "Our Order Service — I/O Heavy"
        IO1["gRPC call to Inventory Service<br/>⏳ 5-10ms of waiting"]
        IO2["PostgreSQL write<br/>⏳ 2-5ms of waiting"]
        IO3["gRPC call to reserve stock<br/>⏳ 5-10ms of waiting"]
        IO4["Kafka message send<br/>⏳ 1-2ms of waiting"]
        CPU["Actual CPU work<br/>(building objects, calculating totals)<br/>⚡ < 1ms"]
    end

    PIE["Ratio: ~95% waiting, ~5% computing<br/><br/>Virtual threads are PERFECT for this.<br/>The CPU is barely used — the bottleneck<br/>is entirely in waiting for responses."]

    style IO1 fill:#ffcdd2
    style IO2 fill:#ffcdd2
    style IO3 fill:#ffcdd2
    style IO4 fill:#ffcdd2
    style CPU fill:#c8e6c9
    style PIE fill:#fff9c4,stroke:none
```

### When Virtual Threads DON'T Help

```mermaid
graph TB
    subgraph "CPU-Intensive Work (virtual threads don't help)"
        C1["Image processing<br/>Video encoding<br/>Machine learning<br/>Complex calculations<br/><br/>The thread is actually COMPUTING,<br/>not waiting. It needs the CPU.<br/>Virtual or platform — same speed."]
    end

    subgraph "I/O-Intensive Work (virtual threads shine!)"
        I1["Database queries<br/>HTTP/gRPC calls<br/>File reads/writes<br/>Kafka messaging<br/><br/>The thread is WAITING, not computing.<br/>Virtual threads park during the wait,<br/>freeing resources for other requests."]
    end

    style C1 fill:#fff3e0
    style I1 fill:#c8e6c9
```

---

## Virtual Threads vs Reactive Programming

Before virtual threads, the main solution for high-concurrency Java was **reactive programming** (like Spring WebFlux, Project Reactor). Here's why virtual threads are simpler:

```mermaid
graph TB
    subgraph "Reactive Programming (The Old Solution)"
        RX1["// Reactive code — hard to read and debug
orderService.createOrder(request)
    .flatMap(order ->
        inventoryClient.checkStock(order)
            .flatMap(available -> {
                if (!available)
                    return Mono.error(...);
                return inventoryClient.reserveStock(order)
                    .flatMap(reserved ->
                        orderRepo.save(order)
                            .flatMap(saved ->
                                kafkaTemplate.send(event)
                                    .map(result -> saved)
                            )
                    );
            })
    )
    .subscribe();"]
    end

    subgraph "Virtual Threads (The Java 21 Solution)"
        VT1["// Normal code — easy to read and debug
Order order = orderService.createOrder(request);
boolean available = inventoryClient.checkStock(order);
if (!available) throw new InsufficientStockException();
inventoryClient.reserveStock(order);
orderRepo.save(order);
kafkaTemplate.send(event);
return order;"]
    end

    style RX1 fill:#ffcdd2
    style VT1 fill:#c8e6c9
```

**The comparison:**

| Aspect | Reactive (WebFlux) | Virtual Threads |
|--------|-------------------|-----------------|
| **Code style** | Callbacks, chains, complex operators | Normal, sequential Java code |
| **Debugging** | Painful — stack traces are meaningless | Normal stack traces |
| **Learning curve** | Steep — new programming model | Zero — write code the same way |
| **Libraries** | Need reactive drivers for everything | Works with existing blocking libraries |
| **Performance** | Excellent | Excellent (comparable) |

**Interview talking point:** "We chose virtual threads over reactive programming because they give us the same concurrency benefits with normal, readable, debuggable code. Our OrderService uses standard `@Transactional` methods with blocking gRPC calls — with virtual threads enabled, these blocking calls are automatically efficient because the virtual thread parks instead of blocking a platform thread."

---

## The Relationship Between Virtual Threads, gRPC, and Kafka

All three technologies work together beautifully in our project:

```mermaid
graph TB
    subgraph "How Virtual Threads Make Everything Better"
        subgraph "gRPC Calls (blocking)"
            G["inventoryClient.checkStock()<br/>━━━━━━━━━━━━<br/>Without VT: platform thread BLOCKS (wasted)<br/>With VT: virtual thread PARKS (free)"]
        end

        subgraph "Database Queries (blocking)"
            D["orderRepository.save(order)<br/>━━━━━━━━━━━━<br/>Without VT: platform thread BLOCKS (wasted)<br/>With VT: virtual thread PARKS (free)"]
        end

        subgraph "Kafka Sends (async but confirmation blocks)"
            K["kafkaTemplate.send(event)<br/>━━━━━━━━━━━━<br/>The send itself is async, but if<br/>you .get() the result, it blocks.<br/>With VT: park during the wait."]
        end
    end

    BENEFIT["With virtual threads, the Order Service<br/>can handle thousands of simultaneous<br/>order placements, even though each<br/>one makes 2 gRPC calls + 1 DB write +<br/>1 Kafka send. The carrier threads<br/>are never idle — they're always<br/>picking up the next virtual thread."]

    style G fill:#e8f5e9
    style D fill:#e3f2fd
    style K fill:#fce4ec
    style BENEFIT fill:#fff9c4,stroke:none
```

---

## Key Source Files

| File | What it does |
|------|-----------|
| `order-service/src/main/resources/application.yml` (line 5) | `spring.threads.virtual.enabled: true` — enables virtual threads |
| `inventory-service/src/main/resources/application.yml` (line 5) | Same setting for the Inventory Service |
| `pom.xml` (line 26) | `<java.version>21</java.version>` — requires Java 21 |

That's it — 3 lines across the entire project. The power of virtual threads is that they require **zero code changes**. Every blocking call in the application automatically benefits.

---

## Summary — Virtual Threads in One Picture

```mermaid
graph LR
    subgraph "The Core Idea"
        A["Thread hits a<br/>blocking call<br/>(gRPC, DB, Kafka)"] --> B{"Platform Thread<br/>or Virtual Thread?"}
        B -->|"Platform Thread"| C["Thread BLOCKS<br/>━━━━━━━━━━<br/>~1MB consumed<br/>OS thread occupied<br/>Can't serve others<br/>Pool exhausted at 200"]
        B -->|"Virtual Thread"| D["Thread PARKS<br/>━━━━━━━━━━<br/>~1KB consumed<br/>Carrier freed instantly<br/>Serves other requests<br/>Millions supported"]
    end

    style C fill:#ffcdd2
    style D fill:#c8e6c9
```

**The key insight:** Virtual threads make blocking calls cheap. Instead of redesigning your code to be non-blocking (reactive), you write normal sequential Java code and let the JVM handle the efficiency. One config line, zero code changes, massive concurrency improvement.
