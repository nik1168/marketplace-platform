# Marketplace Platform Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a two-service Spring Boot marketplace platform demonstrating Java 21 virtual threads, Kafka, gRPC, PostgreSQL, and MongoDB.

**Architecture:** Order Service (REST + PostgreSQL) communicates with Inventory Service (MongoDB) via gRPC for sync stock checks and Kafka for async event-driven inventory updates. Virtual threads handle all concurrent I/O.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Spring Data JPA, Spring Data MongoDB, Spring Kafka, grpc-spring-boot-starter, PostgreSQL 16, MongoDB 7, Kafka (KRaft), Maven multi-module, Testcontainers, JUnit 5, Micrometer.

**Design doc:** `docs/plans/2026-03-12-marketplace-platform-design.md`

---

### Task 1: Scaffold Maven Multi-Module Project

**Files:**
- Create: `marketplace-platform/pom.xml`
- Create: `marketplace-platform/order-service/pom.xml`
- Create: `marketplace-platform/inventory-service/pom.xml`

**Step 1: Create parent POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.marketplace</groupId>
    <artifactId>marketplace-platform</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>Marketplace Platform</name>

    <modules>
        <module>order-service</module>
        <module>inventory-service</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <spring-kafka.version>3.2.4</spring-kafka.version>
        <grpc.version>1.64.0</grpc.version>
        <grpc-spring-boot.version>3.1.0.RELEASE</grpc-spring-boot.version>
        <protobuf.version>3.25.3</protobuf.version>
        <testcontainers.version>1.20.1</testcontainers.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.testcontainers</groupId>
                <artifactId>testcontainers-bom</artifactId>
                <version>${testcontainers.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

**Step 2: Create order-service POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.marketplace</groupId>
        <artifactId>marketplace-platform</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>order-service</artifactId>
    <name>Order Service</name>

    <dependencies>
        <!-- Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- JPA + PostgreSQL -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Kafka -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>

        <!-- gRPC client -->
        <dependency>
            <groupId>net.devh</groupId>
            <artifactId>grpc-client-spring-boot-starter</artifactId>
            <version>${grpc-spring-boot.version}</version>
        </dependency>

        <!-- Observability -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>kafka</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step 3: Create inventory-service POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.marketplace</groupId>
        <artifactId>marketplace-platform</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>inventory-service</artifactId>
    <name>Inventory Service</name>

    <dependencies>
        <!-- Web (for actuator) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- MongoDB -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb</artifactId>
        </dependency>

        <!-- Kafka -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>

        <!-- gRPC server -->
        <dependency>
            <groupId>net.devh</groupId>
            <artifactId>grpc-server-spring-boot-starter</artifactId>
            <version>${grpc-spring-boot.version}</version>
        </dependency>

        <!-- Observability -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>mongodb</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>kafka</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step 4: Verify project compiles**

Run: `cd marketplace-platform && mvn clean compile`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add marketplace-platform/
git commit -m "feat: scaffold Maven multi-module project with order-service and inventory-service"
```

---

### Task 2: Docker Compose for Infrastructure

**Files:**
- Create: `marketplace-platform/docker-compose.yml`

**Step 1: Create docker-compose.yml**

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: marketplace-postgres
    environment:
      POSTGRES_DB: orders_db
      POSTGRES_USER: orders_user
      POSTGRES_PASSWORD: orders_pass
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  mongodb:
    image: mongo:7.0
    container_name: marketplace-mongodb
    environment:
      MONGO_INITDB_DATABASE: inventory_db
    ports:
      - "27017:27017"
    volumes:
      - mongo_data:/data/db

  kafka:
    image: apache/kafka:3.7.0
    container_name: marketplace-kafka
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093,EXTERNAL://0.0.0.0:29092
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,EXTERNAL://localhost:29092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
    ports:
      - "29092:29092"

volumes:
  postgres_data:
  mongo_data:
```

**Step 2: Verify infrastructure starts**

Run: `docker compose up -d && docker compose ps`
Expected: All 3 containers running (postgres, mongodb, kafka)

**Step 3: Tear down**

Run: `docker compose down`

**Step 4: Commit**

```bash
git add marketplace-platform/docker-compose.yml
git commit -m "infra: add Docker Compose for PostgreSQL, MongoDB, and Kafka (KRaft)"
```

---

### Task 3: Order Service — Application Bootstrap + Virtual Threads

**Files:**
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/OrderServiceApplication.java`
- Create: `marketplace-platform/order-service/src/main/resources/application.yml`
- Create: `marketplace-platform/order-service/src/test/java/com/marketplace/order/OrderServiceApplicationTests.java`

**Step 1: Create application entry point**

```java
package com.marketplace.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

**Step 2: Create application.yml with virtual threads enabled**

```yaml
spring:
  application:
    name: order-service
  threads:
    virtual:
      enabled: true
  datasource:
    url: jdbc:postgresql://localhost:5432/orders_db
    username: orders_user
    password: orders_pass
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
    open-in-view: false
  kafka:
    bootstrap-servers: localhost:29092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: order-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.marketplace.*"

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

**Step 3: Create basic smoke test**

```java
package com.marketplace.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class OrderServiceApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void contextLoads() {
    }
}
```

**Step 4: Run test to verify context loads**

Run: `cd marketplace-platform && mvn test -pl order-service`
Expected: BUILD SUCCESS, contextLoads passes

**Step 5: Commit**

```bash
git add marketplace-platform/order-service/
git commit -m "feat(order): bootstrap Order Service with virtual threads and Testcontainers"
```

---

### Task 4: Order Service — JPA Entities and Repository

**Files:**
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/model/Order.java`
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/model/OrderItem.java`
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/model/OrderStatus.java`
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/repository/OrderRepository.java`
- Create: `marketplace-platform/order-service/src/test/java/com/marketplace/order/repository/OrderRepositoryTest.java`

**Step 1: Create OrderStatus enum**

```java
package com.marketplace.order.model;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    SHIPPED,
    CANCELLED
}
```

**Step 2: Create Order entity**

```java
package com.marketplace.order.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public Order() {}

    public Order(String customerId) {
        this.customerId = customerId;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
        recalculateTotal();
    }

    private void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and setters
    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderItem> getItems() { return items; }
}
```

**Step 3: Create OrderItem entity**

```java
package com.marketplace.order.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    public OrderItem() {}

    public OrderItem(String productId, int quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    // Getters and setters
    public UUID getId() { return id; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
```

**Step 4: Create OrderRepository**

```java
package com.marketplace.order.repository;

import com.marketplace.order.model.Order;
import com.marketplace.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Page<Order> findByCustomerId(String customerId, Pageable pageable);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
}
```

**Step 5: Write repository integration test**

```java
package com.marketplace.order.repository;

import com.marketplace.order.model.Order;
import com.marketplace.order.model.OrderItem;
import com.marketplace.order.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void shouldSaveAndRetrieveOrderWithItems() {
        Order order = new Order("customer-1");
        order.addItem(new OrderItem("product-1", 2, new BigDecimal("29.99")));
        order.addItem(new OrderItem("product-2", 1, new BigDecimal("49.99")));

        Order saved = orderRepository.save(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("109.97"));
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void shouldFindByCustomerId() {
        Order order = new Order("customer-2");
        order.addItem(new OrderItem("product-1", 1, new BigDecimal("10.00")));
        orderRepository.save(order);

        Page<Order> result = orderRepository.findByCustomerId("customer-2", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCustomerId()).isEqualTo("customer-2");
    }
}
```

**Step 6: Run tests**

Run: `cd marketplace-platform && mvn test -pl order-service`
Expected: All tests pass

**Step 7: Commit**

```bash
git add marketplace-platform/order-service/
git commit -m "feat(order): add Order/OrderItem JPA entities and repository with tests"
```

---

### Task 5: Order Service — DTOs and REST Controller

**Files:**
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/dto/CreateOrderRequest.java`
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/dto/OrderItemRequest.java`
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/dto/OrderResponse.java`
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/service/OrderService.java`
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/controller/OrderController.java`
- Create: `marketplace-platform/order-service/src/test/java/com/marketplace/order/controller/OrderControllerTest.java`

**Step 1: Create request/response DTOs**

```java
// CreateOrderRequest.java
package com.marketplace.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotEmpty @Valid List<OrderItemRequest> items
) {}
```

```java
// OrderItemRequest.java
package com.marketplace.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record OrderItemRequest(
        @NotBlank String productId,
        @Min(1) int quantity,
        @Positive BigDecimal unitPrice
) {}
```

```java
// OrderResponse.java
package com.marketplace.order.dto;

import com.marketplace.order.model.Order;
import com.marketplace.order.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        List<ItemResponse> items,
        Instant createdAt
) {
    public record ItemResponse(String productId, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {}

    public static OrderResponse from(Order order) {
        List<ItemResponse> items = order.getItems().stream()
                .map(i -> new ItemResponse(i.getProductId(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal()))
                .toList();
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getStatus(),
                order.getTotalAmount(), items, order.getCreatedAt());
    }
}
```

**Step 2: Create OrderService (business logic, no Kafka/gRPC yet — stubs)**

```java
package com.marketplace.order.service;

import com.marketplace.order.dto.CreateOrderRequest;
import com.marketplace.order.model.Order;
import com.marketplace.order.model.OrderItem;
import com.marketplace.order.model.OrderStatus;
import com.marketplace.order.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order(request.customerId());
        request.items().forEach(item ->
                order.addItem(new OrderItem(item.productId(), item.quantity(), item.unitPrice()))
        );
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public Page<Order> listOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Transactional
    public Order cancelOrder(UUID orderId) {
        Order order = getOrder(orderId);
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot cancel order in status: " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(UUID id) {
            super("Order not found: " + id);
        }
    }
}
```

**Step 3: Create REST controller**

```java
package com.marketplace.order.controller;

import com.marketplace.order.dto.CreateOrderRequest;
import com.marketplace.order.dto.OrderResponse;
import com.marketplace.order.model.Order;
import com.marketplace.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable UUID id) {
        return OrderResponse.from(orderService.getOrder(id));
    }

    @GetMapping
    public Page<OrderResponse> listOrders(Pageable pageable) {
        return orderService.listOrders(pageable).map(OrderResponse::from);
    }

    @PutMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable UUID id) {
        return OrderResponse.from(orderService.cancelOrder(id));
    }

    @ExceptionHandler(OrderService.OrderNotFoundException.class)
    public ResponseEntity<String> handleNotFound(OrderService.OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }
}
```

**Step 4: Write controller integration test**

```java
package com.marketplace.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.order.dto.CreateOrderRequest;
import com.marketplace.order.dto.OrderItemRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateOrder() throws Exception {
        var request = new CreateOrderRequest("customer-1", List.of(
                new OrderItemRequest("product-1", 2, new BigDecimal("29.99"))
        ));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value("customer-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].productId").value("product-1"));
    }

    @Test
    void shouldReturn404ForNonExistentOrder() throws Exception {
        mockMvc.perform(get("/api/orders/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCancelOrder() throws Exception {
        var request = new CreateOrderRequest("customer-1", List.of(
                new OrderItemRequest("product-1", 1, new BigDecimal("10.00"))
        ));

        String response = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();

        String orderId = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(put("/api/orders/" + orderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
```

**Step 5: Run tests**

Run: `cd marketplace-platform && mvn test -pl order-service`
Expected: All tests pass

**Step 6: Commit**

```bash
git add marketplace-platform/order-service/
git commit -m "feat(order): add REST API with DTOs, service layer, and controller tests"
```

---

### Task 6: Protobuf Definition + gRPC Code Generation

**Files:**
- Create: `marketplace-platform/proto/inventory.proto`
- Modify: `marketplace-platform/order-service/pom.xml` (add protobuf plugin + proto dependency)
- Modify: `marketplace-platform/inventory-service/pom.xml` (add protobuf plugin + proto dependency)

**Step 1: Create protobuf definition**

```protobuf
// marketplace-platform/proto/inventory.proto
syntax = "proto3";

package com.marketplace.inventory.grpc;

option java_multiple_files = true;
option java_package = "com.marketplace.inventory.grpc";

service InventoryService {
  rpc CheckStock (CheckStockRequest) returns (CheckStockResponse);
  rpc ReserveStock (ReserveStockRequest) returns (ReserveStockResponse);
}

message CheckStockRequest {
  string product_id = 1;
  int32 quantity = 2;
}

message CheckStockResponse {
  bool available = 1;
  int32 current_stock = 2;
}

message ReserveStockRequest {
  string product_id = 1;
  int32 quantity = 2;
  string order_id = 3;
}

message ReserveStockResponse {
  bool success = 1;
  string message = 2;
}
```

**Step 2: Add protobuf-maven-plugin to both service POMs**

Add to the `<build><plugins>` section of both `order-service/pom.xml` and `inventory-service/pom.xml`:

```xml
<plugin>
    <groupId>org.xolstice.maven.plugins</groupId>
    <artifactId>protobuf-maven-plugin</artifactId>
    <version>0.6.1</version>
    <configuration>
        <protocArtifact>com.google.protobuf:protoc:${protobuf.version}:exe:${os.detected.classifier}</protocArtifact>
        <pluginId>grpc-java</pluginId>
        <pluginArtifact>io.grpc:protoc-gen-grpc-java:${grpc.version}:exe:${os.detected.classifier}</pluginArtifact>
        <protoSourceRoot>${project.basedir}/../proto</protoSourceRoot>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>compile</goal>
                <goal>compile-custom</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Also add os-maven-plugin extension to both POMs:

```xml
<build>
    <extensions>
        <extension>
            <groupId>kr.motd.maven</groupId>
            <artifactId>os-maven-plugin</artifactId>
            <version>1.7.1</version>
        </extension>
    </extensions>
    ...
</build>
```

And add protobuf/grpc dependencies to both POMs:

```xml
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-protobuf</artifactId>
    <version>${grpc.version}</version>
</dependency>
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-stub</artifactId>
    <version>${grpc.version}</version>
</dependency>
<dependency>
    <groupId>jakarta.annotation</groupId>
    <artifactId>jakarta.annotation-api</artifactId>
</dependency>
```

**Step 3: Verify gRPC code generation**

Run: `cd marketplace-platform && mvn clean compile`
Expected: BUILD SUCCESS, generated gRPC stubs in `target/generated-sources/protobuf/`

**Step 4: Commit**

```bash
git add marketplace-platform/
git commit -m "feat: add protobuf definition and gRPC code generation for both services"
```

---

### Task 7: Inventory Service — Application Bootstrap + MongoDB Entities

**Files:**
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/InventoryServiceApplication.java`
- Create: `marketplace-platform/inventory-service/src/main/resources/application.yml`
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/model/Product.java`
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/model/StockEvent.java`
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/model/StockEventType.java`
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/repository/ProductRepository.java`
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/repository/StockEventRepository.java`
- Create: `marketplace-platform/inventory-service/src/test/java/com/marketplace/inventory/repository/ProductRepositoryTest.java`

**Step 1: Create application entry point**

```java
package com.marketplace.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
```

**Step 2: Create application.yml**

```yaml
spring:
  application:
    name: inventory-service
  threads:
    virtual:
      enabled: true
  data:
    mongodb:
      uri: mongodb://localhost:27017/inventory_db
  kafka:
    bootstrap-servers: localhost:29092
    consumer:
      group-id: inventory-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.marketplace.*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

server:
  port: 8081

grpc:
  server:
    port: 9090

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

**Step 3: Create MongoDB documents**

```java
// Product.java
package com.marketplace.inventory.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "products")
public class Product {

    @Id
    private String id;

    @Indexed(unique = true)
    private String productId;

    private String name;
    private String sku;
    private String category;
    private int currentStock;
    private int reservedStock;
    private Instant lastUpdated = Instant.now();

    @Version
    private Long version;

    public Product() {}

    public Product(String productId, String name, String sku, String category, int currentStock) {
        this.productId = productId;
        this.name = name;
        this.sku = sku;
        this.category = category;
        this.currentStock = currentStock;
        this.reservedStock = 0;
    }

    public int getAvailableStock() {
        return currentStock - reservedStock;
    }

    public boolean hasAvailableStock(int quantity) {
        return getAvailableStock() >= quantity;
    }

    public void reserveStock(int quantity) {
        if (!hasAvailableStock(quantity)) {
            throw new IllegalStateException("Insufficient stock for product: " + productId);
        }
        this.reservedStock += quantity;
        this.lastUpdated = Instant.now();
    }

    public void releaseStock(int quantity) {
        this.reservedStock = Math.max(0, this.reservedStock - quantity);
        this.lastUpdated = Instant.now();
    }

    public void confirmReservation(int quantity) {
        this.currentStock -= quantity;
        this.reservedStock -= quantity;
        this.lastUpdated = Instant.now();
    }

    // Getters
    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getName() { return name; }
    public String getSku() { return sku; }
    public String getCategory() { return category; }
    public int getCurrentStock() { return currentStock; }
    public int getReservedStock() { return reservedStock; }
    public Instant getLastUpdated() { return lastUpdated; }
    public Long getVersion() { return version; }
}
```

```java
// StockEventType.java
package com.marketplace.inventory.model;

public enum StockEventType {
    RESERVED,
    RELEASED,
    CONFIRMED,
    REPLENISHED
}
```

```java
// StockEvent.java
package com.marketplace.inventory.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "stock_events")
public class StockEvent {

    @Id
    private String id;
    private String productId;
    private StockEventType eventType;
    private int quantity;
    private String orderId;
    private Instant timestamp = Instant.now();

    public StockEvent() {}

    public StockEvent(String productId, StockEventType eventType, int quantity, String orderId) {
        this.productId = productId;
        this.eventType = eventType;
        this.quantity = quantity;
        this.orderId = orderId;
    }

    // Getters
    public String getId() { return id; }
    public String getProductId() { return productId; }
    public StockEventType getEventType() { return eventType; }
    public int getQuantity() { return quantity; }
    public String getOrderId() { return orderId; }
    public Instant getTimestamp() { return timestamp; }
}
```

**Step 4: Create repositories**

```java
// ProductRepository.java
package com.marketplace.inventory.repository;

import com.marketplace.inventory.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String> {
    Optional<Product> findByProductId(String productId);
}
```

```java
// StockEventRepository.java
package com.marketplace.inventory.repository;

import com.marketplace.inventory.model.StockEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface StockEventRepository extends MongoRepository<StockEvent, String> {
    List<StockEvent> findByProductId(String productId);
    List<StockEvent> findByOrderId(String orderId);
}
```

**Step 5: Write repository test**

```java
package com.marketplace.inventory.repository;

import com.marketplace.inventory.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Testcontainers
class ProductRepositoryTest {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    void shouldSaveAndFindByProductId() {
        Product product = new Product("prod-1", "Wireless Mouse", "SKU-001", "electronics", 100);
        productRepository.save(product);

        Optional<Product> found = productRepository.findByProductId("prod-1");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Wireless Mouse");
        assertThat(found.get().getCurrentStock()).isEqualTo(100);
        assertThat(found.get().getAvailableStock()).isEqualTo(100);
    }

    @Test
    void shouldReserveStock() {
        Product product = new Product("prod-2", "Keyboard", "SKU-002", "electronics", 50);
        product.reserveStock(10);
        productRepository.save(product);

        Product found = productRepository.findByProductId("prod-2").orElseThrow();

        assertThat(found.getCurrentStock()).isEqualTo(50);
        assertThat(found.getReservedStock()).isEqualTo(10);
        assertThat(found.getAvailableStock()).isEqualTo(40);
    }
}
```

**Step 6: Run tests**

Run: `cd marketplace-platform && mvn test -pl inventory-service`
Expected: All tests pass

**Step 7: Commit**

```bash
git add marketplace-platform/inventory-service/
git commit -m "feat(inventory): bootstrap Inventory Service with MongoDB entities, repositories, and tests"
```

---

### Task 8: Inventory Service — gRPC Server Implementation

**Files:**
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/service/InventoryStockService.java`
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/grpc/InventoryGrpcServer.java`
- Create: `marketplace-platform/inventory-service/src/test/java/com/marketplace/inventory/grpc/InventoryGrpcServerTest.java`

**Step 1: Create stock service (business logic)**

```java
package com.marketplace.inventory.service;

import com.marketplace.inventory.model.Product;
import com.marketplace.inventory.model.StockEvent;
import com.marketplace.inventory.model.StockEventType;
import com.marketplace.inventory.repository.ProductRepository;
import com.marketplace.inventory.repository.StockEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class InventoryStockService {

    private static final Logger log = LoggerFactory.getLogger(InventoryStockService.class);

    private final ProductRepository productRepository;
    private final StockEventRepository stockEventRepository;

    public InventoryStockService(ProductRepository productRepository, StockEventRepository stockEventRepository) {
        this.productRepository = productRepository;
        this.stockEventRepository = stockEventRepository;
    }

    public boolean checkStock(String productId, int quantity) {
        return productRepository.findByProductId(productId)
                .map(product -> product.hasAvailableStock(quantity))
                .orElse(false);
    }

    public int getCurrentStock(String productId) {
        return productRepository.findByProductId(productId)
                .map(Product::getAvailableStock)
                .orElse(0);
    }

    public boolean reserveStock(String productId, int quantity, String orderId) {
        try {
            Product product = productRepository.findByProductId(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            product.reserveStock(quantity);
            productRepository.save(product);

            stockEventRepository.save(new StockEvent(productId, StockEventType.RESERVED, quantity, orderId));
            log.info("Reserved {} units of product {} for order {}", quantity, productId, orderId);
            return true;
        } catch (OptimisticLockingFailureException e) {
            log.warn("Optimistic lock conflict reserving stock for product {}, order {}", productId, orderId);
            return false;
        } catch (IllegalStateException e) {
            log.warn("Insufficient stock for product {}: {}", productId, e.getMessage());
            return false;
        }
    }

    public void releaseStock(String productId, int quantity, String orderId) {
        productRepository.findByProductId(productId).ifPresent(product -> {
            product.releaseStock(quantity);
            productRepository.save(product);
            stockEventRepository.save(new StockEvent(productId, StockEventType.RELEASED, quantity, orderId));
            log.info("Released {} units of product {} for order {}", quantity, productId, orderId);
        });
    }
}
```

**Step 2: Create gRPC server implementation**

```java
package com.marketplace.inventory.grpc;

import com.marketplace.inventory.service.InventoryStockService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class InventoryGrpcServer extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final InventoryStockService stockService;

    public InventoryGrpcServer(InventoryStockService stockService) {
        this.stockService = stockService;
    }

    @Override
    public void checkStock(CheckStockRequest request, StreamObserver<CheckStockResponse> responseObserver) {
        boolean available = stockService.checkStock(request.getProductId(), request.getQuantity());
        int currentStock = stockService.getCurrentStock(request.getProductId());

        CheckStockResponse response = CheckStockResponse.newBuilder()
                .setAvailable(available)
                .setCurrentStock(currentStock)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void reserveStock(ReserveStockRequest request, StreamObserver<ReserveStockResponse> responseObserver) {
        boolean success = stockService.reserveStock(
                request.getProductId(), request.getQuantity(), request.getOrderId());

        ReserveStockResponse response = ReserveStockResponse.newBuilder()
                .setSuccess(success)
                .setMessage(success ? "Stock reserved" : "Failed to reserve stock")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
```

**Step 3: Write gRPC server test**

```java
package com.marketplace.inventory.grpc;

import com.marketplace.inventory.model.Product;
import com.marketplace.inventory.repository.ProductRepository;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"grpc.server.port=0"})
@Testcontainers
class InventoryGrpcServerTest {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private net.devh.boot.grpc.server.autoconfigure.GrpcServerProperties grpcProperties;

    private InventoryServiceGrpc.InventoryServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        productRepository.save(new Product("prod-1", "Wireless Mouse", "SKU-001", "electronics", 100));

        int port = grpcProperties.getPort();
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
        stub = InventoryServiceGrpc.newBlockingStub(channel);
    }

    @Test
    void shouldCheckStockAvailable() {
        CheckStockResponse response = stub.checkStock(
                CheckStockRequest.newBuilder().setProductId("prod-1").setQuantity(10).build());

        assertThat(response.getAvailable()).isTrue();
        assertThat(response.getCurrentStock()).isEqualTo(100);
    }

    @Test
    void shouldCheckStockUnavailable() {
        CheckStockResponse response = stub.checkStock(
                CheckStockRequest.newBuilder().setProductId("prod-1").setQuantity(200).build());

        assertThat(response.getAvailable()).isFalse();
    }

    @Test
    void shouldReserveStock() {
        ReserveStockResponse response = stub.reserveStock(
                ReserveStockRequest.newBuilder()
                        .setProductId("prod-1").setQuantity(10).setOrderId("order-1").build());

        assertThat(response.getSuccess()).isTrue();
    }
}
```

Note: The gRPC test setup may need adjustment based on `grpc-spring-boot-starter` version — the approach to getting the dynamic port might differ. Check the library docs during implementation. An alternative is to use `@GrpcClient` annotation with `in-process` channel for testing.

**Step 4: Run tests**

Run: `cd marketplace-platform && mvn test -pl inventory-service`
Expected: All tests pass

**Step 5: Commit**

```bash
git add marketplace-platform/inventory-service/
git commit -m "feat(inventory): add gRPC server for CheckStock and ReserveStock with tests"
```

---

### Task 9: Order Service — gRPC Client to Inventory Service

**Files:**
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/grpc/InventoryGrpcClient.java`
- Modify: `marketplace-platform/order-service/src/main/java/com/marketplace/order/service/OrderService.java`
- Modify: `marketplace-platform/order-service/src/main/resources/application.yml`
- Create: `marketplace-platform/order-service/src/test/java/com/marketplace/order/service/OrderServiceTest.java`

**Step 1: Add gRPC channel config to application.yml**

Add to `order-service/src/main/resources/application.yml`:

```yaml
grpc:
  client:
    inventory-service:
      address: static://localhost:9090
      negotiation-type: plaintext
```

**Step 2: Create gRPC client wrapper**

```java
package com.marketplace.order.grpc;

import com.marketplace.inventory.grpc.CheckStockRequest;
import com.marketplace.inventory.grpc.CheckStockResponse;
import com.marketplace.inventory.grpc.InventoryServiceGrpc;
import com.marketplace.inventory.grpc.ReserveStockRequest;
import com.marketplace.inventory.grpc.ReserveStockResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InventoryGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryGrpcClient.class);

    @GrpcClient("inventory-service")
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    public boolean checkStock(String productId, int quantity) {
        CheckStockResponse response = inventoryStub.checkStock(
                CheckStockRequest.newBuilder()
                        .setProductId(productId)
                        .setQuantity(quantity)
                        .build());
        log.info("Stock check for product {}: available={}, currentStock={}",
                productId, response.getAvailable(), response.getCurrentStock());
        return response.getAvailable();
    }

    public boolean reserveStock(String productId, int quantity, String orderId) {
        ReserveStockResponse response = inventoryStub.reserveStock(
                ReserveStockRequest.newBuilder()
                        .setProductId(productId)
                        .setQuantity(quantity)
                        .setOrderId(orderId)
                        .build());
        log.info("Reserve stock for product {}, order {}: success={}", productId, orderId, response.getSuccess());
        return response.getSuccess();
    }
}
```

**Step 3: Update OrderService to use gRPC client for stock validation**

Update `OrderService.createOrder()`:

```java
// Add to constructor:
private final InventoryGrpcClient inventoryClient;

