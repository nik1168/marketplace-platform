package com.marketplace.inventory.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Represents an event received from Kafka when a new order has been placed.
 * The Order Service publishes this event, and the Inventory Service consumes it
 * to reserve stock for each item in the order.
 *
 * This is a Java "record" — a compact way to define an immutable data class.
 * Records automatically generate the constructor, getters (e.g., orderId()), equals(), hashCode(), and toString().
 */
public record OrderPlacedEvent(
        String orderId,           // Unique identifier of the order
        String customerId,        // Who placed the order
        List<OrderItemEvent> items, // List of products and quantities in this order
        BigDecimal totalAmount,   // Total price of the order
        Instant timestamp         // When the order was placed
) {
    /**
     * Represents a single item (product + quantity + price) within an order.
     * Nested inside OrderPlacedEvent because it only makes sense in this context.
     */
    public record OrderItemEvent(String productId, int quantity, BigDecimal unitPrice) {}
}
