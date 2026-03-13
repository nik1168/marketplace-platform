package com.marketplace.order.repository;

import com.marketplace.order.model.Order;
import com.marketplace.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository interface for accessing Order data in the database.
 *
 * By extending JpaRepository, Spring Data JPA automatically provides implementations
 * for common operations like save(), findById(), findAll(), delete(), etc.
 * — no manual SQL or implementation code is needed.
 *
 * The generic parameters <Order, UUID> mean:
 * - Order is the entity type this repository manages
 * - UUID is the type of the entity's primary key
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Finds all orders placed by a specific customer, with pagination support.
     * Spring Data JPA generates the SQL query automatically based on the method name:
     * "findByCustomerId" becomes "SELECT * FROM orders WHERE customer_id = ?"
     */
    Page<Order> findByCustomerId(String customerId, Pageable pageable);

    /**
     * Finds all orders with a specific status (e.g., PENDING, CONFIRMED), with pagination.
     * Again, the query is auto-generated from the method name.
     */
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
}
