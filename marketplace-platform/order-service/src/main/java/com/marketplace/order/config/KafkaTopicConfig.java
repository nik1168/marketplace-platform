package com.marketplace.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration for the Order Service.
 *
 * This class ensures the required Kafka topics exist when the application starts.
 * If a topic already exists, Kafka will not recreate it. If it doesn't exist,
 * Spring Kafka will create it automatically with the specified settings.
 */
// @Configuration tells Spring that this class contains bean definitions.
// Beans defined here are created once at startup and managed by the Spring container.
@Configuration
public class KafkaTopicConfig {

    /** Topic name constant — used by producers to send order events (placed, cancelled) */
    public static final String ORDER_EVENTS_TOPIC = "order-events";

    /**
     * Defines the "order-events" Kafka topic with 3 partitions and 1 replica.
     *
     * - Partitions (3): allow parallel processing — different orders can be processed
     *   simultaneously by different consumer instances for higher throughput.
     * - Replicas (1): how many copies of the data exist across Kafka brokers.
     *   In production, you'd typically use 3 replicas for fault tolerance.
     */
    // @Bean tells Spring to call this method at startup and register the returned object
    // as a bean. Spring Kafka uses this NewTopic bean to auto-create the topic if needed.
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(ORDER_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
