package com.marketplace.inventory.repository;

import com.marketplace.inventory.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

/**
 * Repository interface for accessing Product documents in MongoDB.
 *
 * By extending MongoRepository, Spring Data automatically provides common database operations
 * (save, findById, findAll, delete, count, etc.) without writing any implementation code.
 * Spring generates the implementation class at runtime based on this interface definition.
 */
public interface ProductRepository extends MongoRepository<Product, String> {

    /**
     * Finds a product by its business-level productId (e.g., "prod-1").
     * Spring Data automatically generates the MongoDB query from the method name:
     * "findByProductId" becomes a query like { productId: <value> }.
     *
     * Returns Optional to safely handle the case where the product doesn't exist.
     */
    Optional<Product> findByProductId(String productId);
}
