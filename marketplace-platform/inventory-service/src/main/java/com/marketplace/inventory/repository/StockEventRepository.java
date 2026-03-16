package com.marketplace.inventory.repository;

import com.marketplace.inventory.model.StockEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface StockEventRepository extends MongoRepository<StockEvent, String> {

    List<StockEvent> findByProductId(String productId);

    List<StockEvent> findByOrderId(String orderId);
}
