package com.marketplace.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * DTO representing a single item in a create-order request.
 * Each item specifies which product to order, how many, and at what price.
 *
 * Validation annotations ensure the client sends valid data before it reaches the service layer.
 */
public record OrderItemRequest(
        // @NotBlank ensures productId is provided and not empty
        @NotBlank String productId,

        // @Min(1) ensures at least 1 unit is ordered — zero or negative quantities make no sense
        @Min(1) int quantity,

        // @Positive ensures the price is greater than zero
        @Positive BigDecimal unitPrice
) {}
