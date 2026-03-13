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
