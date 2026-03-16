package com.marketplace.order.kafka.event;

import java.time.Instant;

public record StockUpdatedEvent(
        String orderId,
        String productId,
        boolean success,
        String message,
        Instant timestamp
) {}
