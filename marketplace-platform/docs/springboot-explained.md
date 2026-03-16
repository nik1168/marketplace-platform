# Spring Boot — Explained from Scratch

## What is Spring Boot?

Imagine you want to open a restaurant. You have two options:

**Option A: Build everything from scratch.** You design the kitchen layout, install plumbing, wire the electricity, buy ovens, set up ventilation, build the dining area, install a POS system... Before cooking a single dish, you've spent months on infrastructure.

**Option B: Move into a pre-built restaurant space.** The kitchen is already set up with standard equipment, the plumbing works, tables are in place. You just bring your recipes, hire your staff, and start cooking.

**Spring Boot is Option B.**

```mermaid
graph TB
    subgraph "Without Spring Boot (build from scratch)"
        A1["1. Set up a web server (Tomcat)"]
        A2["2. Configure database connections"]
        A3["3. Wire up dependency injection"]
        A4["4. Set up JSON serialization"]
        A5["5. Configure security, logging, metrics..."]
        A6["6. FINALLY write your business logic"]
        A1 --> A2 --> A3 --> A4 --> A5 --> A6
    end

    subgraph "With Spring Boot (pre-configured)"
        B1["1. Add dependencies to pom.xml"]
        B2["2. Write your business logic"]
        B3["Done!"]
        B1 --> B2 --> B3
    end

    style A1 fill:#ffcdd2
    style A2 fill:#ffcdd2
    style A3 fill:#ffcdd2
    style A4 fill:#ffcdd2
    style A5 fill:#ffcdd2
    style A6 fill:#c8e6c9
    style B1 fill:#e3f2fd
    style B2 fill:#c8e6c9
    style B3 fill:#c8e6c9
```

**Spring** is a massive Java framework — it's the kitchen equipment, plumbing, and wiring. It provides tools for web servers, databases, messaging, security, and more.

**Spring Boot** is the pre-configured setup. It says: "You added a database dependency? I'll configure a connection pool for you. You added a web dependency? I'll start an embedded Tomcat server. You added Kafka? I'll create producers and consumers automatically." You just write the parts that are unique to your business.

---

## Dependency Injection — The Core Idea

### The Analogy: A Restaurant Kitchen

Imagine a chef in a restaurant. The chef needs ingredients (tomatoes, flour, olive oil) to cook. There are two approaches:

**Without dependency injection:** The chef goes to the farm, picks tomatoes, goes to the mill, grinds flour, goes to the olive grove... The chef is responsible for finding and creating every ingredient.

**With dependency injection:** The restaurant has a **supply manager** who delivers fresh ingredients to the kitchen every morning. The chef just says "I need tomatoes, flour, and olive oil" and they appear on the counter. The chef doesn't care where they come from — they just use them.

**Spring is the supply manager.** Your classes declare what they need (in the constructor), and Spring provides it automatically.

```mermaid
graph TB
    subgraph "Without Dependency Injection"
        CHEF1["OrderService"] -->|"creates its own"| DB1["new OrderRepository()"]
        CHEF1 -->|"creates its own"| GRPC1["new InventoryGrpcClient()"]
        CHEF1 -->|"creates its own"| KAFKA1["new OrderEventProducer()"]
        PROBLEM["The service must know HOW to create<br/>every dependency, and all THEIR<br/>dependencies too. Tightly coupled!"]
    end

    subgraph "With Dependency Injection (Spring)"
        SPRING["Spring Container<br/>(the supply manager)<br/>Creates and manages<br/>all objects"] -->|"provides"| DB2["OrderRepository"]
        SPRING -->|"provides"| GRPC2["InventoryGrpcClient"]
        SPRING -->|"provides"| KAFKA2["OrderEventProducer"]
        DB2 --> CHEF2["OrderService"]
        GRPC2 --> CHEF2
        KAFKA2 --> CHEF2
        BENEFIT["The service just declares what it needs.<br/>Spring handles creation and wiring.<br/>Loosely coupled!"]
    end

    style PROBLEM fill:#ffcdd2
    style BENEFIT fill:#c8e6c9
    style SPRING fill:#fff3e0
```

### How It Works in Our Project

Here is exactly how dependency injection works in the Order Service:

```java
// The OrderService DECLARES what it needs in its constructor.
// Spring sees these parameters and says: "I know what those are — let me provide them."
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryGrpcClient inventoryClient;
    private final OrderEventProducer eventProducer;

    // Constructor injection — Spring automatically provides all these dependencies
    public OrderService(OrderRepository orderRepository,
                        InventoryGrpcClient inventoryClient,
                        OrderEventProducer eventProducer, ...) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
        this.eventProducer = eventProducer;
    }
}
```

Spring's process at startup:
1. "I see `OrderService` needs an `OrderRepository`. Let me create that first."
2. "I see `OrderService` needs an `InventoryGrpcClient`. Let me create that too."
3. "I see `OrderService` needs an `OrderEventProducer`. Let me create that."
4. "Now I have everything. Let me create `OrderService` and pass all three in."

```mermaid
graph TB
    subgraph "Spring Boot Startup — Dependency Resolution"
        S["Spring Container starts up"]
        S --> SCAN["Scans all classes for<br/>@Service, @Component,<br/>@Repository, @Configuration..."]
        SCAN --> RESOLVE["Resolves dependency tree:<br/>Who needs what?"]
        RESOLVE --> CREATE["Creates objects in the right order:<br/>1. OrderRepository (no deps)<br/>2. InventoryGrpcClient (no deps)<br/>3. OrderEventProducer (needs KafkaTemplate)<br/>4. OrderService (needs all three above)"]
        CREATE --> READY["Application is ready!<br/>All objects are wired together."]
    end

    style S fill:#e3f2fd
    style SCAN fill:#fff3e0
    style RESOLVE fill:#f3e5f5
    style CREATE fill:#e8f5e9
    style READY fill:#c8e6c9
```

---

## Every Annotation Used in Our Project — Explained

Annotations are the labels you put on your classes and methods to tell Spring what to do with them. They start with `@`. Think of them as sticky notes you attach to your code saying "Spring, treat this as a ____."

### Core Component Annotations

These tell Spring: "This class is important — create an instance and manage it for me."

