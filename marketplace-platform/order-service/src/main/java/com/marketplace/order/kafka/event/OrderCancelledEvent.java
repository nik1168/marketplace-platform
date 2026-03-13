package com.marketplace.order.kafka.event;

import java.time.Instant;
import java.util.List;

/**
 * Kafka event published when an order is cancelled.
 * The Inventory Service consumes this event to release the stock
 * that was previously reserved for this order.
 */
public record OrderCancelledEvent(
        String orderId,            // The ID of the cancelled order
        List<CancelledItem> items, // The items whose stock should be released
        Instant timestamp          // When the cancellation occurred
) {
    /**
     * Nested record representing a single item in the cancelled order.
     * Contains only the product ID and quantity — enough info for the
     * Inventory Service to know how much stock to release.
     */
    public record CancelledItem(String productId, int quantity) {}
}
