package com.marketplace.order.grpc;

import com.marketplace.inventory.grpc.CheckStockRequest;
import com.marketplace.inventory.grpc.CheckStockResponse;
import com.marketplace.inventory.grpc.InventoryServiceGrpc;
import com.marketplace.inventory.grpc.ReserveStockRequest;
import com.marketplace.inventory.grpc.ReserveStockResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InventoryGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryGrpcClient.class);

    @GrpcClient("inventory-service")
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    public boolean checkStock(String productId, int quantity) {
        CheckStockResponse response = inventoryStub.checkStock(
                CheckStockRequest.newBuilder()
                        .setProductId(productId)
                        .setQuantity(quantity)
                        .build());
        log.info("Stock check for product {}: available={}, currentStock={}",
                productId, response.getAvailable(), response.getCurrentStock());
        return response.getAvailable();
    }

    public boolean reserveStock(String productId, int quantity, String orderId) {
        ReserveStockResponse response = inventoryStub.reserveStock(
                ReserveStockRequest.newBuilder()
                        .setProductId(productId)
                        .setQuantity(quantity)
                        .setOrderId(orderId)
                        .build());
        log.info("Reserve stock for product {}, order {}: success={}", productId, orderId, response.getSuccess());
        return response.getSuccess();
    }
}