public OrderService(OrderRepository orderRepository, InventoryGrpcClient inventoryClient) {
    this.orderRepository = orderRepository;
    this.inventoryClient = inventoryClient;
}

@Transactional
public Order createOrder(CreateOrderRequest request) {
    // Check stock for all items via gRPC
    for (var item : request.items()) {
        if (!inventoryClient.checkStock(item.productId(), item.quantity())) {
            throw new InsufficientStockException(item.productId());
        }
    }

    Order order = new Order(request.customerId());
    request.items().forEach(item ->
            order.addItem(new OrderItem(item.productId(), item.quantity(), item.unitPrice()))
    );
    Order saved = orderRepository.save(order);

    // Reserve stock for all items
    for (var item : request.items()) {
        inventoryClient.reserveStock(item.productId(), item.quantity(), saved.getId().toString());
    }

    return saved;
}

public static class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productId) {
        super("Insufficient stock for product: " + productId);
    }
}
```

Add exception handler to `OrderController`:

```java
@ExceptionHandler(OrderService.InsufficientStockException.class)
public ResponseEntity<String> handleInsufficientStock(OrderService.InsufficientStockException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ex.getMessage());
}
```

**Step 4: Write unit test with mocked gRPC client**

```java
package com.marketplace.order.service;

import com.marketplace.order.dto.CreateOrderRequest;
import com.marketplace.order.dto.OrderItemRequest;
import com.marketplace.order.grpc.InventoryGrpcClient;
import com.marketplace.order.model.Order;
import com.marketplace.order.model.OrderStatus;
import com.marketplace.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryGrpcClient inventoryClient;

    @InjectMocks
    private OrderService orderService;

    @Test
    void shouldCreateOrderWhenStockAvailable() {
        when(inventoryClient.checkStock("prod-1", 2)).thenReturn(true);
        when(inventoryClient.reserveStock(eq("prod-1"), eq(2), anyString())).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateOrderRequest("cust-1", List.of(
                new OrderItemRequest("prod-1", 2, new BigDecimal("29.99"))
        ));

        Order result = orderService.createOrder(request);

        assertThat(result.getCustomerId()).isEqualTo("cust-1");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    void shouldRejectOrderWhenStockUnavailable() {
        when(inventoryClient.checkStock("prod-1", 2)).thenReturn(false);

        var request = new CreateOrderRequest("cust-1", List.of(
                new OrderItemRequest("prod-1", 2, new BigDecimal("29.99"))
        ));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderService.InsufficientStockException.class);
    }
}
```

**Step 5: Run tests**

Run: `cd marketplace-platform && mvn test -pl order-service`
Expected: All tests pass

**Step 6: Commit**

```bash
git add marketplace-platform/order-service/
git commit -m "feat(order): integrate gRPC client for stock validation on order creation"
```

---

### Task 10: Kafka Events — Shared Event Classes

**Files:**
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/kafka/event/OrderPlacedEvent.java`
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/kafka/event/OrderCancelledEvent.java`
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/kafka/event/OrderPlacedEvent.java`
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/kafka/event/OrderCancelledEvent.java`
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/kafka/event/StockUpdatedEvent.java`
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/kafka/event/StockUpdatedEvent.java`

Note: In a real microservices setup you'd use a shared schema registry or module. For this learning project, we duplicate the event classes in each service to keep modules independent.

**Step 1: Create event classes in order-service**

```java
// OrderPlacedEvent.java (order-service)
package com.marketplace.order.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderPlacedEvent(
        String orderId,
        String customerId,
        List<OrderItemEvent> items,
        BigDecimal totalAmount,
        Instant timestamp
) {
    public record OrderItemEvent(String productId, int quantity, BigDecimal unitPrice) {}
}
```

```java
// OrderCancelledEvent.java (order-service)
package com.marketplace.order.kafka.event;

