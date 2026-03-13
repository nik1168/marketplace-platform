package com.marketplace.inventory.config;

import com.marketplace.inventory.model.Product;
import com.marketplace.inventory.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds the MongoDB database with sample products when the application starts.
 * This is only active in the "dev" profile, so production won't get test data.
 *
 * Implements CommandLineRunner, which means Spring will call the run() method
 * automatically after the application context is fully initialized.
 */
// @Component registers this class as a Spring-managed bean so it gets picked up and executed.
@Component
// @Profile("dev") means this bean is ONLY created when the "dev" profile is active.
// You activate it with: --spring.profiles.active=dev or SPRING_PROFILES_ACTIVE=dev
// This prevents test data from being inserted in production environments.
@Profile("dev")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ProductRepository productRepository;

    /**
     * Constructor injection: Spring provides the ProductRepository automatically.
     */
    public DataSeeder(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Runs at application startup (only in the "dev" profile).
     * Inserts 5 sample products into MongoDB if the collection is empty.
     * The check for count() == 0 prevents duplicate data if the app is restarted.
     */
    @Override
    public void run(String... args) {
        // Only seed data if the products collection is empty (avoids duplicates on restart)
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
