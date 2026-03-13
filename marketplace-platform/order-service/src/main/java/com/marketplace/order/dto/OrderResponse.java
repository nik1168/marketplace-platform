package com.marketplace.order.dto;

import com.marketplace.order.model.Order;
import com.marketplace.order.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing the response sent back to the client when they request order details.
 * This keeps the API response separate from the internal Order entity, so we only expose
 * the fields we want clients to see (and in the format we want).
 */
public record OrderResponse(
        UUID id,
        String customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        List<ItemResponse> items,
        Instant createdAt
) {
    /**
     * Nested record representing a single item in the order response.
     * Includes the calculated subtotal so the client doesn't have to compute it.
     */
    public record ItemResponse(String productId, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {}

    /**
     * Factory method that converts an Order entity into an OrderResponse DTO.
     * This pattern keeps the conversion logic in one place and makes it easy
     * to create responses from entities throughout the codebase.
     */
    public static OrderResponse from(Order order) {
        // Convert each OrderItem entity to an ItemResponse DTO
        List<ItemResponse> items = order.getItems().stream()
                .map(i -> new ItemResponse(i.getProductId(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal()))
                .toList();
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getStatus(),
                order.getTotalAmount(), items, order.getCreatedAt());
    }
}
