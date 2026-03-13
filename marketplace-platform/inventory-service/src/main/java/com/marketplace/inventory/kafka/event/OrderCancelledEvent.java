package com.marketplace.inventory.kafka.event;

import java.time.Instant;
import java.util.List;

/**
 * Represents an event received from Kafka when an order has been cancelled.
 * When this event is consumed, the Inventory Service releases the previously reserved stock
 * so it becomes available for other orders.
 *
 * This is a Java "record" — an immutable data class with auto-generated constructor,
 * getters, equals(), hashCode(), and toString().
 */
public record OrderCancelledEvent(
        String orderId,              // Which order was cancelled
        List<CancelledItem> items,   // The items that need their stock released
        Instant timestamp            // When the cancellation occurred
) {
    /**
     * Represents a single cancelled item — the product and quantity to release back to inventory.
     */
    public record CancelledItem(String productId, int quantity) {}
}
