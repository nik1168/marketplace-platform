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
