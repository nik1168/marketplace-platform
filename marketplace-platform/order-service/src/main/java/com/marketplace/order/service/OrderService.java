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

/**
 * Core business logic for managing orders.
 * This service coordinates between the database (via OrderRepository),
 * the Inventory Service (via gRPC), and Kafka (via OrderEventProducer)
 * to create, retrieve, cancel, and update orders.
 */
// @Service marks this class as a Spring-managed service bean so it can be
// automatically injected into other components (like the controller)
@Service
public class OrderService {

    // Logger for recording informational and error messages during runtime
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // Database access for orders
    private final OrderRepository orderRepository;
    // gRPC client to communicate synchronously with the Inventory Service
    private final InventoryGrpcClient inventoryClient;
    // Kafka producer to publish order events asynchronously
    private final OrderEventProducer eventProducer;
    // Micrometer metrics for monitoring: counts successful orders
    private final Counter ordersCreatedCounter;
    // Micrometer metrics for monitoring: counts rejected orders
    private final Counter ordersRejectedCounter;
    // Micrometer metrics for monitoring: measures how long order creation takes
    private final Timer orderCreationTimer;

    /**
     * Constructor injection — Spring automatically provides all these dependencies.
     * This is the preferred way to inject dependencies because it makes them required
     * and makes the class easier to test.
     */
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

    /**
     * Creates a new order: checks stock via gRPC, persists the order, reserves stock,
     * and publishes an OrderPlaced event to Kafka.
     *
     * The full flow:
     * 1. Check that all requested products have enough stock (gRPC call)
     * 2. Save the order to the database
     * 3. Reserve stock for each product (gRPC call)
     * 4. Publish an "OrderPlaced" event to Kafka so other services can react
     * 5. Track metrics (success/failure counters and timing)
     */
    // @Transactional ensures all database operations in this method run in a single transaction.
    // If anything fails, all database changes are rolled back automatically.
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        // Record the start time so we can measure how long order creation takes
        long start = System.nanoTime();
        try {
            // Step 1: Check stock for all items via gRPC call to the Inventory Service
            for (var item : request.items()) {
                if (!inventoryClient.checkStock(item.productId(), item.quantity())) {
                    throw new InsufficientStockException(item.productId());
                }
            }

            // Step 2: Create the Order entity and add all items to it
            Order order = new Order(request.customerId());
            request.items().forEach(item ->
                    order.addItem(new OrderItem(item.productId(), item.quantity(), item.unitPrice()))
            );
            // Save the order to the database (status starts as PENDING)
            Order saved = orderRepository.save(order);

            // Step 3: Reserve stock for all items via gRPC
            for (var item : request.items()) {
                inventoryClient.reserveStock(item.productId(), item.quantity(), saved.getId().toString());
            }

            // Step 4: Publish an OrderPlaced event to Kafka so the Inventory Service
            // and any other interested services can process this order asynchronously
            eventProducer.publishOrderPlaced(saved);

            // Step 5: Increment the "orders created" metric for monitoring dashboards
            ordersCreatedCounter.increment();
            return saved;
        } catch (InsufficientStockException e) {
            // Track rejected orders separately for monitoring
            ordersRejectedCounter.increment();
            throw e;
        } finally {
            // Always record how long the operation took, even if it failed
            orderCreationTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Retrieves a single order by its ID, including all its items.
     */
    // @Transactional(readOnly = true) optimizes the transaction for read-only access,
    // which can improve performance (e.g., skipping dirty-checking)
    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        // Force initialization of the lazy-loaded items collection while still inside
        // the transaction. Without this, accessing items outside the transaction would
        // throw a LazyInitializationException because the database session would be closed.
        order.getItems().size();
        return order;
    }

    /**
     * Returns a paginated list of all orders.
     * Pageable allows clients to request specific pages (e.g., page 0, size 20).
     */
    @Transactional(readOnly = true)
    public Page<Order> listOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    /**
     * Cancels an order if it's in a cancellable state (PENDING or CONFIRMED).
     * Publishes an OrderCancelled event to Kafka so the Inventory Service can release the reserved stock.
     */
    @Transactional
    public Order cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Only orders that are PENDING or CONFIRMED can be cancelled.
        // Orders that are already SHIPPED, REJECTED, or CANCELLED cannot be cancelled.
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot cancel order in status: " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        // Force initialization of items within the transaction (same reason as in getOrder)
        saved.getItems().size();
        // Notify other services (especially Inventory) that this order has been cancelled
        eventProducer.publishOrderCancelled(saved);
        return saved;
    }

    /**
     * Updates the status of an order. Called by the Kafka consumer when the Inventory Service
     * confirms or rejects stock reservation (via a StockUpdated event).
     */
    @Transactional
    public void updateOrderStatus(UUID orderId, OrderStatus status) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(status);
            orderRepository.save(order);
        });
    }

    /**
     * Custom exception thrown when an order is not found in the database.
     * Defined as a static inner class to keep it close to the service that uses it.
     */
    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(UUID id) {
            super("Order not found: " + id);
        }
    }

    /**
     * Custom exception thrown when a product doesn't have enough stock to fulfill the order.
     */
    public static class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(String productId) {
            super("Insufficient stock for product: " + productId);
        }
    }
}
