package com.marketplace.order.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Kafka event published when a new order is successfully created.
 * The Inventory Service consumes this event to process stock reservations.
 *
 * This is a Java record — an immutable data carrier that automatically generates
 * constructor, getters, equals(), hashCode(), and toString().
 * Records are ideal for events because events should be immutable (never changed after creation).
 */
public record OrderPlacedEvent(
        String orderId,          // Unique identifier of the created order
        String customerId,       // The customer who placed the order
        List<OrderItemEvent> items, // The products and quantities ordered
        BigDecimal totalAmount,  // Total cost of the order
        Instant timestamp        // When the event was created
) {
    /**
     * Nested record representing a single item within the OrderPlacedEvent.
     * Contains the product info needed by the Inventory Service to reserve stock.
     */
    public record OrderItemEvent(String productId, int quantity, BigDecimal unitPrice) {}
}
