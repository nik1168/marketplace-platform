package com.marketplace.inventory.repository;

import com.marketplace.inventory.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findByProductId(String productId);
}
