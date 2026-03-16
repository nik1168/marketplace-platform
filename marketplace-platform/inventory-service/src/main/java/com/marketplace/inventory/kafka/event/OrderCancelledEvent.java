package com.marketplace.inventory.kafka.event;

import java.time.Instant;
import java.util.List;

public record OrderCancelledEvent(
        String orderId,
        List<CancelledItem> items,
        Instant timestamp
) {
    public record CancelledItem(String productId, int quantity) {}
}
