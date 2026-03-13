package com.marketplace.inventory.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration for the Inventory Service.
 * This class ensures the required Kafka topics exist when the application starts.
 * If a topic already exists, Kafka will skip creation (no error).
 */
// @Configuration tells Spring that this class contains bean definitions.
// Beans defined here will be created and managed by the Spring container.
@Configuration
public class KafkaTopicConfig {

    // Constant for the topic name to avoid typos — used by the producer when sending messages.
    public static final String INVENTORY_EVENTS_TOPIC = "inventory-events";

    /**
     * Creates the "inventory-events" Kafka topic with 3 partitions and 1 replica.
     *
     * - Partitions allow Kafka to process messages in parallel (3 consumers can read simultaneously).
     * - Replicas determine how many copies of the data exist for fault tolerance.
     *   Using 1 replica is fine for development but should be higher in production.
     *
     * @Bean tells Spring to call this method at startup and register the returned object
     * as a managed bean. Spring Kafka's admin client uses this bean to create the topic.
     */
    @Bean
    public NewTopic inventoryEventsTopic() {
        return TopicBuilder.name(INVENTORY_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
