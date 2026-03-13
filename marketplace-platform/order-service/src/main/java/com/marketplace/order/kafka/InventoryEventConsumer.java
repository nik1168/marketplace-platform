package com.marketplace.order.kafka;

import com.marketplace.order.kafka.event.StockUpdatedEvent;
import com.marketplace.order.model.OrderStatus;
import com.marketplace.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer that listens for events from the Inventory Service on the "inventory-events" topic.
 *
 * When the Inventory Service finishes processing a stock reservation (triggered by an OrderPlaced event),
 * it publishes a StockUpdatedEvent. This consumer picks up that event and updates the order status
 * to either CONFIRMED (stock was reserved successfully) or REJECTED (stock reservation failed).
 *
 * This is the second half of the asynchronous order flow:
 *   Order Service -> Kafka (OrderPlaced) -> Inventory Service -> Kafka (StockUpdated) -> THIS CONSUMER
 */
// @Component makes this a Spring-managed bean so the @KafkaListener is activated
@Component
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final OrderService orderService;

    public InventoryEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Called automatically by Spring Kafka whenever a new message arrives on the "inventory-events" topic.
     * Updates the order status based on whether the inventory reservation was successful.
     */
    // @KafkaListener tells Spring Kafka to subscribe to the specified topic and call this method
    // for each message received.
    // - topics: which Kafka topic to listen on
    // - groupId: consumer group ID — ensures only one instance of this service processes each message
    // - properties: configures the JSON deserializer to convert the message into a StockUpdatedEvent object
    @KafkaListener(topics = "inventory-events", groupId = "order-service-group",
            properties = {"spring.json.value.default.type=com.marketplace.order.kafka.event.StockUpdatedEvent"})
    public void handleStockUpdated(StockUpdatedEvent event) {
        log.info("Received StockUpdated for order {}: success={}", event.orderId(), event.success());

        // Convert the order ID string back to a UUID for database lookup
        UUID orderId = UUID.fromString(event.orderId());

        // Update the order status based on whether the inventory reservation succeeded
        if (event.success()) {
            // Stock was reserved — confirm the order
            orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED);
        } else {
            // Stock reservation failed — reject the order
            orderService.updateOrderStatus(orderId, OrderStatus.REJECTED);
        }
    }
}
