package com.marketplace.order.kafka.event;

import java.time.Instant;

/**
 * Kafka event received from the Inventory Service after it processes a stock reservation.
 * This event tells the Order Service whether the stock reservation was successful or not.
 *
 * Flow: Order Service publishes OrderPlaced -> Inventory Service processes it ->
 *       Inventory Service publishes StockUpdated -> Order Service consumes it here
 *       and updates the order status to CONFIRMED or REJECTED.
 */
public record StockUpdatedEvent(
        String orderId,    // The order this stock update is for
        String productId,  // The product whose stock was updated
        boolean success,   // true if stock was reserved, false if reservation failed
        String message,    // Human-readable description of what happened
        Instant timestamp  // When the stock update occurred
) {}
