package com.marketplace.inventory.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

/**
 * Represents a product in the inventory system.
 * Each product tracks its total stock and how much of that stock is reserved for pending orders.
 * This class is stored as a document in the MongoDB "products" collection.
 */
// @Document tells Spring Data MongoDB to store instances of this class in the "products" collection.
// It's similar to @Entity in JPA but for MongoDB (a NoSQL document database).
@Document(collection = "products")
public class Product {

    // @Id marks this field as the unique identifier for the MongoDB document.
    // MongoDB will auto-generate this value if not set.
    @Id
    private String id;

    // @Indexed(unique = true) creates a unique index on this field in MongoDB,
    // ensuring no two products can have the same productId, and speeding up lookups by productId.
    @Indexed(unique = true)
    private String productId;

    private String name;        // Human-readable product name (e.g., "Wireless Mouse")
    private String sku;         // Stock Keeping Unit — a unique code used in warehouses to identify products
    private String category;    // Product category (e.g., "electronics", "furniture")
    private int currentStock;   // Total number of units physically available
    private int reservedStock;  // Units reserved for pending orders but not yet shipped
    private Instant lastUpdated = Instant.now(); // Timestamp of the last stock change

    // @Version enables optimistic locking: MongoDB will reject a save if another process
    // modified the same document since it was loaded. This prevents lost updates when
    // multiple requests try to reserve stock at the same time.
    @Version
    private Long version;

    /** No-argument constructor required by Spring Data MongoDB for deserialization. */
    public Product() {}

    /**
     * Creates a new product with initial stock. Reserved stock starts at zero
     * because a brand-new product has no pending orders.
     */
    public Product(String productId, String name, String sku, String category, int currentStock) {
        this.productId = productId;
        this.name = name;
        this.sku = sku;
        this.category = category;
        this.currentStock = currentStock;
        this.reservedStock = 0;
    }

    /**
     * Calculates how many units are actually available for new orders.
     * Available stock = total stock minus what's already reserved.
     */
    public int getAvailableStock() {
        return currentStock - reservedStock;
    }

    /**
     * Checks whether there are enough unreserved units to fulfill a request for the given quantity.
     */
    public boolean hasAvailableStock(int quantity) {
        return getAvailableStock() >= quantity;
    }

    /**
     * Reserves a certain quantity of stock for an order.
     * This does NOT remove items from inventory — it marks them as "spoken for"
     * so other orders can't claim the same units.
     * Throws an exception if there aren't enough available units.
     */
    public void reserveStock(int quantity) {
        if (!hasAvailableStock(quantity)) {
            throw new IllegalStateException("Insufficient stock for product: " + productId);
        }
        this.reservedStock += quantity;
        this.lastUpdated = Instant.now();
    }

    /**
     * Releases previously reserved stock back to the available pool.
     * This is used when an order is cancelled — the reserved items become available again.
     * Uses Math.max(0, ...) to prevent reservedStock from going negative if there's a mismatch.
     */
    public void releaseStock(int quantity) {
        this.reservedStock = Math.max(0, this.reservedStock - quantity);
        this.lastUpdated = Instant.now();
    }

    /**
     * Confirms a reservation by actually removing the items from stock.
     * This is called after an order has been fully confirmed and is ready to ship.
     * Both currentStock and reservedStock decrease because the items are leaving the warehouse.
     */
    public void confirmReservation(int quantity) {
        this.currentStock -= quantity;
        this.reservedStock -= quantity;
        this.lastUpdated = Instant.now();
    }

    // --- Getters ---
    // Standard getter methods to access the private fields.
    // These are used by Spring Data, gRPC handlers, and other services.

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getName() { return name; }
    public String getSku() { return sku; }
    public String getCategory() { return category; }
    public int getCurrentStock() { return currentStock; }
    public int getReservedStock() { return reservedStock; }
    public Instant getLastUpdated() { return lastUpdated; }
    public Long getVersion() { return version; }
}