```mermaid
graph TB
    subgraph "Component Annotations — The Family Tree"
        SBA["@SpringBootApplication<br/>━━━━━━━━━━━━<br/>The big boss.<br/>Combines 3 annotations:<br/>@Configuration<br/>@EnableAutoConfiguration<br/>@ComponentScan<br/><br/>Put on the main class.<br/>Says 'start everything!'"]

        RC["@RestController<br/>━━━━━━━━━━━━<br/>Handles HTTP requests.<br/>Like a waiter who takes<br/>customer orders and<br/>brings back food (JSON).<br/><br/>Used on: OrderController<br/>ProductController"]

        SVC["@Service<br/>━━━━━━━━━━━━<br/>Contains business logic.<br/>Like the head chef who<br/>decides HOW to cook.<br/><br/>Used on: OrderService<br/>InventoryStockService"]

        REPO["@Repository<br/>━━━━━━━━━━━━<br/>Talks to the database.<br/>Like the pantry manager who<br/>stores and retrieves ingredients.<br/><br/>Used on: OrderRepository<br/>ProductRepository<br/>(implicit — via JpaRepository)"]

        COMP["@Component<br/>━━━━━━━━━━━━<br/>A generic Spring-managed class.<br/>Like a utility worker who<br/>does specialized tasks.<br/><br/>Used on: OrderEventProducer<br/>InventoryEventConsumer<br/>InventoryGrpcClient<br/>DataSeeder"]

        CFG["@Configuration<br/>━━━━━━━━━━━━<br/>Defines beans and settings.<br/>Like the restaurant manager<br/>who sets up the kitchen<br/>before opening day.<br/><br/>Used on: MetricsConfig<br/>KafkaTopicConfig<br/>WebConfig"]
    end

    style SBA fill:#fff3e0
    style RC fill:#e3f2fd
    style SVC fill:#e8f5e9
    style REPO fill:#f3e5f5
    style COMP fill:#fce4ec
    style CFG fill:#fff9c4
```

| Annotation | Restaurant Analogy | Used In Our Project |
|---|---|---|
| `@SpringBootApplication` | Opening the restaurant — turns on all the lights, starts all systems | `OrderServiceApplication`, `InventoryServiceApplication` |
| `@RestController` | The waiter — takes orders from customers (HTTP requests) and brings back results (JSON) | `OrderController`, `ProductController` |
| `@Service` | The head chef — contains the recipes (business logic) | `OrderService`, `InventoryStockService` |
| `@Repository` | The pantry manager — stores and retrieves ingredients (data) | `OrderRepository` (implicit), `ProductRepository` (implicit) |
| `@Component` | A utility worker — does specialized jobs | `OrderEventProducer`, `InventoryEventConsumer`, `InventoryGrpcClient`, `DataSeeder` |
| `@Configuration` | The restaurant manager — sets up the kitchen before opening | `MetricsConfig`, `KafkaTopicConfig`, `WebConfig` |

### HTTP Mapping Annotations

These tell Spring which HTTP requests should go to which methods — like routing phone calls to the right department.

```mermaid
graph TB
    subgraph "HTTP Request Routing"
        CLIENT["Client sends HTTP request"]

        CLIENT -->|"POST /api/orders"| POST["@PostMapping<br/>createOrder()<br/>'Create something new'"]
        CLIENT -->|"GET /api/orders/abc-123"| GET1["@GetMapping('/{id}')<br/>getOrder()<br/>'Fetch one item'"]
        CLIENT -->|"GET /api/orders"| GET2["@GetMapping<br/>listOrders()<br/>'Fetch a list'"]
        CLIENT -->|"PUT /api/orders/abc-123/cancel"| PUT["@PutMapping('/{id}/cancel')<br/>cancelOrder()<br/>'Update something'"]
    end

    subgraph "@RequestMapping('/api/orders')"
        NOTE["Sets the BASE path for all<br/>endpoints in the controller.<br/>Every @GetMapping, @PostMapping, etc.<br/>is relative to this base path."]
    end

    style POST fill:#c8e6c9
    style GET1 fill:#e3f2fd
    style GET2 fill:#e3f2fd
    style PUT fill:#fff3e0
```

| Annotation | What It Does | Example |
|---|---|---|
| `@RequestMapping("/api/orders")` | Sets the base URL path for all endpoints in the controller | Every endpoint starts with `/api/orders` |
| `@GetMapping` | Maps HTTP GET requests (reading data) | `GET /api/orders` returns a list of orders |
| `@GetMapping("/{id}")` | Maps GET requests with a dynamic path segment | `GET /api/orders/abc-123` returns one order |
| `@PostMapping` | Maps HTTP POST requests (creating data) | `POST /api/orders` creates a new order |
| `@PutMapping("/{id}/cancel")` | Maps HTTP PUT requests (updating data) | `PUT /api/orders/abc-123/cancel` cancels an order |
| `@PathVariable` | Extracts a value from the URL path | `/{id}` in the URL becomes the `UUID id` parameter |
| `@RequestBody` | Tells Spring to convert the JSON request body into a Java object | JSON `{"customerId": "john", ...}` becomes a `CreateOrderRequest` |
| `@RequestParam` | Extracts a query parameter from the URL | `?page=0&size=20` (used by `Pageable` automatically) |

### JPA / Database Annotations (Order Service — PostgreSQL)

These tell Spring how to map Java classes to database tables. Think of them as labels that say "this field is a column, this class is a table."

```mermaid
graph TB
    subgraph "Java Class → Database Table"
        subgraph "Order.java"
            CLS["@Entity<br/>@Table(name = 'orders')<br/>public class Order"]
            F1["@Id<br/>@GeneratedValue(strategy = UUID)<br/>private UUID id"]
            F2["@Column(nullable = false)<br/>private String customerId"]
            F3["@Enumerated(EnumType.STRING)<br/>private OrderStatus status"]
            F4["@OneToMany(mappedBy = 'order',<br/>cascade = ALL, orphanRemoval = true)<br/>private List<OrderItem> items"]
            F5["@PreUpdate<br/>void onUpdate()"]
        end

        subgraph "PostgreSQL Table: orders"
            T1["id (UUID, PRIMARY KEY)"]
            T2["customer_id (VARCHAR, NOT NULL)"]
            T3["status (VARCHAR, NOT NULL)<br/>'PENDING', 'CONFIRMED', etc."]
            T4["← Referenced by order_items table"]
            T5["← Runs before every UPDATE"]
        end

        F1 -.->|"maps to"| T1
        F2 -.->|"maps to"| T2
        F3 -.->|"maps to"| T3
        F4 -.->|"maps to"| T4
        F5 -.->|"triggers"| T5
    end

    style CLS fill:#e3f2fd
    style T1 fill:#e8f5e9
    style T2 fill:#e8f5e9
    style T3 fill:#e8f5e9
```

