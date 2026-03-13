package com.marketplace.inventory.model;

/**
 * Defines the different types of stock changes that can happen to a product.
 * Used in StockEvent to record what kind of inventory operation was performed.
 */
public enum StockEventType {
    RESERVED,     // Stock was set aside for a pending order (units are still in warehouse but "spoken for")
    RELEASED,     // A reservation was cancelled, making those units available again
    CONFIRMED,    // A reservation was finalized — units are being shipped out of the warehouse
    REPLENISHED   // New units were added to inventory (e.g., a new shipment arrived)
}
