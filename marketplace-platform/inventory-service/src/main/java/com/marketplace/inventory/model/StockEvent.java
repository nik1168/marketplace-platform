package com.marketplace.inventory.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

/**
 * Audit trail entry for every stock level change (reserve, release, confirm, replenish).
 */
@Document(collection = "stock_events")
public class StockEvent {

    @Id
    private String id;

    private String productId;
    private StockEventType eventType;
    private int quantity;
    private String orderId;
    private Instant timestamp = Instant.now();

    public StockEvent() {}

    public StockEvent(String productId, StockEventType eventType, int quantity, String orderId) {
        this.productId = productId;
        this.eventType = eventType;
        this.quantity = quantity;
        this.orderId = orderId;
    }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public StockEventType getEventType() { return eventType; }
    public int getQuantity() { return quantity; }
    public String getOrderId() { return orderId; }
    public Instant getTimestamp() { return timestamp; }
}
