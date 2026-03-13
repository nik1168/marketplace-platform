package com.marketplace.order.service;

import com.marketplace.order.dto.CreateOrderRequest;
import com.marketplace.order.dto.OrderItemRequest;
import com.marketplace.order.grpc.InventoryGrpcClient;
import com.marketplace.order.kafka.OrderEventProducer;
import com.marketplace.order.model.Order;
import com.marketplace.order.model.OrderStatus;
import com.marketplace.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryGrpcClient inventoryClient;

    @Mock
    private OrderEventProducer eventProducer;

    @InjectMocks
    private OrderService orderService;

    @Test
    void shouldCreateOrderWhenStockAvailable() {
        when(inventoryClient.checkStock("prod-1", 2)).thenReturn(true);
        when(inventoryClient.reserveStock(eq("prod-1"), eq(2), anyString())).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            Field idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, UUID.randomUUID());
            return order;
        });

        var request = new CreateOrderRequest("cust-1", List.of(
                new OrderItemRequest("prod-1", 2, new BigDecimal("29.99"))
        ));

        Order result = orderService.createOrder(request);

        assertThat(result.getCustomerId()).isEqualTo("cust-1");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    void shouldRejectOrderWhenStockUnavailable() {
        when(inventoryClient.checkStock("prod-1", 2)).thenReturn(false);

        var request = new CreateOrderRequest("cust-1", List.of(
                new OrderItemRequest("prod-1", 2, new BigDecimal("29.99"))
        ));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderService.InsufficientStockException.class);
    }
}
