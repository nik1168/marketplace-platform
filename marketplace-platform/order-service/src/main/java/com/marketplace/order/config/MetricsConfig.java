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
