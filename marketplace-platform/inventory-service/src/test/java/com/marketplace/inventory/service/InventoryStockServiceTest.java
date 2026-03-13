package com.marketplace.inventory.service;

import com.marketplace.inventory.model.Product;
import com.marketplace.inventory.repository.ProductRepository;
import com.marketplace.inventory.repository.StockEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.kafka.KafkaContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"grpc.server.port=0"})
@Testcontainers
class InventoryStockServiceTest {

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
    private InventoryStockService stockService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockEventRepository stockEventRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        stockEventRepository.deleteAll();
        productRepository.save(new Product("prod-1", "Wireless Mouse", "SKU-001", "electronics", 100));
    }

    @Test
    void shouldCheckStockAvailable() {
        assertThat(stockService.checkStock("prod-1", 10)).isTrue();
        assertThat(stockService.checkStock("prod-1", 200)).isFalse();
    }

    @Test
    void shouldCheckStockForNonExistentProduct() {
        assertThat(stockService.checkStock("nonexistent", 1)).isFalse();
    }

    @Test
    void shouldGetCurrentStock() {
        assertThat(stockService.getCurrentStock("prod-1")).isEqualTo(100);
        assertThat(stockService.getCurrentStock("nonexistent")).isEqualTo(0);
    }

    @Test
    void shouldReserveStock() {
        boolean result = stockService.reserveStock("prod-1", 10, "order-1");

        assertThat(result).isTrue();
        Product product = productRepository.findByProductId("prod-1").orElseThrow();
        assertThat(product.getReservedStock()).isEqualTo(10);
        assertThat(product.getAvailableStock()).isEqualTo(90);
    }

    @Test
    void shouldFailToReserveInsufficientStock() {
        boolean result = stockService.reserveStock("prod-1", 200, "order-1");

        assertThat(result).isFalse();
    }

    @Test
    void shouldReleaseStock() {
        stockService.reserveStock("prod-1", 10, "order-1");
        stockService.releaseStock("prod-1", 10, "order-1");

        Product product = productRepository.findByProductId("prod-1").orElseThrow();
        assertThat(product.getReservedStock()).isEqualTo(0);
        assertThat(product.getAvailableStock()).isEqualTo(100);
    }
}
