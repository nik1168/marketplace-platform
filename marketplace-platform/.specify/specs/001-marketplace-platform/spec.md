# Marketplace Platform — Specification

## Overview

An online marketplace where customers can browse products, place orders, and track their status, while administrators can manage inventory and monitor operations through a dashboard.

## Users

### Customers
People who want to buy products from the marketplace.

### Administrators
Internal staff who manage product inventory and monitor order activity.

## User Stories

### Customer: Browse Products
As a customer, I want to see all available products with their names, descriptions, prices, and current stock levels, so I can decide what to buy.

- Products should display available stock (total stock minus any reserved units)
- Out-of-stock products should still be visible but clearly marked as unavailable
- The product list should support pagination for large catalogs

### Customer: Place an Order
As a customer, I want to place an order for one or more products, so I can purchase them.

- Before accepting an order, the system must verify that enough stock exists for every item
- If any product has insufficient stock, the entire order must be rejected with a clear error message
- The system must prevent overselling — if two customers order the last units simultaneously, only one should succeed
- After placing an order, the customer receives an order ID and can track its status
- The order starts as PENDING and is confirmed asynchronously once stock is successfully reserved

### Customer: Track Order Status
As a customer, I want to check the status of my order, so I know whether it has been confirmed, rejected, or cancelled.

- Order statuses: PENDING → CONFIRMED or REJECTED
- CONFIRMED orders can be CANCELLED by the customer
- Each status transition should happen automatically based on inventory processing

### Customer: Cancel an Order
As a customer, I want to cancel a pending or confirmed order, so I can change my mind.

- Only PENDING or CONFIRMED orders can be cancelled
- When an order is cancelled, any reserved stock must be released back to inventory

### Admin: View All Orders
As an administrator, I want to see all orders across all customers, so I can monitor marketplace activity.

- Orders should display customer ID, items, total amount, status, and timestamps
- The list should support pagination and sorting by date

### Admin: Manage Products
As an administrator, I want to add new products and update stock levels, so I can keep the inventory current.

- Admins can create products with name, description, price, and initial stock
- Admins can update stock quantities for existing products
- Stock changes should be reflected immediately for new customer orders

### Admin: Monitor System Health
As an administrator, I want to see key metrics about the system, so I can identify issues quickly.

- Track: total orders created, total orders rejected, order processing time
- Expose health check endpoints for infrastructure monitoring

## Business Rules

1. **No overselling** — The system must never confirm more units than physically available. Concurrent orders must be handled safely.
2. **Atomic stock reservation** — Stock check and reservation should minimize the window for race conditions. If a conflict occurs, the order should be rejected rather than overselling.
3. **Asynchronous confirmation** — Order confirmation does not need to be instant. A short delay (seconds) is acceptable in exchange for system reliability.
4. **Stock release on cancellation** — Cancelled orders must always release their reserved stock. The system must not leak reserved units.
5. **Idempotent operations** — Processing the same event twice must not cause incorrect state (e.g., double-reserving stock).

## Out of Scope

- User authentication and authorization
- Payment processing
- Shipping and delivery tracking
- Product categories and search
- Email/SMS notifications
- Multi-currency support
