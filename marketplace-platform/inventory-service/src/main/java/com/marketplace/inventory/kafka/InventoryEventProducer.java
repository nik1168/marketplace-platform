package com.marketplace.inventory.kafka;

import com.marketplace.inventory.config.KafkaTopicConfig;
import com.marketplace.inventory.kafka.event.StockUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Kafka producer that publishes inventory-related events to the "inventory-events" topic.
 *
 * After the Inventory Service processes a stock operation (e.g., reserving stock for an order),
 * this producer sends the result to Kafka so other services (like the Order Service) can react.
 * This enables asynchronous communication between microservices.
 */
// @Component tells Spring to manage this class as a bean, so it can be injected into other classes.
@Component
public class InventoryEventProducer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventProducer.class);

    // KafkaTemplate is Spring's helper class for sending messages to Kafka topics.
    // <String, Object> means the message key is a String and the value can be any object
    // (it will be serialized to JSON automatically by the configured serializer).
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Constructor injection: Spring provides the KafkaTemplate, which is auto-configured
     * based on the application's Kafka settings (broker address, serializers, etc.).
     */
    public InventoryEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a StockUpdatedEvent to the "inventory-events" Kafka topic.
     * The Order Service listens on this topic to find out whether stock reservation
     * succeeded or failed, so it can confirm or reject the order.
     *
     * @param orderId   the order this update relates to (also used as the Kafka message key
     *                  so all events for the same order go to the same partition, preserving order)
     * @param productId the product whose stock was updated
     * @param success   whether the stock operation succeeded
     * @param message   a human-readable description of the result
     */
    public void publishStockUpdated(String orderId, String productId, boolean success, String message) {
        var event = new StockUpdatedEvent(orderId, productId, success, message, Instant.now());
        // Send the event to Kafka; orderId is the key (ensures ordering per order)
        kafkaTemplate.send(KafkaTopicConfig.INVENTORY_EVENTS_TOPIC, orderId, event)
                // whenComplete is called asynchronously after the send finishes (success or failure)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // Log the error but don't throw — Kafka sends are fire-and-forget here
                        log.error("Failed to publish StockUpdated for order {}", orderId, ex);
                    } else {
                        log.info("Published StockUpdated for order {}, product {}, success={}",
                                orderId, productId, success);
                    }
                });
    }
}