import java.time.Instant;
import java.util.List;

public record OrderCancelledEvent(
        String orderId,
        List<CancelledItem> items,
        Instant timestamp
) {
    public record CancelledItem(String productId, int quantity) {}
}
```

```java
// StockUpdatedEvent.java (order-service — consumed)
package com.marketplace.order.kafka.event;

import java.time.Instant;

public record StockUpdatedEvent(
        String orderId,
        String productId,
        boolean success,
        String message,
        Instant timestamp
) {}
```

**Step 2: Create mirror event classes in inventory-service**

```java
// OrderPlacedEvent.java (inventory-service — consumed)
package com.marketplace.inventory.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderPlacedEvent(
        String orderId,
        String customerId,
        List<OrderItemEvent> items,
        BigDecimal totalAmount,
        Instant timestamp
) {
    public record OrderItemEvent(String productId, int quantity, BigDecimal unitPrice) {}
}
```

```java
// OrderCancelledEvent.java (inventory-service — consumed)
package com.marketplace.inventory.kafka.event;

import java.time.Instant;
import java.util.List;

public record OrderCancelledEvent(
        String orderId,
        List<CancelledItem> items,
        Instant timestamp
) {
    public record CancelledItem(String productId, int quantity) {}
}
```

```java
// StockUpdatedEvent.java (inventory-service — produced)
package com.marketplace.inventory.kafka.event;

