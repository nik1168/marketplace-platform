package com.marketplace.inventory.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

/**
 * Represents an event that changed the stock level of a product.
 * Every time stock is reserved, released, confirmed, or replenished, a StockEvent is created.
 * This provides a full audit trail (history) of all stock changes — useful for debugging,
 * analytics, and understanding how inventory levels changed over time.
 */
// @Document maps this class to the "stock_events" collection in MongoDB.
@Document(collection = "stock_events")
public class StockEvent {

    // @Id marks this as the MongoDB document identifier, auto-generated if not set.
    @Id
    private String id;

    private String productId;              // Which product was affected
    private StockEventType eventType;      // What happened (RESERVED, RELEASED, CONFIRMED, REPLENISHED)
    private int quantity;                  // How many units were involved
    private String orderId;               // Which order triggered this event (can be null for replenishments)
    private Instant timestamp = Instant.now(); // When this event occurred

    /** No-argument constructor required by Spring Data MongoDB for deserialization. */
    public StockEvent() {}

    /**
     * Creates a new stock event recording a change to a product's inventory.
     *
     * @param productId the product whose stock changed
     * @param eventType what kind of change happened (e.g., RESERVED, RELEASED)
     * @param quantity  how many units were affected
     * @param orderId   the order that caused this change (if applicable)
     */
    public StockEvent(String productId, StockEventType eventType, int quantity, String orderId) {
        this.productId = productId;
        this.eventType = eventType;
        this.quantity = quantity;
        this.orderId = orderId;
    }

    // --- Getters ---
    // Standard getter methods for reading event data.

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public StockEventType getEventType() { return eventType; }
    public int getQuantity() { return quantity; }
    public String getOrderId() { return orderId; }
    public Instant getTimestamp() { return timestamp; }
}
