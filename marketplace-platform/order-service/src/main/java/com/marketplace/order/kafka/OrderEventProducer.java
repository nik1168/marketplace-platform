package com.marketplace.order.kafka;

import com.marketplace.order.config.KafkaTopicConfig;
import com.marketplace.order.kafka.event.OrderCancelledEvent;
import com.marketplace.order.kafka.event.OrderPlacedEvent;
import com.marketplace.order.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Kafka producer that publishes order-related events to the "order-events" topic.
 *
 * Kafka is a message broker used for asynchronous communication between microservices.
 * When something important happens (like an order being placed or cancelled), this producer
 * sends an event so that other services (like the Inventory Service) can react to it
 * without the Order Service needing to call them directly.
 *
 * This enables loose coupling — services don't need to know about each other's internals.
 */
@Component
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);

    // KafkaTemplate is Spring's helper for sending messages to Kafka topics
    // <String, Object> means the key is a String (we use the order ID) and the value is any event object
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes an OrderPlacedEvent to Kafka when a new order is successfully created.
     * The Inventory Service listens for this event and processes the stock reservation.
     */
    public void publishOrderPlaced(Order order) {
        // Convert the order's items into event-specific DTOs
        var items = order.getItems().stream()
                .map(i -> new OrderPlacedEvent.OrderItemEvent(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .toList();

        // Build the event with all relevant order information
        var event = new OrderPlacedEvent(
                order.getId().toString(),
                order.getCustomerId(),
                items,
                order.getTotalAmount(),
                Instant.now()
        );

        // Send the event to Kafka, using the order ID as the message key.
        // Using the order ID as the key ensures all events for the same order
        // go to the same Kafka partition, maintaining ordering per order.
        kafkaTemplate.send(KafkaTopicConfig.ORDER_EVENTS_TOPIC, order.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    // This callback runs after the send completes (either success or failure)
                    if (ex != null) {
                        log.error("Failed to publish OrderPlaced for order {}", order.getId(), ex);
                    } else {
                        log.info("Published OrderPlaced for order {} to partition {}",
                                order.getId(), result.getRecordMetadata().partition());
                    }
                });
    }

    /**
     * Publishes an OrderCancelledEvent to Kafka when an order is cancelled.
     * The Inventory Service listens for this event and releases the reserved stock.
     */
    public void publishOrderCancelled(Order order) {
        // Convert items to cancelled-item DTOs (only need product ID and quantity for stock release)
        var items = order.getItems().stream()
                .map(i -> new OrderCancelledEvent.CancelledItem(i.getProductId(), i.getQuantity()))
                .toList();

        var event = new OrderCancelledEvent(order.getId().toString(), items, Instant.now());

        // Send to the same topic — the Inventory Service distinguishes event types during deserialization
        kafkaTemplate.send(KafkaTopicConfig.ORDER_EVENTS_TOPIC, order.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCancelled for order {}", order.getId(), ex);
                    } else {
                        log.info("Published OrderCancelled for order {}", order.getId());
                    }
                });
    }
}
