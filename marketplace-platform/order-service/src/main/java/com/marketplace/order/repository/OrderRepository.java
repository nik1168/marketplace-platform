package com.marketplace.order.repository;

import com.marketplace.order.model.Order;
import com.marketplace.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Page<Order> findByCustomerId(String customerId, Pageable pageable);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
}