| Annotation | What It Does | Our Usage |
|---|---|---|
| `@Entity` | "This class maps to a database table" | `Order`, `OrderItem` |
| `@Table(name = "orders")` | Specifies the table name (needed because "order" is a reserved SQL word) | `Order` maps to `orders` table |
| `@Id` | "This field is the primary key" | `UUID id` in `Order` and `OrderItem` |
| `@GeneratedValue(strategy = UUID)` | "Auto-generate a UUID for new records" | Both `Order` and `OrderItem` |
| `@Column(nullable = false)` | "This column cannot be NULL in the database" | `customerId`, `status`, `productId`, `quantity`, `unitPrice` |
| `@Enumerated(EnumType.STRING)` | "Store enum values as their name ('PENDING'), not as numbers (0)" | `OrderStatus status` |
| `@OneToMany` | "One Order has many OrderItems" (one-to-many relationship) | `Order.items` |
| `@ManyToOne(fetch = LAZY)` | "Many OrderItems belong to one Order" (the other side of the relationship) | `OrderItem.order` |
| `@JoinColumn` | "This column holds the foreign key" | `order_id` in the `order_items` table |
| `@PreUpdate` | "Run this method automatically before any update" | `Order.onUpdate()` updates the `updatedAt` timestamp |

### MongoDB Annotations (Inventory Service)

```mermaid
graph TB
    subgraph "Java Class → MongoDB Document"
        subgraph "Product.java"
            CLS2["@Document(collection = 'products')<br/>public class Product"]
            M1["@Id<br/>private String id"]
            M2["@Indexed(unique = true)<br/>private String productId"]
            M3["@Version<br/>private Long version"]
        end

        subgraph "MongoDB Collection: products"
            D1["{<br/>  _id: 'auto-generated',<br/>  productId: 'prod-1',<br/>  name: 'Wireless Mouse',<br/>  currentStock: 500,<br/>  reservedStock: 0,<br/>  version: 1<br/>}"]
        end

        CLS2 -.->|"maps to"| D1
    end

    style CLS2 fill:#e3f2fd
    style D1 fill:#e8f5e9
```

| Annotation | What It Does | Our Usage |
|---|---|---|
| `@Document(collection = "products")` | "Store this class as documents in the 'products' MongoDB collection" (like `@Entity` but for MongoDB) | `Product` |
| `@Id` | "This field is the document's unique identifier" | `Product.id` |
| `@Indexed(unique = true)` | "Create a unique index on this field for fast lookups and uniqueness" | `Product.productId` |
| `@Version` | "Use this field for optimistic locking — reject saves if the version doesn't match" | `Product.version` (prevents two requests from overwriting each other) |

### Transaction and Data Fetching Annotations

| Annotation | What It Does | Our Usage |
|---|---|---|
| `@Transactional` | "Wrap this method in a database transaction — if anything fails, undo everything" | `OrderService.createOrder()`, `cancelOrder()`, `updateOrderStatus()` |
| `@Transactional(readOnly = true)` | "Read-only transaction — optimized for queries (no dirty-checking overhead)" | `OrderService.getOrder()`, `listOrders()` |
| `@EntityGraph(attributePaths = "items")` | "When loading orders, also load their items in the same query (avoid lazy loading issues)" | `OrderRepository.findAll()` |

### Configuration and Bean Annotations

| Annotation | What It Does | Our Usage |
|---|---|---|
| `@Configuration` | "This class defines beans and settings for the application" | `MetricsConfig`, `KafkaTopicConfig`, `WebConfig` |
| `@Bean` | "The object returned by this method should be managed by Spring" | Counters, Timers, Kafka topics |
| `@Profile("dev")` | "Only activate this bean when the 'dev' profile is active" | `DataSeeder` (seeds sample data in development only) |

### Messaging and gRPC Annotations

| Annotation | What It Does | Our Usage |
|---|---|---|
| `@KafkaListener(topics = "...")` | "Call this method automatically when a message arrives on this Kafka topic" | `InventoryEventConsumer.handleStockUpdated()` |
| `@GrpcService` | "This class is a gRPC server endpoint — like `@RestController` but for gRPC" | `InventoryGrpcServer` |
| `@GrpcClient("inventory-service")` | "Inject a gRPC client stub connected to the 'inventory-service'" | `InventoryGrpcClient.inventoryStub` |

### Validation Annotations

These act as automatic guards at the door — they reject bad data before it reaches your business logic.

```mermaid
graph TB
    subgraph "Validation Flow"
        REQ["Client sends JSON:<br/>{customerId: '', items: []}"]
        VALID["@Valid on @RequestBody<br/>triggers validation"]
        CHECK["Spring checks all validation<br/>annotations on the DTO fields"]

        NB["@NotBlank on customerId<br/>'Must not be blank'<br/>FAILS"]
        NE["@NotEmpty on items<br/>'Must have at least 1 item'<br/>FAILS"]

        ERR["Spring returns HTTP 400<br/>Bad Request with error details"]

        REQ --> VALID --> CHECK
        CHECK --> NB
        CHECK --> NE
        NB --> ERR
        NE --> ERR
    end

    style REQ fill:#ffcdd2
    style ERR fill:#ffcdd2
    style NB fill:#fff3e0
    style NE fill:#fff3e0
```

