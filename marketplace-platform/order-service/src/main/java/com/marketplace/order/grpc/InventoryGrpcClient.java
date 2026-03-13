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

/**
 * gRPC client that communicates synchronously with the Inventory Service.
 *
 * gRPC (Google Remote Procedure Call) is a high-performance protocol for service-to-service
 * communication. Unlike REST (which uses JSON over HTTP), gRPC uses Protocol Buffers (a binary
 * format) which is faster and more compact. The method signatures are defined in a .proto file.
 *
 * This client provides two operations:
 * - checkStock: asks the Inventory Service if a product has enough stock
 * - reserveStock: asks the Inventory Service to reserve stock for an order
 */
// @Component marks this class as a Spring-managed bean so it can be injected into other classes
@Component
public class InventoryGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryGrpcClient.class);

    // @GrpcClient injects a gRPC stub (client) configured to connect to the "inventory-service"
    // The connection details (host, port) are configured in application.yml
    // "BlockingStub" means calls are synchronous — the method waits for a response before returning
    @GrpcClient("inventory-service")
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    /**
     * Checks if the Inventory Service has enough stock for the given product and quantity.
     *
     * @param productId the product to check
     * @param quantity  the number of units needed
     * @return true if stock is available, false otherwise
     */
    public boolean checkStock(String productId, int quantity) {
        // Build the gRPC request using the generated builder pattern (from the .proto definition)
        CheckStockResponse response = inventoryStub.checkStock(
                CheckStockRequest.newBuilder()
                        .setProductId(productId)
                        .setQuantity(quantity)
                        .build());
        log.info("Stock check for product {}: available={}, currentStock={}",
                productId, response.getAvailable(), response.getCurrentStock());
        return response.getAvailable();
    }

    /**
     * Asks the Inventory Service to reserve stock for a specific order.
     * Reserved stock is held until the order is confirmed or cancelled.
     *
     * @param productId the product to reserve
     * @param quantity  the number of units to reserve
     * @param orderId   the order that the reservation is for
     * @return true if the reservation was successful, false otherwise
     */
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