import java.time.Instant;

public record StockUpdatedEvent(
        String orderId,
        String productId,
        boolean success,
        String message,
        Instant timestamp
) {}
```

**Step 3: Verify project compiles**

Run: `cd marketplace-platform && mvn clean compile`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add marketplace-platform/
git commit -m "feat: add Kafka event classes for order and inventory domains"
```

---

### Task 11: Order Service — Kafka Producer

**Files:**
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/kafka/OrderEventProducer.java`
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/config/KafkaTopicConfig.java`
- Modify: `marketplace-platform/order-service/src/main/java/com/marketplace/order/service/OrderService.java` (integrate producer)
- Create: `marketplace-platform/order-service/src/test/java/com/marketplace/order/kafka/OrderEventProducerTest.java`

**Step 1: Create Kafka topic configuration**

```java
package com.marketplace.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String ORDER_EVENTS_TOPIC = "order-events";

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(ORDER_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
```

**Step 2: Create Kafka producer**

```java
package com.marketplace.order.kafka;

import com.marketplace.order.config.KafkaTopicConfig;
import com.marketplace.order.kafka.event.OrderCancelledEvent;
import com.marketplace.order.kafka.event.OrderPlacedEvent;
import com.marketplace.order.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderPlaced(Order order) {
        var items = order.getItems().stream()
                .map(i -> new OrderPlacedEvent.OrderItemEvent(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .toList();

        var event = new OrderPlacedEvent(
                order.getId().toString(),
                order.getCustomerId(),
                items,
                order.getTotalAmount(),
                Instant.now()
        );

        kafkaTemplate.send(KafkaTopicConfig.ORDER_EVENTS_TOPIC, order.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderPlaced for order {}", order.getId(), ex);
                    } else {
                        log.info("Published OrderPlaced for order {} to partition {}",
                                order.getId(), result.getRecordMetadata().partition());
                    }
                });
    }

    public void publishOrderCancelled(Order order) {
        var items = order.getItems().stream()
                .map(i -> new OrderCancelledEvent.CancelledItem(i.getProductId(), i.getQuantity()))
                .toList();

        var event = new OrderCancelledEvent(order.getId().toString(), items, Instant.now());

        kafkaTemplate.send(KafkaTopicConfig.ORDER_EVENTS_TOPIC, order.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCancelled for order {}", order.getId(), ex);
                    } else {
                        log.info("Published OrderCancelled for order {}", order.getId());
                    }
                });
    }
}
```