| Annotation | What It Does | Our Usage |
|---|---|---|
| `@Valid` | "Validate this object's fields before processing" | On `@RequestBody CreateOrderRequest` in the controller |
| `@NotBlank` | "Must not be null, empty, or just whitespace" | `CreateOrderRequest.customerId`, `OrderItemRequest.productId` |
| `@NotEmpty` | "List must not be null and must have at least one element" | `CreateOrderRequest.items` |
| `@Min(1)` | "Number must be at least 1" | `OrderItemRequest.quantity` |
| `@Positive` | "Number must be greater than zero" | `OrderItemRequest.unitPrice` |

---

## The Layered Architecture Pattern

Spring Boot applications follow a layered architecture — like a restaurant where each role has a clear responsibility and only talks to the next layer.

```mermaid
graph TB
    subgraph "The Layered Architecture"
        CLIENT["Client<br/>(Browser, Mobile App, curl)<br/>Sends HTTP requests"]

        subgraph "Controller Layer — The Waiter"
            CTRL["@RestController<br/>OrderController / ProductController<br/>━━━━━━━━━━━━━━━<br/>Receives HTTP requests<br/>Validates input (@Valid)<br/>Converts entities to DTOs<br/>Returns HTTP responses<br/>Handles exceptions"]
        end

        subgraph "Service Layer — The Chef"
            SVC["@Service<br/>OrderService / InventoryStockService<br/>━━━━━━━━━━━━━━━<br/>Contains business logic<br/>Orchestrates operations<br/>Manages transactions<br/>Calls other services (gRPC)<br/>Publishes events (Kafka)"]
        end

        subgraph "Repository Layer — The Pantry"
            REPO["JpaRepository / MongoRepository<br/>OrderRepository / ProductRepository<br/>━━━━━━━━━━━━━━━<br/>Reads from database<br/>Writes to database<br/>Auto-generates queries from method names"]
        end

        subgraph "Database — The Storage Room"
            DB["PostgreSQL (orders)<br/>MongoDB (products)"]
        end

        CLIENT -->|"HTTP Request<br/>POST /api/orders"| CTRL
        CTRL -->|"Calls service method<br/>orderService.createOrder(request)"| SVC
        SVC -->|"Calls repository<br/>orderRepository.save(order)"| REPO
        REPO -->|"SQL / MongoDB query"| DB
        DB -->|"Data"| REPO
        REPO -->|"Entity"| SVC
        SVC -->|"Entity"| CTRL
        CTRL -->|"HTTP Response<br/>JSON (OrderResponse DTO)"| CLIENT
    end

    style CLIENT fill:#e0e0e0
    style CTRL fill:#e3f2fd
    style SVC fill:#e8f5e9
    style REPO fill:#f3e5f5
    style DB fill:#fff3e0
```

**Why layers?** Each layer has exactly one responsibility:
- **Controller** does NOT contain business logic — it just receives requests and returns responses
- **Service** does NOT know about HTTP or JSON — it just processes business rules
- **Repository** does NOT know about business rules — it just reads and writes data

This means you can change one layer without breaking the others. For example, you could replace PostgreSQL with MySQL, and only the repository layer would need changes.

---

## Auto-Configuration Magic

The most powerful feature of Spring Boot is **auto-configuration** — it reads your `application.yml` and your dependencies, then automatically configures everything for you.

### How It Works

```mermaid
graph TB
    subgraph "Spring Boot Startup Process"
        POM["Step 1: Read pom.xml<br/>━━━━━━━━━━━━<br/>'I see spring-boot-starter-data-jpa<br/>and postgresql on the classpath...'"]

        YML["Step 2: Read application.yml<br/>━━━━━━━━━━━━<br/>'Database URL is<br/>jdbc:postgresql://localhost:5432/orders_db<br/>Username is orders_user'"]

        AUTO["Step 3: Auto-configure!<br/>━━━━━━━━━━━━<br/>'I'll create a DataSource connection pool,<br/>a JPA EntityManagerFactory,<br/>a TransactionManager,<br/>and configure Hibernate dialect<br/>for PostgreSQL.'<br/><br/>YOU WROTE ZERO CONFIG CODE."]
    end

    POM --> YML --> AUTO

    style POM fill:#e3f2fd
    style YML fill:#fff3e0
    style AUTO fill:#c8e6c9
```

### What Gets Auto-Configured in Our Project

```mermaid
graph TB
    subgraph "Order Service — What Spring Boot Auto-Configures"
        subgraph "From spring-boot-starter-web"
            W1["Embedded Tomcat server on port 8080"]
            W2["Jackson JSON serializer/deserializer"]
            W3["Exception handling framework"]
        end

        subgraph "From spring-boot-starter-data-jpa + postgresql"
            D1["HikariCP connection pool"]
            D2["Hibernate ORM configured for PostgreSQL"]
            D3["Transaction manager"]
            D4["Auto DDL updates (ddl-auto: update)"]
        end

        subgraph "From spring-kafka"
            K1["KafkaTemplate for sending messages"]
            K2["KafkaListenerContainerFactory for consumers"]
            K3["JSON serializer/deserializer"]
        end

        subgraph "From spring-boot-starter-actuator"
            A1["/actuator/health endpoint"]
            A2["/actuator/metrics endpoint"]
            A3["/actuator/prometheus endpoint"]
        end

        subgraph "From application.yml"
            Y1["spring.threads.virtual.enabled: true<br/>→ Uses Java 21 virtual threads"]
        end
    end

    style W1 fill:#e3f2fd
    style W2 fill:#e3f2fd
    style D1 fill:#e8f5e9
    style D2 fill:#e8f5e9
    style K1 fill:#fce4ec
    style K2 fill:#fce4ec
    style A1 fill:#fff3e0
    style Y1 fill:#f3e5f5
```

### application.yml — The Control Panel

The `application.yml` file is like the control panel for your restaurant. Every setting has a purpose:

