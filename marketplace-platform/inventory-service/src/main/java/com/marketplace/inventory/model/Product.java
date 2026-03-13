package com.marketplace.inventory.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "products")
public class Product {

    @Id
    private String id;

    @Indexed(unique = true)
    private String productId;

    private String name;
    private String sku;
    private String category;
    private int currentStock;
    private int reservedStock;
    private Instant lastUpdated = Instant.now();

    @Version
    private Long version;

    public Product() {}

    public Product(String productId, String name, String sku, String category, int currentStock) {
        this.productId = productId;
        this.name = name;
        this.sku = sku;
        this.category = category;
        this.currentStock = currentStock;
        this.reservedStock = 0;
    }

    public int getAvailableStock() {
        return currentStock - reservedStock;
    }

    public boolean hasAvailableStock(int quantity) {
        return getAvailableStock() >= quantity;
    }

    public void reserveStock(int quantity) {
        if (!hasAvailableStock(quantity)) {
            throw new IllegalStateException("Insufficient stock for product: " + productId);
        }
        this.reservedStock += quantity;
        this.lastUpdated = Instant.now();
    }

    public void releaseStock(int quantity) {
        this.reservedStock = Math.max(0, this.reservedStock - quantity);
        this.lastUpdated = Instant.now();
    }

    public void confirmReservation(int quantity) {
        this.currentStock -= quantity;
        this.reservedStock -= quantity;
        this.lastUpdated = Instant.now();
    }

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