**Step 3: Update OrderService to publish events after order creation/cancellation**

Add `OrderEventProducer` to `OrderService` constructor and call it:

```java
private final OrderEventProducer eventProducer;

public OrderService(OrderRepository orderRepository, InventoryGrpcClient inventoryClient, OrderEventProducer eventProducer) {
    this.orderRepository = orderRepository;
    this.inventoryClient = inventoryClient;
    this.eventProducer = eventProducer;
}

// In createOrder(), after save:
Order saved = orderRepository.save(order);
// ... reserve stock ...
eventProducer.publishOrderPlaced(saved);
return saved;

// In cancelOrder(), after save:
order.setStatus(OrderStatus.CANCELLED);
Order saved = orderRepository.save(order);
eventProducer.publishOrderCancelled(saved);
return saved;
```

**Step 4: Write Kafka producer integration test**

```java
package com.marketplace.order.kafka;

import com.marketplace.order.kafka.event.OrderPlacedEvent;
import com.marketplace.order.model.Order;
import com.marketplace.order.model.OrderItem;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OrderEventProducerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private OrderEventProducer producer;

    @Test
    void shouldPublishOrderPlacedEvent() {
        Order order = new Order("customer-1");
        order.addItem(new OrderItem("prod-1", 2, new BigDecimal("29.99")));

        producer.publishOrderPlaced(order);

        // Verify message was sent by consuming from the topic
        try (KafkaConsumer<String, OrderPlacedEvent> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-group",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        ), new StringDeserializer(), new JsonDeserializer<>(OrderPlacedEvent.class, false))) {
            consumer.subscribe(List.of("order-events"));
            ConsumerRecords<String, OrderPlacedEvent> records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records.count()).isGreaterThanOrEqualTo(1);
        }
    }
}
```

