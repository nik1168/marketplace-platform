package com.marketplace.inventory.grpc;

import com.marketplace.inventory.service.InventoryStockService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class InventoryGrpcServer extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final InventoryStockService stockService;

    public InventoryGrpcServer(InventoryStockService stockService) {
        this.stockService = stockService;
    }

    @Override
    public void checkStock(CheckStockRequest request, StreamObserver<CheckStockResponse> responseObserver) {
        boolean available = stockService.checkStock(request.getProductId(), request.getQuantity());
        int currentStock = stockService.getCurrentStock(request.getProductId());

        CheckStockResponse response = CheckStockResponse.newBuilder()
                .setAvailable(available)
                .setCurrentStock(currentStock)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void reserveStock(ReserveStockRequest request, StreamObserver<ReserveStockResponse> responseObserver) {
        boolean success = stockService.reserveStock(
                request.getProductId(), request.getQuantity(), request.getOrderId());

        ReserveStockResponse response = ReserveStockResponse.newBuilder()
                .setSuccess(success)
                .setMessage(success ? "Stock reserved" : "Failed to reserve stock")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
