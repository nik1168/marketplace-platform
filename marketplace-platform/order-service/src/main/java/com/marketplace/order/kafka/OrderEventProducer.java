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

@Component
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderPlaced(Order order) {
        var items = order.getItems().stream()
                .map(i -> new OrderPlacedEvent.OrderItemEvent(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .toList();

        var event = new OrderPlacedEvent(
                order.getId().toString(),
                order.getCustomerId(),
                items,
                order.getTotalAmount(),
                Instant.now()
        );

        kafkaTemplate.send(KafkaTopicConfig.ORDER_EVENTS_TOPIC, order.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderPlaced for order {}", order.getId(), ex);
                    } else {
                        log.info("Published OrderPlaced for order {} to partition {}",
                                order.getId(), result.getRecordMetadata().partition());
                    }
                });
    }

    public void publishOrderCancelled(Order order) {
        var items = order.getItems().stream()
                .map(i -> new OrderCancelledEvent.CancelledItem(i.getProductId(), i.getQuantity()))
                .toList();

        var event = new OrderCancelledEvent(order.getId().toString(), items, Instant.now());

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