```yaml
spring:
  application:
    name: order-service              # Name shown in logs and monitoring

  threads:
    virtual:
      enabled: true                  # Use Java 21 virtual threads for better performance

  datasource:
    url: jdbc:postgresql://localhost:5432/orders_db    # Where the database is
    username: orders_user                               # Login credentials
    password: orders_pass

  jpa:
    hibernate:
      ddl-auto: update               # Auto-create/update tables when the app starts
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect  # Tell Hibernate we're using PostgreSQL
    open-in-view: false              # Performance: don't keep DB sessions open during view rendering

  kafka:
    bootstrap-servers: localhost:29092   # Where Kafka is running
    producer:                            # How to SEND messages
      key-serializer: StringSerializer       # Keys are strings
      value-serializer: JsonSerializer       # Values are converted to JSON
    consumer:                            # How to RECEIVE messages
      group-id: order-service-group          # Consumer group identity
      auto-offset-reset: earliest            # Start reading from the beginning if new

server:
  port: 8080                         # HTTP port for REST API

grpc:
  client:
    inventory-service:
      address: static://localhost:9090    # Where the gRPC server is
      negotiation-type: plaintext         # No TLS (dev mode)

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus    # Which monitoring endpoints to expose
```

Spring Boot reads this file at startup and uses each value to configure the corresponding subsystem. You write configuration; Spring Boot writes the code that uses it.

---

## Spring Data JPA and Spring Data MongoDB — How Repositories Work

### The Magic of Method Names

The most mind-blowing feature of Spring Data is that **you write an interface with method names, and Spring generates the SQL/queries for you.** No implementation code needed.

```mermaid
graph TB
    subgraph "You write this (just an interface!)"
        IFACE["public interface OrderRepository<br/>extends JpaRepository<Order, UUID> {<br/><br/>  Page<Order> findByCustomerId(String customerId, Pageable pageable);<br/><br/>  Page<Order> findByStatus(OrderStatus status, Pageable pageable);<br/>}"]
    end

    subgraph "Spring generates this automatically at runtime"
        IMPL["Spring creates a class that implements OrderRepository<br/>━━━━━━━━━━━━━━━<br/>findByCustomerId → SELECT * FROM orders WHERE customer_id = ?<br/>findByStatus → SELECT * FROM orders WHERE status = ?<br/>save() → INSERT or UPDATE<br/>findById() → SELECT * FROM orders WHERE id = ?<br/>findAll() → SELECT * FROM orders<br/>delete() → DELETE FROM orders WHERE id = ?<br/>count() → SELECT COUNT(*) FROM orders"]
    end

    IFACE -->|"At startup, Spring sees this interface<br/>and generates the implementation"| IMPL

    style IFACE fill:#e8f5e9
    style IMPL fill:#e0e0e0
```

### How Method Names Become Queries

Spring Data parses the method name to build queries. It follows a pattern:

```mermaid
graph LR
    subgraph "Method Name Parsing"
        MN["findByCustomerId"]
        P1["find"] --> P2["By"]
        P2 --> P3["CustomerId"]

        MN2["findByStatus"]
        P4["find"] --> P5["By"]
        P5 --> P6["Status"]
    end

    subgraph "Generated Queries"
        Q1["SELECT * FROM orders<br/>WHERE customer_id = ?"]
        Q2["SELECT * FROM orders<br/>WHERE status = ?"]
    end

    P3 -->|"generates"| Q1
    P6 -->|"generates"| Q2

    style MN fill:#e8f5e9
    style MN2 fill:#e8f5e9
    style Q1 fill:#e3f2fd
    style Q2 fill:#e3f2fd
```

### JpaRepository vs MongoRepository

Our project uses both because we have two different databases:

```mermaid
graph TB
    subgraph "Order Service — JpaRepository (SQL/PostgreSQL)"
        JPA["OrderRepository extends JpaRepository<Order, UUID><br/>━━━━━━━━━━━━━━━<br/>Uses SQL under the hood<br/>Works with @Entity classes<br/>Supports @Transactional<br/>Supports @EntityGraph for JOIN FETCH<br/><br/>findByCustomerId → SQL WHERE clause<br/>save() → SQL INSERT/UPDATE"]
    end

    subgraph "Inventory Service — MongoRepository (NoSQL/MongoDB)"
        MONGO["ProductRepository extends MongoRepository<Product, String><br/>━━━━━━━━━━━━━━━<br/>Uses MongoDB queries under the hood<br/>Works with @Document classes<br/>Supports optimistic locking (@Version)<br/><br/>findByProductId → MongoDB query { productId: ? }<br/>save() → MongoDB insert/update"]
    end

    style JPA fill:#e3f2fd
    style MONGO fill:#e8f5e9
```

Both follow the same pattern: you declare an interface, Spring provides the implementation. The difference is only which database they talk to.

### @EntityGraph — Solving the Lazy Loading Problem

```mermaid
graph TB
    subgraph "The Problem: Lazy Loading"
        LAZY1["orderRepository.findAll() returns orders<br/>but items are NOT loaded yet (lazy)"]
        LAZY2["Transaction ends<br/>Database session closes"]
        LAZY3["Code tries to access order.getItems()"]
        LAZY4["LazyInitializationException!<br/>No database session to load items from!"]
        LAZY1 --> LAZY2 --> LAZY3 --> LAZY4
    end

    subgraph "The Solution: @EntityGraph"
        EG1["@EntityGraph(attributePaths = 'items')<br/>Page<Order> findAll(Pageable pageable)"]
        EG2["Spring generates:<br/>SELECT * FROM orders<br/>LEFT JOIN order_items ON ...<br/><br/>Items are loaded in the SAME query!"]
        EG3["Transaction ends — but items<br/>are already loaded. No problem!"]
        EG1 --> EG2 --> EG3
    end

    style LAZY4 fill:#ffcdd2
    style EG3 fill:#c8e6c9
```

---

## Design Patterns in Spring Boot

Our project uses several well-known design patterns. Here is each one with an explanation of how it appears in the code.

### 1. Repository Pattern

**What:** Hides database access behind a clean interface. The service layer never writes SQL directly.

**In our project:** `OrderRepository` and `ProductRepository` are interfaces. The service calls `orderRepository.save(order)` without knowing whether it is PostgreSQL, MySQL, or an in-memory database.

### 2. Service Layer Pattern

**What:** All business logic lives in a dedicated layer, separate from HTTP handling and data access.

**In our project:** `OrderService` contains the logic for creating orders (check stock, save order, reserve stock, publish events). The controller just calls `orderService.createOrder(request)` and returns the result.

### 3. DTO Pattern (Data Transfer Object)

**What:** Separate objects for what the API receives/returns vs. what is stored in the database. This prevents exposing internal database structure to clients.

