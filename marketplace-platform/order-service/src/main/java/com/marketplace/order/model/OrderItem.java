package com.marketplace.order.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * JPA entity representing a single line item within an order.
 * For example, "2 units of product X at $10.00 each" would be one OrderItem.
 * Each OrderItem belongs to exactly one Order (many-to-one relationship).
 * Maps to the "order_items" table in PostgreSQL.
 */
// @Entity marks this class as a JPA entity mapped to a database table
@Entity
// @Table specifies the database table name for this entity
@Table(name = "order_items")
public class OrderItem {

    // Primary key with auto-generated UUID
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // @ManyToOne defines the owning side of the relationship — many items belong to one order
    // FetchType.LAZY means the parent Order is only loaded from the DB when actually accessed,
    // which improves performance by avoiding unnecessary database queries
    @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn specifies the foreign key column in the order_items table
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // The ID of the product being ordered (references a product in the Inventory Service)
    @Column(nullable = false)
    private String productId;

    // How many units of this product were ordered
    @Column(nullable = false)
    private int quantity;

    // The price per single unit of the product
    // precision=10 allows up to 10 total digits, scale=2 means 2 decimal places
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /** Default no-arg constructor required by JPA */
    public OrderItem() {}

    /** Creates a new order item with the specified product, quantity, and price per unit */
    public OrderItem(String productId, int quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    /**
     * Calculates the subtotal for this line item (unit price * quantity).
     * For example, 3 units at $5.00 = $15.00
     */
    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    // --- Getters and setters ---
    public UUID getId() { return id; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
