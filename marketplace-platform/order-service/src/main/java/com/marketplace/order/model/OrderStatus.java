package com.marketplace.order.model;

/**
 * Lifecycle flow:
 *   PENDING -> CONFIRMED -> SHIPPED
 *   PENDING -> REJECTED  (if stock is unavailable)
 *   PENDING -> CANCELLED (if the customer cancels)
 *   CONFIRMED -> CANCELLED (if the customer cancels after confirmation)
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    SHIPPED,
    CANCELLED
}