**Step 5: Run tests**

Run: `cd marketplace-platform && mvn test -pl order-service`
Expected: All tests pass

**Step 6: Commit**

```bash
git add marketplace-platform/order-service/
git commit -m "feat(order): add Kafka producer for OrderPlaced and OrderCancelled events"
```

---

### Task 12: Inventory Service — Kafka Consumer + Producer

**Files:**
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/kafka/OrderEventConsumer.java`
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/kafka/InventoryEventProducer.java`
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/config/KafkaTopicConfig.java`
- Create: `marketplace-platform/inventory-service/src/test/java/com/marketplace/inventory/kafka/OrderEventConsumerTest.java`

**Step 1: Create Kafka topic config**

```java
package com.marketplace.inventory.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String INVENTORY_EVENTS_TOPIC = "inventory-events";

    @Bean
    public NewTopic inventoryEventsTopic() {
        return TopicBuilder.name(INVENTORY_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
```

**Step 2: Create inventory event producer**

```java
package com.marketplace.inventory.kafka;

import com.marketplace.inventory.config.KafkaTopicConfig;
import com.marketplace.inventory.kafka.event.StockUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class InventoryEventProducer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventoryEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishStockUpdated(String orderId, String productId, boolean success, String message) {
        var event = new StockUpdatedEvent(orderId, productId, success, message, Instant.now());
        kafkaTemplate.send(KafkaTopicConfig.INVENTORY_EVENTS_TOPIC, orderId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish StockUpdated for order {}", orderId, ex);
                    } else {
                        log.info("Published StockUpdated for order {}, product {}, success={}",
                                orderId, productId, success);
                    }
                });
    }
}
```

**Step 3: Create order event consumer**

```java
package com.marketplace.inventory.kafka;

