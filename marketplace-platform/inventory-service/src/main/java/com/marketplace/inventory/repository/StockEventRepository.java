package com.marketplace.inventory.repository;

import com.marketplace.inventory.model.StockEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

/**
 * Repository interface for accessing StockEvent documents in MongoDB.
 *
 * Extends MongoRepository to get standard CRUD operations for free.
 * Also defines custom query methods that Spring Data implements automatically
 * based on the method name convention.
 */
public interface StockEventRepository extends MongoRepository<StockEvent, String> {

    /**
     * Finds all stock events related to a specific product.
     * Useful for viewing the full history of inventory changes for a product.
     * Spring Data generates the query automatically from the method name.
     */
    List<StockEvent> findByProductId(String productId);

    /**
     * Finds all stock events triggered by a specific order.
     * Useful for understanding all the inventory impacts of a single order.
     * Spring Data generates the query automatically from the method name.
     */
    List<StockEvent> findByOrderId(String orderId);
}
