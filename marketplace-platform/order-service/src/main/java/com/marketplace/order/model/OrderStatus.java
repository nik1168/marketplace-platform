package com.marketplace.order.model;

/**
 * Represents the possible states an order can be in throughout its lifecycle.
 * An enum is used here because the set of statuses is fixed and well-known.
 *
 * Typical lifecycle flow:
 *   PENDING -> CONFIRMED -> SHIPPED
 *   PENDING -> REJECTED  (if stock is unavailable)
 *   PENDING -> CANCELLED (if the customer cancels)
 *   CONFIRMED -> CANCELLED (if the customer cancels after confirmation)
 */
public enum OrderStatus {
    /** Order has been created but not yet confirmed by the inventory system */
    PENDING,

    /** Inventory confirmed that stock was successfully reserved */
    CONFIRMED,

    /** Inventory could not reserve stock — the order is rejected */
    REJECTED,

    /** Order has been shipped to the customer */
    SHIPPED,

    /** Order was cancelled by the customer or the system */
    CANCELLED
}
