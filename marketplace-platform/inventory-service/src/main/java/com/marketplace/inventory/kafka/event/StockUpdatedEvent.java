package com.marketplace.inventory.kafka.event;

import java.time.Instant;

/**
 * Represents an event published to Kafka after a stock operation has been processed.
 * The Inventory Service publishes this event to the "inventory-events" topic,
 * and the Order Service consumes it to know whether stock reservation succeeded or failed.
 *
 * If success is true, the order can be confirmed. If false, the order should be rejected.
 *
 * This is a Java "record" — an immutable data class with auto-generated constructor,
 * getters, equals(), hashCode(), and toString().
 */
public record StockUpdatedEvent(
        String orderId,    // The order this stock update relates to
        String productId,  // Which product's stock was updated
        boolean success,   // Whether the stock operation succeeded (true) or failed (false)
        String message,    // Human-readable description (e.g., "Stock reserved" or "Failed to reserve stock")
        Instant timestamp  // When the stock update occurred
) {}
