package com.marketplace.order.kafka;

import com.marketplace.order.kafka.event.StockUpdatedEvent;
import com.marketplace.order.model.OrderStatus;
import com.marketplace.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final OrderService orderService;

    public InventoryEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    // spring.json.use.type.headers=false: ignore producer-side type headers to avoid ClassNotFoundException
    // when the producer and consumer have different package structures for the same event class
    @KafkaListener(topics = "inventory-events", groupId = "order-service-group",
            properties = {
                "spring.json.value.default.type=com.marketplace.order.kafka.event.StockUpdatedEvent",
                "spring.json.use.type.headers=false"
            })
    public void handleStockUpdated(StockUpdatedEvent event) {
        log.info("Received StockUpdated for order {}: success={}", event.orderId(), event.success());

        UUID orderId = UUID.fromString(event.orderId());

        if (event.success()) {
            orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED);
        } else {
            orderService.updateOrderStatus(orderId, OrderStatus.REJECTED);
        }
    }
}
