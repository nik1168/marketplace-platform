package com.marketplace.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Data Transfer Object (DTO) representing the request body for creating a new order.
 * DTOs are used to separate the API contract from the internal database model,
 * so we can control exactly what data the client needs to send.
 *
 * This is a Java "record" — a compact way to define an immutable data class.
 * The compiler automatically generates the constructor, getters, equals(), hashCode(), and toString().
 */
public record CreateOrderRequest(
        // @NotBlank ensures the customerId is not null, not empty, and not just whitespace
        @NotBlank String customerId,

        // @NotEmpty ensures the items list is not null and has at least one element
        // @Valid tells Spring to also validate each OrderItemRequest inside the list
        @NotEmpty @Valid List<OrderItemRequest> items
) {}
