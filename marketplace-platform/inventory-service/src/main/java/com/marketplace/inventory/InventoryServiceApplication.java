package com.marketplace.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Inventory Service microservice.
 * This service manages product stock levels, handles stock reservations via gRPC,
 * and reacts to order events from Kafka to reserve or release inventory.
 */
// @SpringBootApplication combines three annotations:
// - @Configuration: marks this class as a source of bean definitions
// - @EnableAutoConfiguration: tells Spring Boot to automatically configure beans based on dependencies
// - @ComponentScan: tells Spring to scan this package and sub-packages for components (@Service, @Component, etc.)
@SpringBootApplication
public class InventoryServiceApplication {

    /**
     * The main method that starts the Spring Boot application.
     * Spring Boot will set up the embedded web server, connect to MongoDB and Kafka,
     * and register the gRPC server automatically.
     */
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
