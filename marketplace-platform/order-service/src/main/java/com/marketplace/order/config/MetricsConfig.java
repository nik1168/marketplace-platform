package com.marketplace.order.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for application metrics using Micrometer.
 *
 * Micrometer is a metrics library that works with monitoring systems like Prometheus and Grafana.
 * These metrics help us track how the Order Service is performing in production:
 * - How many orders are being created?
 * - How many are being rejected due to stock issues?
 * - How long does order creation take?
 *
 * Metrics are exposed via the /actuator/prometheus endpoint for scraping by monitoring tools.
 */
@Configuration
public class MetricsConfig {

    /**
     * Counter that tracks the total number of successfully created orders.
     * A Counter only goes up — it's ideal for tracking cumulative totals.
     *
     * MeterRegistry is provided by Spring Boot's auto-configuration and connects
     * to whatever monitoring backend is configured (e.g., Prometheus).
     */
    @Bean
    public Counter ordersCreatedCounter(MeterRegistry registry) {
        return Counter.builder("orders.created.total")
                .description("Total orders created")
                .register(registry);
    }

    /**
     * Counter that tracks the total number of orders rejected due to insufficient stock.
     * Useful for alerting — a sudden spike in rejections might indicate an inventory problem.
     */
    @Bean
    public Counter ordersRejectedCounter(MeterRegistry registry) {
        return Counter.builder("orders.rejected.total")
                .description("Total orders rejected due to insufficient stock")
                .register(registry);
    }

    /**
     * Timer that measures how long the order creation process takes.
     * Unlike a Counter, a Timer records durations and provides statistics
     * like average, max, and percentiles — useful for spotting performance issues.
     */
    @Bean
    public Timer orderCreationTimer(MeterRegistry registry) {
        return Timer.builder("orders.creation.duration")
                .description("Time to create an order")
                .register(registry);
    }
}
