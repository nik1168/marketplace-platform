package com.marketplace.inventory.kafka;

import com.marketplace.inventory.kafka.event.OrderPlacedEvent;
import com.marketplace.inventory.service.InventoryStockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final InventoryStockService stockService;
    private final InventoryEventProducer eventProducer;

    public OrderEventConsumer(InventoryStockService stockService, InventoryEventProducer eventProducer) {
        this.stockService = stockService;
        this.eventProducer = eventProducer;
    }

    // spring.json.use.type.headers=false: ignore producer-side type headers to avoid ClassNotFoundException
    // when the producer and consumer have different package structures for the same event class
    @KafkaListener(topics = "order-events", groupId = "inventory-service-group",
            properties = {
                "spring.json.value.default.type=com.marketplace.inventory.kafka.event.OrderPlacedEvent",
                "spring.json.use.type.headers=false"
            })
    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("Received OrderPlaced event for order {}", event.orderId());

        for (var item : event.items()) {
            boolean success = stockService.reserveStock(item.productId(), item.quantity(), event.orderId());
            eventProducer.publishStockUpdated(
                    event.orderId(),
                    item.productId(),
                    success,
                    success ? "Stock reserved" : "Failed to reserve stock"
            );
        }
    }
}