import com.marketplace.inventory.kafka.event.OrderCancelledEvent;
import com.marketplace.inventory.kafka.event.OrderPlacedEvent;
import com.marketplace.inventory.service.InventoryStockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final InventoryStockService stockService;
    private final InventoryEventProducer eventProducer;

    public OrderEventConsumer(InventoryStockService stockService, InventoryEventProducer eventProducer) {
        this.stockService = stockService;
        this.eventProducer = eventProducer;
    }

    @KafkaListener(topics = "order-events", groupId = "inventory-service-group",
            properties = {"spring.json.value.default.type=com.marketplace.inventory.kafka.event.OrderPlacedEvent"})
    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("Received OrderPlaced event for order {}", event.orderId());

        for (var item : event.items()) {
            boolean success = stockService.reserveStock(item.productId(), item.quantity(), event.orderId());
            eventProducer.publishStockUpdated(
                    event.orderId(),
                    item.productId(),
                    success,
                    success ? "Stock reserved" : "Failed to reserve stock"
            );
        }
    }

    // Note: For handling multiple event types on the same topic, you'll need
    // a custom deserializer or type header mapping. For simplicity, OrderCancelled
    // could be on a separate topic, or use Spring Kafka's type mapping:
    // spring.kafka.consumer.properties.spring.json.type.mapping=OrderPlaced:...,OrderCancelled:...
}
```

**Step 4: Write consumer integration test**

```java
package com.marketplace.inventory.kafka;

import com.marketplace.inventory.kafka.event.OrderPlacedEvent;
import com.marketplace.inventory.model.Product;
import com.marketplace.inventory.repository.ProductRepository;
import com.marketplace.inventory.repository.StockEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class OrderEventConsumerTest {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockEventRepository stockEventRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        stockEventRepository.deleteAll();
        productRepository.save(new Product("prod-1", "Mouse", "SKU-001", "electronics", 100));
    }

    @Test
    void shouldReserveStockOnOrderPlacedEvent() {
        var event = new OrderPlacedEvent(
                "order-1", "customer-1",
                List.of(new OrderPlacedEvent.OrderItemEvent("prod-1", 5, new BigDecimal("29.99"))),
                new BigDecimal("149.95"),
                Instant.now()
        );

        kafkaTemplate.send("order-events", "order-1", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Product product = productRepository.findByProductId("prod-1").orElseThrow();
            assertThat(product.getReservedStock()).isEqualTo(5);
            assertThat(product.getAvailableStock()).isEqualTo(95);
        });
    }
}
```

Note: Add `awaitility` dependency to `inventory-service/pom.xml` for test:

```xml
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
</dependency>
```

**Step 5: Run tests**

Run: `cd marketplace-platform && mvn test -pl inventory-service`
Expected: All tests pass

**Step 6: Commit**

```bash
git add marketplace-platform/inventory-service/
git commit -m "feat(inventory): add Kafka consumer for OrderPlaced and producer for StockUpdated events"
```

---

### Task 13: Order Service — Kafka Consumer for Inventory Events

**Files:**
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/kafka/InventoryEventConsumer.java`
- Modify: `marketplace-platform/order-service/src/main/java/com/marketplace/order/service/OrderService.java` (add updateOrderStatus)

