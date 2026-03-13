package com.marketplace.order.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity representing a customer order in the database.
 * Each order belongs to one customer and contains one or more OrderItems.
 * This class maps to the "orders" table in PostgreSQL.
 */
// @Entity tells JPA that this class is a database entity (i.e., it maps to a table)
@Entity
// @Table specifies the table name — we use "orders" because "order" is a reserved SQL keyword
@Table(name = "orders")
public class Order {

    // @Id marks this field as the primary key of the table
    @Id
    // @GeneratedValue with UUID strategy tells JPA to auto-generate a unique UUID for each new order
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // @Column(nullable = false) means this field cannot be NULL in the database
    @Column(nullable = false)
    private String customerId;

    // @Enumerated(EnumType.STRING) stores the enum value as its name (e.g., "PENDING")
    // rather than as a number, making the database more readable
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    // BigDecimal is used for monetary values to avoid floating-point precision errors
    // precision=12 means up to 12 total digits, scale=2 means 2 decimal places
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    // updatable = false means this field is set once at creation and never changed
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    // @OneToMany defines a one-to-many relationship: one Order has many OrderItems
    // mappedBy = "order" means the OrderItem entity owns the relationship (has the foreign key)
    // cascade = ALL means all operations (save, delete, etc.) on Order also apply to its items
    // orphanRemoval = true means if an item is removed from this list, it's deleted from the DB
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    /** Default no-arg constructor required by JPA — it needs this to create entity instances */
    public Order() {}

    /** Creates a new order for the given customer, starting in PENDING status */
    public Order(String customerId) {
        this.customerId = customerId;
    }

    /**
     * Adds an item to this order and recalculates the total.
     * Also sets the back-reference from the item to this order
     * so JPA can properly manage the relationship.
     */
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
        recalculateTotal();
    }

    /**
     * Recalculates the order total by summing up each item's subtotal.
     * Called whenever an item is added to keep the total in sync.
     */
    private void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * JPA lifecycle callback that runs automatically before any update to this entity.
     * It keeps the updatedAt timestamp current.
     */
    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // --- Getters and setters ---
    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderItem> getItems() { return items; }
}
