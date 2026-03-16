package com.marketplace.order.service;

import com.marketplace.order.dto.CreateOrderRequest;
import com.marketplace.order.grpc.InventoryGrpcClient;
import com.marketplace.order.kafka.OrderEventProducer;
import com.marketplace.order.model.Order;
import com.marketplace.order.model.OrderItem;
import com.marketplace.order.model.OrderStatus;
import com.marketplace.order.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final InventoryGrpcClient inventoryClient;
    private final OrderEventProducer eventProducer;
    private final Counter ordersCreatedCounter;
    private final Counter ordersRejectedCounter;
    private final Timer orderCreationTimer;

    public OrderService(OrderRepository orderRepository, InventoryGrpcClient inventoryClient,
                        OrderEventProducer eventProducer, Counter ordersCreatedCounter,
                        Counter ordersRejectedCounter, Timer orderCreationTimer) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
        this.eventProducer = eventProducer;
        this.ordersCreatedCounter = ordersCreatedCounter;
        this.ordersRejectedCounter = ordersRejectedCounter;
        this.orderCreationTimer = orderCreationTimer;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        long start = System.nanoTime();
        try {
            for (var item : request.items()) {
                if (!inventoryClient.checkStock(item.productId(), item.quantity())) {
                    throw new InsufficientStockException(item.productId());
                }
            }

            Order order = new Order(request.customerId());
            request.items().forEach(item ->
                    order.addItem(new OrderItem(item.productId(), item.quantity(), item.unitPrice()))
            );
            Order saved = orderRepository.save(order);

            for (var item : request.items()) {
                inventoryClient.reserveStock(item.productId(), item.quantity(), saved.getId().toString());
            }

            eventProducer.publishOrderPlaced(saved);
            ordersCreatedCounter.increment();
            return saved;
        } catch (InsufficientStockException e) {
            ordersRejectedCounter.increment();
            throw e;
        } finally {
            orderCreationTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        // Force initialization of lazy-loaded items while the session is still open
        order.getItems().size();
        return order;
    }

    @Transactional(readOnly = true)
    public Page<Order> listOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Transactional
    public Order cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot cancel order in status: " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        // Force initialization of lazy-loaded items while the session is still open
        saved.getItems().size();
        eventProducer.publishOrderCancelled(saved);
        return saved;
    }

    @Transactional
    public void updateOrderStatus(UUID orderId, OrderStatus status) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(status);
            orderRepository.save(order);
        });
    }

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(UUID id) {
            super("Order not found: " + id);
        }
    }

    public static class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(String productId) {
            super("Insufficient stock for product: " + productId);
        }
    }
}
