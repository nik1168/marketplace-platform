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

@Service
public class InventoryStockService {

    private static final Logger log = LoggerFactory.getLogger(InventoryStockService.class);

    private final ProductRepository productRepository;
    private final StockEventRepository stockEventRepository;

    public InventoryStockService(ProductRepository productRepository, StockEventRepository stockEventRepository) {
        this.productRepository = productRepository;
        this.stockEventRepository = stockEventRepository;
    }

    public boolean checkStock(String productId, int quantity) {
        return productRepository.findByProductId(productId)
                .map(product -> product.hasAvailableStock(quantity))
                .orElse(false);
    }

    public int getCurrentStock(String productId) {
        return productRepository.findByProductId(productId)
                .map(Product::getAvailableStock)
                .orElse(0);
    }

    public boolean reserveStock(String productId, int quantity, String orderId) {
        try {
            Product product = productRepository.findByProductId(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            product.reserveStock(quantity);
            productRepository.save(product);

            stockEventRepository.save(new StockEvent(productId, StockEventType.RESERVED, quantity, orderId));
            log.info("Reserved {} units of product {} for order {}", quantity, productId, orderId);
            return true;
        } catch (OptimisticLockingFailureException e) {
            log.warn("Optimistic lock conflict reserving stock for product {}, order {}", productId, orderId);
            return false;
        } catch (IllegalStateException e) {
            log.warn("Insufficient stock for product {}: {}", productId, e.getMessage());
            return false;
        }
    }

    public void releaseStock(String productId, int quantity, String orderId) {
        productRepository.findByProductId(productId).ifPresent(product -> {
            product.releaseStock(quantity);
            productRepository.save(product);
            stockEventRepository.save(new StockEvent(productId, StockEventType.RELEASED, quantity, orderId));
            log.info("Released {} units of product {} for order {}", quantity, productId, orderId);
        });
    }
}
