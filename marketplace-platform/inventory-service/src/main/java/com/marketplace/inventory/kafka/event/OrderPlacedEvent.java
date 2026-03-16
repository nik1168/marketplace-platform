package com.marketplace.inventory.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderPlacedEvent(
        String orderId,
        String customerId,
        List<OrderItemEvent> items,
        BigDecimal totalAmount,
        Instant timestamp
) {
    public record OrderItemEvent(String productId, int quantity, BigDecimal unitPrice) {}
}
