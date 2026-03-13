package com.marketplace.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Order Service microservice.
 * This is the main class that boots up the entire Spring application.
 *
 * The Order Service is responsible for creating, retrieving, and cancelling orders.
 * It communicates with the Inventory Service via gRPC (to check/reserve stock)
 * and Kafka (to publish order events and consume inventory events).
 */
// @SpringBootApplication is a convenience annotation that combines three things:
// 1. @Configuration — marks this class as a source of bean definitions
// 2. @EnableAutoConfiguration — tells Spring Boot to auto-configure beans based on dependencies
// 3. @ComponentScan — tells Spring to scan this package (and sub-packages) for components
@SpringBootApplication
public class OrderServiceApplication {

    /**
     * The main method — Java's standard entry point.
     * SpringApplication.run() bootstraps the application: starts the embedded web server,
     * loads all configuration, creates beans, and begins listening for HTTP requests.
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
