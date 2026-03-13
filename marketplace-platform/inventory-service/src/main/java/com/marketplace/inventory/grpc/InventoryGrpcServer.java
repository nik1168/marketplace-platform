package com.marketplace.inventory.grpc;

import com.marketplace.inventory.service.InventoryStockService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * gRPC server that exposes inventory operations to other microservices over the network.
 *
 * gRPC is a high-performance communication protocol (alternative to REST) that uses
 * Protocol Buffers for fast serialization. The Order Service calls this server to check
 * stock availability before accepting new orders.
 *
 * The method signatures and request/response types are generated from the proto/inventory.proto file.
 * This class extends the auto-generated base class and provides the actual business logic.
 */
// @GrpcService registers this class as a gRPC service endpoint.
// It's similar to @RestController for REST APIs but for gRPC.
// Spring Boot automatically starts the gRPC server (on port 9090) and routes requests here.
@GrpcService
public class InventoryGrpcServer extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final InventoryStockService stockService;

    /**
     * Constructor injection: Spring provides the InventoryStockService instance automatically.
     */
    public InventoryGrpcServer(InventoryStockService stockService) {
        this.stockService = stockService;
    }

    /**
     * Handles a gRPC request to check if a product has enough stock.
     * The Order Service calls this before creating a new order to verify availability.
     *
     * @param request          contains the productId and desired quantity
     * @param responseObserver used to send the response back to the caller (gRPC uses an observer pattern)
     */
    @Override
    public void checkStock(CheckStockRequest request, StreamObserver<CheckStockResponse> responseObserver) {
        // Check if the requested quantity is available
        boolean available = stockService.checkStock(request.getProductId(), request.getQuantity());
        // Also fetch the current available stock count to include in the response
        int currentStock = stockService.getCurrentStock(request.getProductId());

        // Build the gRPC response using the generated builder pattern
        CheckStockResponse response = CheckStockResponse.newBuilder()
                .setAvailable(available)
                .setCurrentStock(currentStock)
                .build();

        // Send the response back to the caller
        responseObserver.onNext(response);
        // Signal that we're done sending responses (gRPC supports streaming; onCompleted ends the call)
        responseObserver.onCompleted();
    }

    /**
     * Handles a gRPC request to reserve stock for an order.
     * This sets aside inventory so it can't be claimed by other orders.
     *
     * @param request          contains productId, quantity, and orderId
     * @param responseObserver used to send the response back to the caller
     */
    @Override
    public void reserveStock(ReserveStockRequest request, StreamObserver<ReserveStockResponse> responseObserver) {
        // Attempt to reserve the requested stock
        boolean success = stockService.reserveStock(
                request.getProductId(), request.getQuantity(), request.getOrderId());

        // Build the response indicating whether the reservation succeeded or failed
        ReserveStockResponse response = ReserveStockResponse.newBuilder()
                .setSuccess(success)
                .setMessage(success ? "Stock reserved" : "Failed to reserve stock")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
