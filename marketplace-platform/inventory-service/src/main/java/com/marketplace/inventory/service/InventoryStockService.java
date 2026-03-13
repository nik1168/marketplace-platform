package com.marketplace.inventory.service;

import com.marketplace.inventory.model.Product;
import com.marketplace.inventory.model.StockEvent;
import com.marketplace.inventory.model.StockEventType;
import com.marketplace.inventory.repository.ProductRepository;
import com.marketplace.inventory.repository.StockEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Core business logic for managing inventory stock levels.
 * This service handles checking stock availability, reserving stock for orders,
 * and releasing stock when orders are cancelled.
 *
 * It is used by both the gRPC server (for synchronous stock checks from Order Service)
 * and the Kafka consumer (for asynchronous stock reservation when orders are placed).
 */
// @Service marks this class as a Spring-managed service bean.
// Spring will create a single instance and inject it wherever it's needed (dependency injection).
@Service
public class InventoryStockService {

    // Logger for recording important events and errors. Using SLF4J, a standard Java logging API.
    private static final Logger log = LoggerFactory.getLogger(InventoryStockService.class);

    private final ProductRepository productRepository;       // For reading/writing Product documents in MongoDB
    private final StockEventRepository stockEventRepository; // For saving stock change history

    /**
     * Constructor-based dependency injection: Spring automatically provides
     * the repository instances when creating this service.
     */
    public InventoryStockService(ProductRepository productRepository, StockEventRepository stockEventRepository) {
        this.productRepository = productRepository;
        this.stockEventRepository = stockEventRepository;
    }

    /**
     * Checks if a product has enough available stock to fulfill a request.
     *
     * @param productId the product to check
     * @param quantity  how many units are needed
     * @return true if enough stock is available, false if product doesn't exist or stock is insufficient
     */
    public boolean checkStock(String productId, int quantity) {
        return productRepository.findByProductId(productId)
                .map(product -> product.hasAvailableStock(quantity)) // If product exists, check its stock
                .orElse(false); // If product doesn't exist, treat as "not available"
    }

    /**
     * Gets the current available stock for a product (total stock minus reserved stock).
     *
     * @param productId the product to look up
     * @return available stock count, or 0 if the product doesn't exist
     */
    public int getCurrentStock(String productId) {
        return productRepository.findByProductId(productId)
                .map(Product::getAvailableStock) // Method reference — same as writing (p) -> p.getAvailableStock()
                .orElse(0);
    }

    /**
     * Attempts to reserve stock for a specific order.
     * This marks units as "reserved" so other orders can't claim them.
     *
     * Uses optimistic locking: if two requests try to reserve stock at the same time,
     * one will succeed and the other will get an OptimisticLockingFailureException.
     * This prevents overselling without using database-level locks.
     *
     * @param productId the product to reserve
     * @param quantity  how many units to reserve
     * @param orderId   the order requesting the reservation
     * @return true if reservation succeeded, false if it failed for any reason
     */
    public boolean reserveStock(String productId, int quantity, String orderId) {
        try {
            // Look up the product; throw an exception if it doesn't exist
            Product product = productRepository.findByProductId(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            // Mark units as reserved (this will throw IllegalStateException if stock is insufficient)
            product.reserveStock(quantity);
            // Save the updated product back to MongoDB
            productRepository.save(product);

            // Record this stock change in the event history for auditing
            stockEventRepository.save(new StockEvent(productId, StockEventType.RESERVED, quantity, orderId));
            log.info("Reserved {} units of product {} for order {}", quantity, productId, orderId);
            return true;
        } catch (OptimisticLockingFailureException e) {
            // Another request modified this product at the same time — the @Version field didn't match.
            // This is expected under high concurrency and is handled gracefully by returning false.
            log.warn("Optimistic lock conflict reserving stock for product {}, order {}", productId, orderId);
            return false;
        } catch (IllegalStateException e) {
            // Not enough available stock to fulfill the reservation
            log.warn("Insufficient stock for product {}: {}", productId, e.getMessage());
            return false;
        }
    }

    /**
     * Releases previously reserved stock back to the available pool.
     * Called when an order is cancelled — the reserved items become available for other orders.
     *
     * @param productId the product whose stock should be released
     * @param quantity  how many units to release
     * @param orderId   the order that originally reserved the stock
     */
    public void releaseStock(String productId, int quantity, String orderId) {
        // ifPresent: only perform the release if the product exists; silently skip if it doesn't
        productRepository.findByProductId(productId).ifPresent(product -> {
            product.releaseStock(quantity);
            productRepository.save(product);
            // Record the release event for auditing
            stockEventRepository.save(new StockEvent(productId, StockEventType.RELEASED, quantity, orderId));
            log.info("Released {} units of product {} for order {}", quantity, productId, orderId);
        });
    }
}
