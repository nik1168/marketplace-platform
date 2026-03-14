# Marketplace Frontend — Design Document

## Purpose

A React + TypeScript frontend that serves as both a realistic e-commerce storefront and an admin monitoring dashboard, allowing full end-to-end testing and demo of the marketplace backend (Order Service + Inventory Service).

## Tech Stack

- React 18 + TypeScript + Vite
- Material UI (MUI) for components and styling
- React Router for navigation
- Polling (3-5s intervals) for real-time status updates

## Architecture

Two views accessible via sidebar navigation:

### Storefront View

Three-step flow on a single page:

1. **Product Catalog** — Grid of product cards fetched from `GET /api/products` (new Inventory Service endpoint). Add to cart with quantity selector. Out-of-stock products disabled.
2. **Cart & Checkout** — Side panel with cart items, totals, customer ID input, "Place Order" button calling `POST /api/orders`.
3. **Order Confirmation** — Shows order with status badge, polls `GET /api/orders/{id}` every 3s to show live status transition (PENDING → CONFIRMED/REJECTED).

### Admin Dashboard View

Three panels:

1. **Orders Table** — All orders with status chips (color-coded), click to expand detail. Auto-refreshes every 5s.
2. **Inventory Table** — All products with stock levels, low stock highlighted. Auto-refreshes every 5s.
3. **Order Detail** — Full order info, status timeline/stepper, "Cancel Order" button for PENDING/CONFIRMED orders.

## Project Structure

```
marketplace-frontend/
├── src/
│   ├── pages/
│   │   ├── Storefront.tsx
│   │   └── AdminDashboard.tsx
│   ├── components/
│   │   ├── storefront/
│   │   │   ├── ProductCatalog.tsx
│   │   │   ├── Cart.tsx
│   │   │   ├── CheckoutForm.tsx
│   │   │   └── OrderConfirmation.tsx
│   │   └── admin/
│   │       ├── OrdersTable.tsx
│   │       ├── InventoryTable.tsx
│   │       └── OrderDetail.tsx
│   ├── api/
│   │   └── client.ts
│   ├── types/
│   │   └── index.ts
│   ├── App.tsx
│   └── main.tsx
├── tsconfig.json
├── package.json
└── vite.config.ts
```

## Backend Changes Required

1. **New endpoint:** `GET /api/products` and `GET /api/products/{id}` in Inventory Service (ProductController)
2. **CORS config:** Allow `http://localhost:5173` in both Order Service and Inventory Service (WebConfig class)

## Status Color Mapping

- PENDING → orange
- CONFIRMED → green
- REJECTED → red
- CANCELLED → grey
- SHIPPED → blue
