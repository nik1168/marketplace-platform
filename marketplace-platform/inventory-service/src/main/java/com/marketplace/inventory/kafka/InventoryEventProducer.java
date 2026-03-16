package com.marketplace.inventory.kafka;

import com.marketplace.inventory.config.KafkaTopicConfig;
import com.marketplace.inventory.kafka.event.StockUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class InventoryEventProducer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventoryEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishStockUpdated(String orderId, String productId, boolean success, String message) {
        var event = new StockUpdatedEvent(orderId, productId, success, message, Instant.now());
        // orderId as key ensures ordering per order across partitions
        kafkaTemplate.send(KafkaTopicConfig.INVENTORY_EVENTS_TOPIC, orderId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish StockUpdated for order {}", orderId, ex);
                    } else {
                        log.info("Published StockUpdated for order {}, product {}, success={}",
                                orderId, productId, success);
                    }
                });
    }
}
