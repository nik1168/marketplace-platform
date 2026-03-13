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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"grpc.server.port=0"})
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
