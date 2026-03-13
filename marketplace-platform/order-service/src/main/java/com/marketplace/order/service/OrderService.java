package com.marketplace.order.service;

import com.marketplace.order.dto.CreateOrderRequest;
import com.marketplace.order.model.Order;
import com.marketplace.order.model.OrderItem;
import com.marketplace.order.model.OrderStatus;
import com.marketplace.order.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order(request.customerId());
        request.items().forEach(item ->
                order.addItem(new OrderItem(item.productId(), item.quantity(), item.unitPrice()))
        );
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        // Force initialization of items within the transaction
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
        // Force initialization of items within the transaction
        saved.getItems().size();
        return saved;
    }

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(UUID id) {
            super("Order not found: " + id);
        }
    }
}