```mermaid
graph LR
    subgraph "API Layer (what clients see)"
        REQ["CreateOrderRequest (DTO)<br/>━━━━━━━━━━━━<br/>customerId<br/>items[]"]
        RES["OrderResponse (DTO)<br/>━━━━━━━━━━━━<br/>id, customerId, status,<br/>totalAmount, items[], createdAt"]
    end

    subgraph "Database Layer (internal)"
        ENT["Order (Entity)<br/>━━━━━━━━━━━━<br/>id, customerId, status,<br/>totalAmount, createdAt,<br/>updatedAt, items[]<br/>(+ JPA relationships,<br/>version fields, etc.)"]
    end

    REQ -->|"Controller converts<br/>DTO → Entity"| ENT
    ENT -->|"OrderResponse.from(order)<br/>Entity → DTO"| RES

    style REQ fill:#e3f2fd
    style RES fill:#e3f2fd
    style ENT fill:#e8f5e9
```

**In our project:**
- `CreateOrderRequest` and `OrderItemRequest` are input DTOs (what the client sends)
- `OrderResponse` is the output DTO (what the client receives)
- `Order` and `OrderItem` are entities (what is stored in the database)

The `OrderResponse.from(order)` factory method converts from entity to DTO.

### 4. Factory Method Pattern

**What:** A static method that creates objects, encapsulating the creation logic in one place.

**In our project:** `OrderResponse.from(Order order)` is a factory method that converts an `Order` entity to an `OrderResponse` DTO. Every controller uses it instead of manually mapping fields.

### 5. Observer Pattern (Kafka Events)

**What:** When something happens, interested parties are notified automatically, without the publisher knowing who they are.

```mermaid
graph TB
    subgraph "Observer Pattern via Kafka"
        PUB["Order Service<br/>(Publisher)<br/>━━━━━━━━━━━━<br/>Publishes 'OrderPlaced'<br/>Doesn't know who listens!"]

        KAFKA["Kafka Topic: order-events<br/>(The bulletin board)"]

        SUB1["Inventory Service<br/>(Subscriber 1)<br/>━━━━━━━━━━━━<br/>Reserves stock"]

        SUB2["Future: Notification Service<br/>(Subscriber 2)<br/>━━━━━━━━━━━━<br/>Sends email confirmation"]

        SUB3["Future: Analytics Service<br/>(Subscriber 3)<br/>━━━━━━━━━━━━<br/>Records metrics"]

        PUB -->|"publishes"| KAFKA
        KAFKA -->|"notifies"| SUB1
        KAFKA -.->|"could notify"| SUB2
        KAFKA -.->|"could notify"| SUB3
    end

    NOTE["Adding new subscribers<br/>requires ZERO changes to<br/>the publisher!"]

    style PUB fill:#e8f5e9
    style KAFKA fill:#fce4ec
    style SUB1 fill:#e3f2fd
    style SUB2 fill:#e0e0e0
    style SUB3 fill:#e0e0e0
    style NOTE fill:#fff9c4,stroke:none
```

### 6. Template Method Pattern (KafkaTemplate)

**What:** A pre-built class that handles the boilerplate and lets you fill in the specifics.

**In our project:** `KafkaTemplate<String, Object>` handles serialization, partition selection, network communication, and error handling. You just call `kafkaTemplate.send(topic, key, event)` — the template handles the rest. Similarly, `JpaRepository` is a template that provides `save()`, `findById()`, `findAll()`, and `delete()` without you implementing them.

### 7. Proxy Pattern (How @Transactional Works Behind the Scenes)

**What:** Spring wraps your class in an invisible "proxy" that adds behavior before and after your methods.

```mermaid
graph TB
    subgraph "What you think happens"
        DIRECT["Controller calls<br/>orderService.createOrder()"]
    end

    subgraph "What actually happens"
        PROXY["Controller calls → PROXY<br/>(Spring-generated wrapper)<br/>━━━━━━━━━━━━<br/>1. Open database transaction<br/>2. Call the REAL createOrder()<br/>3a. If success → COMMIT transaction<br/>3b. If exception → ROLLBACK transaction"]
        REAL["Real OrderService.createOrder()<br/>(your actual code runs here)"]

        PROXY -->|"delegates to"| REAL
    end

    DIRECT -.->|"actually goes through"| PROXY

    style DIRECT fill:#e3f2fd
    style PROXY fill:#fff3e0
    style REAL fill:#e8f5e9
```

When you put `@Transactional` on a method, Spring does not modify your class directly. Instead, at startup, it creates a **proxy** — a wrapper class that looks identical to yours but adds transaction management around every call. The controller never touches your real `OrderService` directly; it always goes through the proxy.

### 8. Optimistic Locking Pattern

**What:** Instead of locking a database row (preventing others from reading it), you let everyone read and write freely, but detect conflicts using a version number.

**In our project:** The `Product` class has a `@Version` field. When two requests try to reserve stock at the same time:

```mermaid
sequenceDiagram
    participant R1 as Request 1
    participant R2 as Request 2
    participant DB as MongoDB

    R1->>DB: Read product (version = 1)
    R2->>DB: Read product (version = 1)

    R1->>DB: Save product (version = 1 → 2)
    Note over DB: version matches! Save succeeds.

    R2->>DB: Save product (version = 1 → 2)
    Note over DB: version is now 2, not 1!<br/>OptimisticLockingFailureException!

    Note over R2: Catches exception,<br/>returns false (reservation failed).<br/>No data corruption!
```

---

## Bean Lifecycle — How Spring Creates and Manages Objects

A **bean** is any object that Spring creates and manages. Every class annotated with `@Service`, `@Component`, `@Configuration`, `@RestController`, or `@Repository` becomes a bean.

