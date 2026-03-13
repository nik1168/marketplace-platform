package com.marketplace.order.controller;

import com.marketplace.order.dto.CreateOrderRequest;
import com.marketplace.order.dto.OrderResponse;
import com.marketplace.order.model.Order;
import com.marketplace.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/**
 * REST controller that exposes HTTP endpoints for managing orders.
 * This is the entry point for all client requests to the Order Service API.
 *
 * Available endpoints:
 *   POST   /api/orders          — Create a new order
 *   GET    /api/orders/{id}     — Get a specific order by ID
 *   GET    /api/orders          — List all orders (paginated)
 *   PUT    /api/orders/{id}/cancel — Cancel an order
 */
// @RestController combines @Controller and @ResponseBody — it tells Spring that
// every method returns data (JSON) directly, not a view/template name
@RestController
// @RequestMapping sets the base URL path for all endpoints in this controller
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    /** Constructor injection — Spring provides the OrderService automatically */
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates a new order from the request body.
     * Returns HTTP 201 (Created) with the newly created order details.
     */
    // @PostMapping maps HTTP POST requests to this method
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            // @Valid triggers validation of the request body (checks @NotBlank, @NotEmpty, etc.)
            // @RequestBody tells Spring to deserialize the JSON request body into a CreateOrderRequest
            @Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        // Return HTTP 201 Created status (instead of the default 200 OK) because a new resource was created
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    /**
     * Retrieves a single order by its UUID.
     */
    // @GetMapping("/{id}") maps GET requests like /api/orders/abc-123 to this method
    @GetMapping("/{id}")
    public OrderResponse getOrder(
            // @PathVariable extracts the {id} from the URL and converts it to a UUID
            @PathVariable UUID id) {
        return OrderResponse.from(orderService.getOrder(id));
    }

    /**
     * Lists all orders with pagination support.
     * Clients can control pagination with query params like ?page=0&size=20&sort=createdAt,desc
     */
    @GetMapping
    public Page<OrderResponse> listOrders(
            // Pageable is automatically populated by Spring from query parameters (page, size, sort)
            Pageable pageable) {
        // .map() converts each Order entity to an OrderResponse DTO
        return orderService.listOrders(pageable).map(OrderResponse::from);
    }

    /**
     * Cancels an order by its ID.
     * Uses PUT because it's updating the order's status.
     */
    @PutMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable UUID id) {
        return OrderResponse.from(orderService.cancelOrder(id));
    }

    /**
     * Exception handler for when an order is not found.
     * Returns HTTP 404 (Not Found) with the error message.
     */
    // @ExceptionHandler tells Spring to call this method whenever the specified exception
    // is thrown by any method in this controller
    @ExceptionHandler(OrderService.OrderNotFoundException.class)
    public ResponseEntity<String> handleNotFound(OrderService.OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    /**
     * Exception handler for insufficient stock errors.
     * Returns HTTP 422 (Unprocessable Entity) because the request was valid
     * but couldn't be processed due to business rules (not enough stock).
     */
    @ExceptionHandler(OrderService.InsufficientStockException.class)
    public ResponseEntity<String> handleInsufficientStock(OrderService.InsufficientStockException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ex.getMessage());
    }

    /**
     * Exception handler for illegal state transitions (e.g., cancelling a shipped order).
     * Returns HTTP 409 (Conflict) to indicate the request conflicts with the current state.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }
}