**Step 1: Add status update method to OrderService**

```java
@Transactional
public void updateOrderStatus(UUID orderId, OrderStatus status) {
    orderRepository.findById(orderId).ifPresent(order -> {
        order.setStatus(status);
        orderRepository.save(order);
        log.info("Updated order {} status to {}", orderId, status);
    });
}
```

**Step 2: Create inventory event consumer**

```java
package com.marketplace.order.kafka;

import com.marketplace.order.kafka.event.StockUpdatedEvent;
import com.marketplace.order.model.OrderStatus;
import com.marketplace.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final OrderService orderService;

    public InventoryEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "inventory-events", groupId = "order-service-group",
            properties = {"spring.json.value.default.type=com.marketplace.order.kafka.event.StockUpdatedEvent"})
    public void handleStockUpdated(StockUpdatedEvent event) {
        log.info("Received StockUpdated for order {}: success={}", event.orderId(), event.success());

        UUID orderId = UUID.fromString(event.orderId());
        if (event.success()) {
            orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED);
        } else {
            orderService.updateOrderStatus(orderId, OrderStatus.REJECTED);
        }
    }
}
```

**Step 3: Verify project compiles**

Run: `cd marketplace-platform && mvn clean compile`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add marketplace-platform/order-service/
git commit -m "feat(order): add Kafka consumer for StockUpdated events to update order status"
```

---

### Task 14: Observability — Actuator + Micrometer + Structured Logging

**Files:**
- Create: `marketplace-platform/order-service/src/main/resources/logback-spring.xml`
- Create: `marketplace-platform/inventory-service/src/main/resources/logback-spring.xml`
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/config/MetricsConfig.java`

**Step 1: Create structured logging config (JSON format) for both services**

```xml
<!-- Same file for both services -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <springProfile name="!dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>orderId</includeMdcKeyName>
                <includeMdcKeyName>productId</includeMdcKeyName>
            </encoder>
        </appender>
    </springProfile>

    <springProfile name="dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
    </springProfile>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

Note: Add `logstash-logback-encoder` dependency to both POMs for JSON logging in production:

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

Or keep it simple: skip JSON logging and use the default Spring Boot console format. The structured logging config above is optional — discuss it in interview as "production-ready logging."

**Step 2: Add custom metrics to OrderService**

```java
package com.marketplace.order.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter ordersCreatedCounter(MeterRegistry registry) {
        return Counter.builder("orders.created.total")
                .description("Total orders created")
                .register(registry);
    }

    @Bean
    public Counter ordersRejectedCounter(MeterRegistry registry) {
        return Counter.builder("orders.rejected.total")
                .description("Total orders rejected due to insufficient stock")
                .register(registry);
    }

    @Bean
    public Timer orderCreationTimer(MeterRegistry registry) {
        return Timer.builder("orders.creation.duration")
                .description("Time to create an order")
                .register(registry);
    }
}
```

Then inject and use these in `OrderService.createOrder()`:

```java
ordersCreatedCounter.increment();
// or on failure:
ordersRejectedCounter.increment();
```

**Step 3: Verify actuator endpoints work**

After starting the app: `curl http://localhost:8080/actuator/health` should return `{"status":"UP"}`
`curl http://localhost:8080/actuator/metrics/orders.created.total` should return the metric.

**Step 4: Commit**

```bash
git add marketplace-platform/
git commit -m "feat: add observability with Actuator metrics, Micrometer counters, and structured logging"
```

---

### Task 15: Data Seeding + End-to-End Manual Test

**Files:**
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/config/DataSeeder.java`

**Step 1: Create data seeder for inventory products**

```java
package com.marketplace.inventory.config;

import com.marketplace.inventory.model.Product;
import com.marketplace.inventory.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ProductRepository productRepository;

    public DataSeeder(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        if (productRepository.count() == 0) {
            productRepository.save(new Product("prod-1", "Wireless Mouse", "SKU-001", "electronics", 500));
            productRepository.save(new Product("prod-2", "Mechanical Keyboard", "SKU-002", "electronics", 300));
            productRepository.save(new Product("prod-3", "USB-C Hub", "SKU-003", "electronics", 1000));
            productRepository.save(new Product("prod-4", "Monitor Stand", "SKU-004", "furniture", 200));
            productRepository.save(new Product("prod-5", "Desk Lamp", "SKU-005", "furniture", 150));
            log.info("Seeded 5 products into inventory");
        }
    }
}
```

**Step 2: Add dev profile to both services' application.yml**

Add `spring.profiles.active: dev` or run with `--spring.profiles.active=dev`

**Step 3: End-to-end manual test**

1. Start infrastructure: `cd marketplace-platform && docker compose up -d`
2. Start Inventory Service: `cd inventory-service && mvn spring-boot:run -Dspring-boot.run.profiles=dev`
3. Start Order Service: `cd order-service && mvn spring-boot:run -Dspring-boot.run.profiles=dev`
4. Create an order:
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","items":[{"productId":"prod-1","quantity":2,"unitPrice":29.99}]}'
```
5. Verify order status changes from PENDING to CONFIRMED
6. Check inventory was decremented

**Step 4: Commit**

```bash
git add marketplace-platform/
git commit -m "feat(inventory): add dev profile data seeder for manual testing"
```

---

### Task 16: CLAUDE.md

**Files:**
- Create: `marketplace-platform/CLAUDE.md`

**Step 1: Create CLAUDE.md with build/run/test commands and architecture overview**

Content should include:
- How to build: `mvn clean compile` from root
- How to run: docker compose + both services
- How to test: `mvn test`, `mvn test -pl order-service`, `mvn test -pl inventory-service`
- Architecture: 2 services, Kafka topics, gRPC contract, database per service
- Key config: virtual threads enabled, Kafka KRaft, Testcontainers for integration tests

**Step 2: Commit**

```bash
git add marketplace-platform/CLAUDE.md
git commit -m "docs: add CLAUDE.md with build, run, and architecture reference"
```