```mermaid
graph TB
    subgraph "Bean Lifecycle"
        CREATE["1. INSTANTIATION<br/>━━━━━━━━━━━━<br/>Spring calls the constructor<br/>and injects dependencies.<br/><br/>new OrderService(orderRepo,<br/>inventoryClient, eventProducer, ...)"]

        INIT["2. INITIALIZATION<br/>━━━━━━━━━━━━<br/>Spring calls any @PostConstruct<br/>methods or CommandLineRunner.run().<br/><br/>DataSeeder.run() seeds sample data."]

        READY["3. READY<br/>━━━━━━━━━━━━<br/>Bean is fully initialized<br/>and available for use.<br/>Handles requests, processes<br/>messages, etc."]

        DESTROY["4. DESTRUCTION<br/>━━━━━━━━━━━━<br/>When the app shuts down,<br/>Spring calls @PreDestroy methods.<br/>Closes database connections,<br/>flushes logs, etc."]

        CREATE --> INIT --> READY --> DESTROY
    end

    NOTE["By default, Spring creates exactly<br/>ONE instance of each bean (singleton).<br/>The same OrderService object handles<br/>ALL requests — it's shared."]

    style CREATE fill:#e3f2fd
    style INIT fill:#fff3e0
    style READY fill:#c8e6c9
    style DESTROY fill:#ffcdd2
    style NOTE fill:#fff9c4,stroke:none
```

### The Singleton Scope

