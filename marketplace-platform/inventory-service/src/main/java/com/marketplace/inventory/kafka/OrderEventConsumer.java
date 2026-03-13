package com.marketplace.inventory.kafka;

import com.marketplace.inventory.kafka.event.OrderPlacedEvent;
import com.marketplace.inventory.service.InventoryStockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that listens for order-related events from the Order Service.
 *
 * When a new order is placed, this consumer receives the event and attempts to reserve
 * stock for each item in the order. After processing, it publishes the result back to
 * Kafka so the Order Service knows whether the reservation succeeded or failed.
 *
 * This is an example of event-driven architecture: services communicate asynchronously
 * through messages instead of direct HTTP/gRPC calls.
 */
// @Component tells Spring to create an instance of this class and manage it as a bean.
// This is needed so Spring can detect the @KafkaListener method and start consuming messages.
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final InventoryStockService stockService;     // For reserving stock
    private final InventoryEventProducer eventProducer;   // For publishing results back to Kafka

    /**
     * Constructor injection: Spring provides both dependencies automatically.
     */
    public OrderEventConsumer(InventoryStockService stockService, InventoryEventProducer eventProducer) {
        this.stockService = stockService;
        this.eventProducer = eventProducer;
    }

    /**
     * Processes an OrderPlacedEvent received from the "order-events" Kafka topic.
     *
     * For each item in the order, it tries to reserve stock and then publishes
     * a StockUpdatedEvent back to Kafka with the result (success or failure).
     * The Order Service listens for these results to confirm or reject the order.
     *
     * @param event the order event containing the order ID and list of items to reserve
     */
    // @KafkaListener makes this method automatically receive messages from the specified Kafka topic.
    // - topics: which Kafka topic to read from
    // - groupId: consumer group name — ensures only one instance in the group processes each message
    // - properties: tells the JSON deserializer what Java class to convert the message into
    @KafkaListener(topics = "order-events", groupId = "inventory-service-group",
            properties = {"spring.json.value.default.type=com.marketplace.inventory.kafka.event.OrderPlacedEvent"})
    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("Received OrderPlaced event for order {}", event.orderId());

        // Process each item in the order individually
        for (var item : event.items()) {
            // Try to reserve stock for this item
            boolean success = stockService.reserveStock(item.productId(), item.quantity(), event.orderId());
            // Publish the result so the Order Service knows if it worked
            eventProducer.publishStockUpdated(
                    event.orderId(),
                    item.productId(),
                    success,
                    success ? "Stock reserved" : "Failed to reserve stock"
            );
        }
    }
}