By default, every Spring bean is a **singleton** — there is only one instance in the entire application. This is efficient because:
- Database connection pools are shared
- Configuration objects are loaded once
- Service objects are stateless (they don't hold per-request data)

In our project, there is exactly one `OrderService` instance handling all HTTP requests. This is safe because `OrderService` is stateless — it doesn't store any request-specific data in its fields. Each request gets its own data through method parameters.

---

## Error Handling

Our project uses `@ExceptionHandler` methods in the controller to convert exceptions into proper HTTP responses.

```mermaid
graph TB
    subgraph "Error Handling Flow"
        REQ["Client sends:<br/>POST /api/orders<br/>with product that has no stock"]

        SVC["OrderService.createOrder()<br/>throws InsufficientStockException"]

        subgraph "Exception Handlers in OrderController"
            H1["@ExceptionHandler(OrderNotFoundException.class)<br/>→ Returns HTTP 404 (Not Found)"]
            H2["@ExceptionHandler(InsufficientStockException.class)<br/>→ Returns HTTP 422 (Unprocessable Entity)"]
            H3["@ExceptionHandler(IllegalStateException.class)<br/>→ Returns HTTP 409 (Conflict)"]
        end

        RES["Client receives:<br/>HTTP 422<br/>'Insufficient stock for product: prod-1'"]

        REQ --> SVC
        SVC -->|"exception bubbles up"| H2
        H2 --> RES
    end

    style REQ fill:#e3f2fd
    style SVC fill:#ffcdd2
    style H2 fill:#fff3e0
    style RES fill:#e8f5e9
```

The `@ExceptionHandler` annotation tells Spring: "When this exception is thrown by any method in this controller, catch it and call this handler method instead of returning a generic 500 error."

| Exception | HTTP Status | When It Happens |
|---|---|---|
| `OrderNotFoundException` | 404 Not Found | Order with the given ID does not exist |
| `InsufficientStockException` | 422 Unprocessable Entity | Not enough stock to fulfill the order |
| `IllegalStateException` | 409 Conflict | Trying to cancel an order that cannot be cancelled (already shipped, etc.) |

---

## Configuration Properties — How application.yml Maps to Java

Spring Boot takes the YAML configuration and injects values into your application. There are several ways this happens:

```mermaid
graph TB
    subgraph "application.yml"
        Y1["spring.datasource.url:<br/>jdbc:postgresql://localhost:5432/orders_db"]
        Y2["server.port: 8080"]
        Y3["spring.kafka.bootstrap-servers:<br/>localhost:29092"]
        Y4["grpc.client.inventory-service.address:<br/>static://localhost:9090"]
        Y5["management.endpoints.web.exposure.include:<br/>health,info,metrics,prometheus"]
    end

    subgraph "What Spring Does With Each"
        A1["Auto-configures HikariCP<br/>connection pool with this URL"]
        A2["Starts embedded Tomcat<br/>on this port"]
        A3["Configures KafkaTemplate<br/>and consumers with this server"]
        A4["Configures @GrpcClient<br/>to connect to this address"]
        A5["Exposes only these<br/>actuator endpoints"]
    end

    Y1 --> A1
    Y2 --> A2
    Y3 --> A3
    Y4 --> A4
    Y5 --> A5

    style Y1 fill:#e3f2fd
    style Y2 fill:#e3f2fd
    style Y3 fill:#e3f2fd
    style Y4 fill:#e3f2fd
    style Y5 fill:#e3f2fd
    style A1 fill:#e8f5e9
    style A2 fill:#e8f5e9
    style A3 fill:#e8f5e9
    style A4 fill:#e8f5e9
    style A5 fill:#e8f5e9
```

### How Spring Selects What to Auto-Configure

Spring Boot looks at your **classpath** (the dependencies in `pom.xml`) to decide what to configure:

```mermaid
graph TB
    subgraph "Dependency → Auto-Configuration"
        D1["spring-boot-starter-web<br/>on classpath?"] -->|"YES"| A1["Start Tomcat,<br/>configure Jackson JSON,<br/>set up MVC"]
        D2["spring-boot-starter-data-jpa<br/>+ postgresql on classpath?"] -->|"YES"| A2["Create DataSource,<br/>EntityManagerFactory,<br/>TransactionManager"]
        D3["spring-kafka<br/>on classpath?"] -->|"YES"| A3["Create KafkaTemplate,<br/>KafkaListenerContainerFactory"]
        D4["spring-boot-starter-actuator<br/>on classpath?"] -->|"YES"| A4["Create /actuator endpoints"]
        D5["spring-boot-starter-data-mongodb<br/>on classpath?"] -->|"YES"| A5["Create MongoTemplate,<br/>MongoClient"]
        D6["micrometer-registry-prometheus<br/>on classpath?"] -->|"YES"| A6["Create /actuator/prometheus<br/>metrics exporter"]
    end

    style D1 fill:#e3f2fd
    style D2 fill:#e3f2fd
    style D3 fill:#e3f2fd
    style D4 fill:#e3f2fd
    style D5 fill:#e3f2fd
    style D6 fill:#e3f2fd
    style A1 fill:#c8e6c9
    style A2 fill:#c8e6c9
    style A3 fill:#c8e6c9
    style A4 fill:#c8e6c9
    style A5 fill:#c8e6c9
    style A6 fill:#c8e6c9
```

---

## Complete Spring Boot Flow — From HTTP Request to Response

Here is the complete journey of an HTTP request through all the Spring Boot layers when a client creates a new order:

```mermaid
sequenceDiagram
    participant C as Client (Browser)
    participant T as Embedded Tomcat
    participant F as Filters & Validation
    participant CTRL as OrderController
    participant PROXY as Transactional Proxy
    participant SVC as OrderService
    participant GRPC as InventoryGrpcClient
    participant INV as Inventory Service (gRPC)
    participant REPO as OrderRepository
    participant DB as PostgreSQL
    participant KAFKA as KafkaTemplate
    participant KB as Kafka Broker

    C->>T: POST /api/orders<br/>{"customerId": "john", "items": [...]}

    Note over T: Tomcat receives the HTTP request<br/>and routes it to Spring MVC

    T->>F: Deserialize JSON → CreateOrderRequest<br/>Run @Valid validation
    Note over F: @NotBlank checks customerId<br/>@NotEmpty checks items list<br/>@Min(1) checks quantities

    F->>CTRL: createOrder(@Valid @RequestBody request)

    CTRL->>PROXY: orderService.createOrder(request)
    Note over PROXY: @Transactional proxy:<br/>BEGIN TRANSACTION

    PROXY->>SVC: createOrder(request)

    loop For each item in the order
        SVC->>GRPC: checkStock(productId, quantity)
        GRPC->>INV: gRPC call (binary, HTTP/2)
        INV-->>GRPC: available = true
        GRPC-->>SVC: true
    end

    SVC->>REPO: orderRepository.save(order)
    REPO->>DB: INSERT INTO orders ...
    DB-->>REPO: saved order with generated UUID
    REPO-->>SVC: Order entity

    loop For each item
        SVC->>GRPC: reserveStock(productId, quantity, orderId)
        GRPC->>INV: gRPC call
        INV-->>GRPC: success = true
    end

    SVC->>KAFKA: kafkaTemplate.send("order-events", orderId, event)
    KAFKA->>KB: Publish OrderPlacedEvent (async)

    SVC-->>PROXY: return saved order

    Note over PROXY: @Transactional proxy:<br/>COMMIT TRANSACTION

    PROXY-->>CTRL: Order entity

    Note over CTRL: OrderResponse.from(order)<br/>Entity → DTO conversion

    CTRL-->>T: ResponseEntity<OrderResponse><br/>HTTP 201 Created

    T-->>C: HTTP 201<br/>{"id": "abc-123", "status": "PENDING", ...}
```

---

## Key Source Files

| File | What It Is | Key Annotations |
|---|---|---|
| `OrderServiceApplication.java` | Entry point — boots the entire application | `@SpringBootApplication` |
| `OrderController.java` | HTTP endpoint handler — the waiter | `@RestController`, `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@ExceptionHandler` |
| `OrderService.java` | Business logic — the chef | `@Service`, `@Transactional` |
| `OrderRepository.java` | Database access for orders | `JpaRepository`, `@EntityGraph` |
| `Order.java` | Database entity for orders | `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@Column`, `@OneToMany`, `@Enumerated`, `@PreUpdate` |
| `OrderItem.java` | Database entity for order line items | `@Entity`, `@Table`, `@Id`, `@ManyToOne`, `@JoinColumn` |
| `CreateOrderRequest.java` | Input DTO for order creation | `@NotBlank`, `@NotEmpty`, `@Valid` |
| `OrderItemRequest.java` | Input DTO for order items | `@NotBlank`, `@Min`, `@Positive` |
| `OrderResponse.java` | Output DTO for API responses | Factory method pattern |
| `MetricsConfig.java` | Defines monitoring metrics | `@Configuration`, `@Bean` |
| `KafkaTopicConfig.java` | Creates Kafka topics at startup | `@Configuration`, `@Bean` |
| `WebConfig.java` | CORS configuration | `@Configuration`, implements `WebMvcConfigurer` |
| `OrderEventProducer.java` | Sends events to Kafka | `@Component`, uses `KafkaTemplate` |
| `InventoryEventConsumer.java` | Receives events from Kafka | `@Component`, `@KafkaListener` |
| `InventoryGrpcClient.java` | gRPC client for Inventory Service | `@Component`, `@GrpcClient` |
| `InventoryStockService.java` | Inventory business logic | `@Service` |
| `ProductController.java` | HTTP endpoint for products | `@RestController`, `@GetMapping` |
| `ProductRepository.java` | MongoDB access for products | `MongoRepository` |
| `Product.java` | MongoDB document for products | `@Document`, `@Id`, `@Indexed`, `@Version` |
| `InventoryGrpcServer.java` | gRPC server for stock checks | `@GrpcService` |
| `DataSeeder.java` | Seeds sample data in dev mode | `@Component`, `@Profile("dev")`, `CommandLineRunner` |
| `application.yml` (both services) | All configuration settings | Read by Spring Boot auto-configuration |

---

## Summary — Spring Boot in One Picture

```mermaid
graph TB
    subgraph "The Story of Spring Boot"
        A["You write annotated Java classes<br/>@Service, @RestController, @Repository, @Configuration"]
        B["Spring Boot scans your code at startup<br/>and finds all annotated classes"]
        C["It reads application.yml<br/>for database URLs, ports, Kafka servers, etc."]
        D["It checks your dependencies (pom.xml)<br/>and auto-configures everything:<br/>web server, database, Kafka, gRPC, metrics"]
        E["It creates all beans (objects),<br/>resolves dependencies between them,<br/>and wires everything together"]
        F["Your application is ready!<br/>All you wrote was business logic<br/>and a few configuration lines."]

        A --> B --> C --> D --> E --> F
    end

    style A fill:#e3f2fd
    style B fill:#fff3e0
    style C fill:#f3e5f5
    style D fill:#fce4ec
    style E fill:#e8f5e9
    style F fill:#c8e6c9
```

**The key insight:** Spring Boot inverts the traditional approach. Instead of you configuring and creating everything, you just declare what you need (via annotations and YAML), and Spring Boot figures out how to wire it all together. You focus on business logic; Spring Boot handles the infrastructure.
