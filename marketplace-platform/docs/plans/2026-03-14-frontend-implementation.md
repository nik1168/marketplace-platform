# Marketplace Frontend Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a React + TypeScript frontend with a storefront view (product catalog, cart, checkout) and admin dashboard (orders table, inventory table, order detail) that exercises all backend features.

**Architecture:** Single-page app with React Router for two views (Storefront, Admin Dashboard). API client talks to Order Service (port 8080) and Inventory Service (port 8081). Polling for real-time status updates.

**Tech Stack:** React 18, TypeScript, Vite, Material UI (MUI), React Router, Axios

**Design doc:** `docs/plans/2026-03-14-frontend-design.md`

---

### Task 1: Backend Changes — Product REST Endpoint + CORS

**Files:**
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/controller/ProductController.java`
- Create: `marketplace-platform/inventory-service/src/main/java/com/marketplace/inventory/config/WebConfig.java`
- Create: `marketplace-platform/order-service/src/main/java/com/marketplace/order/config/WebConfig.java`

**Step 1: Create ProductController in Inventory Service**

```java
package com.marketplace.inventory.controller;

import com.marketplace.inventory.model.Product;
import com.marketplace.inventory.repository.ProductRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping
    public List<Product> listProducts() {
        return productRepository.findAll();
    }

    @GetMapping("/{productId}")
    public Product getProduct(@PathVariable String productId) {
        return productRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
    }
}
```

**Step 2: Create CORS config for Inventory Service**

```java
package com.marketplace.inventory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
```

**Step 3: Create CORS config for Order Service**

Same class as above but in `com.marketplace.order.config` package.

```java
package com.marketplace.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
```

**Step 4: Verify both services compile**

Run: `cd marketplace-platform && export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && mvn clean compile`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add marketplace-platform/inventory-service/ marketplace-platform/order-service/
git commit -m "feat: add Product REST endpoint and CORS config for frontend

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 2: Scaffold React + TypeScript + Vite Project

**Step 1: Create Vite project**

```bash
cd marketplace-platform
npm create vite@latest marketplace-frontend -- --template react-ts
cd marketplace-frontend
npm install
```

**Step 2: Install dependencies**

```bash
npm install @mui/material @mui/icons-material @emotion/react @emotion/styled
npm install react-router-dom
npm install axios
```

**Step 3: Verify dev server starts**

Run: `npm run dev`
Expected: Vite dev server starts on http://localhost:5173

**Step 4: Commit**

```bash
git add marketplace-platform/marketplace-frontend/
git commit -m "feat(frontend): scaffold React + TypeScript + Vite project with MUI and dependencies

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 3: TypeScript Types + API Client

**Files:**
- Create: `marketplace-platform/marketplace-frontend/src/types/index.ts`
- Create: `marketplace-platform/marketplace-frontend/src/api/client.ts`

**Step 1: Create shared TypeScript types**

```typescript
// src/types/index.ts

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'SHIPPED' | 'CANCELLED';

export interface OrderItem {
  productId: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface Order {
  id: string;
  customerId: string;
  status: OrderStatus;
  totalAmount: number;
  items: OrderItem[];
  createdAt: string;
}

export interface Product {
  id: string;
  productId: string;
  name: string;
  sku: string;
  category: string;
  currentStock: number;
  reservedStock: number;
  lastUpdated: string;
  version: number;
}

export interface CreateOrderRequest {
  customerId: string;
  items: {
    productId: string;
    quantity: number;
    unitPrice: number;
  }[];
}

export interface CartItem {
  product: Product;
  quantity: number;
}
```

**Step 2: Create API client**

```typescript
// src/api/client.ts

import axios from 'axios';
import { Order, Product, CreateOrderRequest } from '../types';

const orderApi = axios.create({
  baseURL: 'http://localhost:8080/api',
});

const inventoryApi = axios.create({
  baseURL: 'http://localhost:8081/api',
});

// Order Service endpoints
export const createOrder = async (request: CreateOrderRequest): Promise<Order> => {
  const response = await orderApi.post<Order>('/orders', request);
  return response.data;
};

export const getOrder = async (orderId: string): Promise<Order> => {
  const response = await orderApi.get<Order>(`/orders/${orderId}`);
  return response.data;
};

export const listOrders = async (page = 0, size = 20): Promise<{ content: Order[]; totalElements: number }> => {
  const response = await orderApi.get('/orders', { params: { page, size, sort: 'createdAt,desc' } });
  return response.data;
};

export const cancelOrder = async (orderId: string): Promise<Order> => {
  const response = await orderApi.put<Order>(`/orders/${orderId}/cancel`);
  return response.data;
};

// Inventory Service endpoints
export const listProducts = async (): Promise<Product[]> => {
  const response = await inventoryApi.get<Product[]>('/products');
  return response.data;
};

export const getProduct = async (productId: string): Promise<Product> => {
  const response = await inventoryApi.get<Product>(`/products/${productId}`);
  return response.data;
};
```

**Step 3: Verify compilation**

Run: `cd marketplace-platform/marketplace-frontend && npm run build`
Expected: Build succeeds

**Step 4: Commit**

```bash
git add marketplace-platform/marketplace-frontend/src/types/ marketplace-platform/marketplace-frontend/src/api/
git commit -m "feat(frontend): add TypeScript types and API client for both services

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 4: App Layout — Sidebar Navigation + Router

**Files:**
- Create: `marketplace-platform/marketplace-frontend/src/pages/Storefront.tsx`
- Create: `marketplace-platform/marketplace-frontend/src/pages/AdminDashboard.tsx`
- Modify: `marketplace-platform/marketplace-frontend/src/App.tsx`
- Modify: `marketplace-platform/marketplace-frontend/src/main.tsx`

**Step 1: Create placeholder pages**

```typescript
// src/pages/Storefront.tsx
import { Typography, Box } from '@mui/material';

export default function Storefront() {
  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4">Storefront</Typography>
    </Box>
  );
}
```

```typescript
// src/pages/AdminDashboard.tsx
import { Typography, Box } from '@mui/material';

export default function AdminDashboard() {
  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4">Admin Dashboard</Typography>
    </Box>
  );
}
```

**Step 2: Create App with sidebar navigation**

```typescript
// src/App.tsx
import { BrowserRouter, Routes, Route, Link, useLocation } from 'react-router-dom';
import {
  Box, Drawer, List, ListItemButton, ListItemIcon, ListItemText,
  Toolbar, AppBar, Typography, CssBaseline, ThemeProvider, createTheme,
} from '@mui/material';
import StorefrontIcon from '@mui/icons-material/Storefront';
import DashboardIcon from '@mui/icons-material/Dashboard';
import Storefront from './pages/Storefront';
import AdminDashboard from './pages/AdminDashboard';

const drawerWidth = 240;

const theme = createTheme({
  palette: {
    primary: { main: '#1976d2' },
    secondary: { main: '#dc004e' },
  },
});

function NavContent() {
  const location = useLocation();

  const navItems = [
    { text: 'Storefront', icon: <StorefrontIcon />, path: '/' },
    { text: 'Admin Dashboard', icon: <DashboardIcon />, path: '/admin' },
  ];

  return (
    <List>
      {navItems.map((item) => (
        <ListItemButton
          key={item.text}
          component={Link}
          to={item.path}
          selected={location.pathname === item.path}
        >
          <ListItemIcon>{item.icon}</ListItemIcon>
          <ListItemText primary={item.text} />
        </ListItemButton>
      ))}
    </List>
  );
}

export default function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <Box sx={{ display: 'flex' }}>
          <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
            <Toolbar>
              <Typography variant="h6" noWrap>
                Marketplace Platform
              </Typography>
            </Toolbar>
          </AppBar>

          <Drawer
            variant="permanent"
            sx={{
              width: drawerWidth,
              '& .MuiDrawer-paper': { width: drawerWidth, boxSizing: 'border-box' },
            }}
          >
            <Toolbar />
            <NavContent />
          </Drawer>

          <Box component="main" sx={{ flexGrow: 1, p: 3 }}>
            <Toolbar />
            <Routes>
              <Route path="/" element={<Storefront />} />
              <Route path="/admin" element={<AdminDashboard />} />
            </Routes>
          </Box>
        </Box>
      </BrowserRouter>
    </ThemeProvider>
  );
}
```

**Step 3: Update main.tsx**

```typescript
// src/main.tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
```

**Step 4: Clean up default Vite files**

Delete `src/App.css`, `src/index.css`, `src/assets/` if they exist. Remove any CSS imports from files.

**Step 5: Verify**

Run: `cd marketplace-platform/marketplace-frontend && npm run build`
Expected: Build succeeds

**Step 6: Commit**

```bash
git add marketplace-platform/marketplace-frontend/
git commit -m "feat(frontend): add sidebar navigation with Storefront and Admin Dashboard routes

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 5: Storefront — Product Catalog Component

**Files:**
- Create: `marketplace-platform/marketplace-frontend/src/components/storefront/ProductCatalog.tsx`

**Step 1: Create ProductCatalog component**

```typescript
// src/components/storefront/ProductCatalog.tsx
import { useEffect, useState } from 'react';
import {
  Grid, Card, CardContent, CardActions, Typography, Button,
  Chip, TextField, Box, Alert, CircularProgress,
} from '@mui/material';
import AddShoppingCartIcon from '@mui/icons-material/AddShoppingCart';
import { listProducts } from '../../api/client';
import { Product, CartItem } from '../../types';

interface ProductCatalogProps {
  cart: CartItem[];
  onAddToCart: (product: Product, quantity: number) => void;
}

export default function ProductCatalog({ cart, onAddToCart }: ProductCatalogProps) {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [quantities, setQuantities] = useState<Record<string, number>>({});

  useEffect(() => {
    loadProducts();
  }, []);

  const loadProducts = async () => {
    try {
      setLoading(true);
      const data = await listProducts();
      setProducts(data);
      setError(null);
    } catch (err) {
      setError('Failed to load products. Is the Inventory Service running on port 8081?');
    } finally {
      setLoading(false);
    }
  };

  const getQuantity = (productId: string) => quantities[productId] || 1;

  const setQuantity = (productId: string, qty: number) => {
    setQuantities((prev) => ({ ...prev, [productId]: Math.max(1, qty) }));
  };

  const getCartQuantity = (productId: string) => {
    const item = cart.find((c) => c.product.productId === productId);
    return item ? item.quantity : 0;
  };

  if (loading) return <CircularProgress sx={{ m: 4 }} />;
  if (error) return <Alert severity="error" sx={{ m: 2 }}>{error}</Alert>;

  return (
    <Grid container spacing={3}>
      {products.map((product) => {
        const availableStock = product.currentStock - product.reservedStock;
        const inCart = getCartQuantity(product.productId);
        const isOutOfStock = availableStock <= 0;

        return (
          <Grid item xs={12} sm={6} md={4} key={product.productId}>
            <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column', opacity: isOutOfStock ? 0.6 : 1 }}>
              <CardContent sx={{ flexGrow: 1 }}>
                <Typography variant="h6" gutterBottom>{product.name}</Typography>
                <Chip label={product.category} size="small" sx={{ mb: 1 }} />
                <Typography variant="body2" color="text.secondary">SKU: {product.sku}</Typography>
                <Typography variant="h5" color="primary" sx={{ mt: 1 }}>
                  ${(29.99).toFixed(2)}
                </Typography>
                <Box sx={{ mt: 1 }}>
                  <Chip
                    label={isOutOfStock ? 'Out of Stock' : `${availableStock} in stock`}
                    color={isOutOfStock ? 'error' : availableStock < 20 ? 'warning' : 'success'}
                    size="small"
                  />
                  {inCart > 0 && (
                    <Chip label={`${inCart} in cart`} color="info" size="small" sx={{ ml: 1 }} />
                  )}
                </Box>
              </CardContent>
              <CardActions sx={{ p: 2, pt: 0 }}>
                <TextField
                  type="number"
                  size="small"
                  label="Qty"
                  value={getQuantity(product.productId)}
                  onChange={(e) => setQuantity(product.productId, parseInt(e.target.value) || 1)}
                  sx={{ width: 80, mr: 1 }}
                  inputProps={{ min: 1, max: availableStock }}
                  disabled={isOutOfStock}
                />
                <Button
                  variant="contained"
                  startIcon={<AddShoppingCartIcon />}
                  onClick={() => onAddToCart(product, getQuantity(product.productId))}
                  disabled={isOutOfStock}
                  size="small"
                >
                  Add to Cart
                </Button>
              </CardActions>
            </Card>
          </Grid>
        );
      })}
    </Grid>
  );
}
```

**Step 2: Verify compilation**

Run: `cd marketplace-platform/marketplace-frontend && npm run build`
Expected: Build succeeds

**Step 3: Commit**

```bash
git add marketplace-platform/marketplace-frontend/
git commit -m "feat(frontend): add ProductCatalog component with stock display and add-to-cart

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 6: Storefront — Cart Component

**Files:**
- Create: `marketplace-platform/marketplace-frontend/src/components/storefront/Cart.tsx`

**Step 1: Create Cart component**

```typescript
// src/components/storefront/Cart.tsx
import {
  Drawer, Box, Typography, List, ListItem, ListItemText,
  IconButton, Divider, Button, Badge, Fab, Chip,
} from '@mui/material';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import { useState } from 'react';
import { CartItem } from '../../types';

interface CartProps {
  cart: CartItem[];
  onUpdateQuantity: (productId: string, quantity: number) => void;
  onRemoveItem: (productId: string) => void;
  onCheckout: () => void;
}

export default function Cart({ cart, onUpdateQuantity, onRemoveItem, onCheckout }: CartProps) {
  const [open, setOpen] = useState(false);

  const totalItems = cart.reduce((sum, item) => sum + item.quantity, 0);
  const totalAmount = cart.reduce((sum, item) => sum + item.quantity * 29.99, 0);

  return (
    <>
      <Fab
        color="primary"
        sx={{ position: 'fixed', bottom: 24, right: 24 }}
        onClick={() => setOpen(true)}
      >
        <Badge badgeContent={totalItems} color="error">
          <ShoppingCartIcon />
        </Badge>
      </Fab>

      <Drawer anchor="right" open={open} onClose={() => setOpen(false)}>
        <Box sx={{ width: 380, p: 2 }}>
          <Typography variant="h6" gutterBottom>Shopping Cart</Typography>
          <Divider />

          {cart.length === 0 ? (
            <Typography sx={{ mt: 2 }} color="text.secondary">Cart is empty</Typography>
          ) : (
            <>
              <List>
                {cart.map((item) => (
                  <ListItem
                    key={item.product.productId}
                    secondaryAction={
                      <IconButton edge="end" onClick={() => onRemoveItem(item.product.productId)}>
                        <DeleteIcon />
                      </IconButton>
                    }
                  >
                    <ListItemText
                      primary={item.product.name}
                      secondary={
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.5 }}>
                          <IconButton
                            size="small"
                            onClick={() => onUpdateQuantity(item.product.productId, item.quantity - 1)}
                            disabled={item.quantity <= 1}
                          >
                            <RemoveIcon fontSize="small" />
                          </IconButton>
                          <Chip label={item.quantity} size="small" />
                          <IconButton
                            size="small"
                            onClick={() => onUpdateQuantity(item.product.productId, item.quantity + 1)}
                          >
                            <AddIcon fontSize="small" />
                          </IconButton>
                          <Typography variant="body2" sx={{ ml: 1 }}>
                            ${(item.quantity * 29.99).toFixed(2)}
                          </Typography>
                        </Box>
                      }
                    />
                  </ListItem>
                ))}
              </List>

              <Divider />
              <Box sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Typography variant="h6">Total: ${totalAmount.toFixed(2)}</Typography>
                <Button variant="contained" color="primary" onClick={onCheckout} size="large">
                  Checkout
                </Button>
              </Box>
            </>
          )}
        </Box>
      </Drawer>
    </>
  );
}
```

**Step 2: Verify compilation**

Run: `cd marketplace-platform/marketplace-frontend && npm run build`

**Step 3: Commit**

```bash
git add marketplace-platform/marketplace-frontend/
git commit -m "feat(frontend): add Cart drawer component with quantity controls

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 7: Storefront — Checkout Form + Order Confirmation

**Files:**
- Create: `marketplace-platform/marketplace-frontend/src/components/storefront/CheckoutForm.tsx`
- Create: `marketplace-platform/marketplace-frontend/src/components/storefront/OrderConfirmation.tsx`

**Step 1: Create CheckoutForm**

```typescript
// src/components/storefront/CheckoutForm.tsx
import { useState } from 'react';
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  TextField, Button, Typography, List, ListItem, ListItemText,
  Divider, Alert, CircularProgress, Box,
} from '@mui/material';
import { CartItem, Order } from '../../types';
import { createOrder } from '../../api/client';

interface CheckoutFormProps {
  open: boolean;
  cart: CartItem[];
  onClose: () => void;
  onOrderPlaced: (order: Order) => void;
}

export default function CheckoutForm({ open, cart, onClose, onOrderPlaced }: CheckoutFormProps) {
  const [customerId, setCustomerId] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const totalAmount = cart.reduce((sum, item) => sum + item.quantity * 29.99, 0);

  const handleSubmit = async () => {
    if (!customerId.trim()) {
      setError('Please enter a customer ID');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const order = await createOrder({
        customerId: customerId.trim(),
        items: cart.map((item) => ({
          productId: item.product.productId,
          quantity: item.quantity,
          unitPrice: 29.99,
        })),
      });
      onOrderPlaced(order);
    } catch (err: any) {
      setError(err.response?.data || 'Failed to place order. Is the Order Service running?');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Checkout</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

        <TextField
          autoFocus
          margin="dense"
          label="Customer ID"
          placeholder="e.g., customer-1"
          fullWidth
          value={customerId}
          onChange={(e) => setCustomerId(e.target.value)}
          sx={{ mb: 2 }}
        />

        <Typography variant="subtitle1" gutterBottom>Order Summary</Typography>
        <List dense>
          {cart.map((item) => (
            <ListItem key={item.product.productId}>
              <ListItemText
                primary={item.product.name}
                secondary={`Qty: ${item.quantity} × $29.99 = $${(item.quantity * 29.99).toFixed(2)}`}
              />
            </ListItem>
          ))}
        </List>
        <Divider />
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 1 }}>
          <Typography variant="h6">Total: ${totalAmount.toFixed(2)}</Typography>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={loading}>Cancel</Button>
        <Button onClick={handleSubmit} variant="contained" disabled={loading}>
          {loading ? <CircularProgress size={24} /> : 'Place Order'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
```

**Step 2: Create OrderConfirmation with status polling**

```typescript
// src/components/storefront/OrderConfirmation.tsx
import { useEffect, useState } from 'react';
import {
  Box, Typography, Chip, Paper, Stepper, Step, StepLabel,
  Button, List, ListItem, ListItemText, Alert, CircularProgress,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import { Order, OrderStatus } from '../../types';
import { getOrder } from '../../api/client';

interface OrderConfirmationProps {
  order: Order;
  onNewOrder: () => void;
}

const STATUS_COLORS: Record<OrderStatus, 'warning' | 'success' | 'error' | 'default' | 'info'> = {
  PENDING: 'warning',
  CONFIRMED: 'success',
  REJECTED: 'error',
  CANCELLED: 'default',
  SHIPPED: 'info',
};

const STATUS_STEPS = ['PENDING', 'CONFIRMED', 'SHIPPED'];

export default function OrderConfirmation({ order: initialOrder, onNewOrder }: OrderConfirmationProps) {
  const [order, setOrder] = useState<Order>(initialOrder);
  const [polling, setPolling] = useState(true);

  useEffect(() => {
    if (!polling) return;

    const interval = setInterval(async () => {
      try {
        const updated = await getOrder(order.id);
        setOrder(updated);
        // Stop polling once we reach a terminal state
        if (['CONFIRMED', 'REJECTED', 'CANCELLED', 'SHIPPED'].includes(updated.status)) {
          setPolling(false);
        }
      } catch (err) {
        // Keep polling on error
      }
    }, 3000);

    return () => clearInterval(interval);
  }, [order.id, polling]);

  const activeStep = STATUS_STEPS.indexOf(order.status);

  return (
    <Paper sx={{ p: 4, maxWidth: 600, mx: 'auto' }}>
      {order.status === 'CONFIRMED' && (
        <Alert icon={<CheckCircleIcon />} severity="success" sx={{ mb: 2 }}>
          Order confirmed! Stock has been reserved.
        </Alert>
      )}
      {order.status === 'REJECTED' && (
        <Alert icon={<ErrorIcon />} severity="error" sx={{ mb: 2 }}>
          Order rejected — insufficient stock.
        </Alert>
      )}

      <Typography variant="h5" gutterBottom>Order Placed!</Typography>
      <Typography variant="body2" color="text.secondary" gutterBottom>
        Order ID: {order.id}
      </Typography>

      <Box sx={{ my: 3 }}>
        <Chip
          label={order.status}
          color={STATUS_COLORS[order.status]}
          sx={{ fontSize: '1rem', py: 2, px: 1 }}
        />
        {polling && (
          <CircularProgress size={16} sx={{ ml: 2 }} />
        )}
        {polling && (
          <Typography variant="caption" sx={{ ml: 1 }}>Waiting for confirmation...</Typography>
        )}
      </Box>

      {order.status !== 'REJECTED' && (
        <Stepper activeStep={activeStep >= 0 ? activeStep : 0} sx={{ mb: 3 }}>
          {STATUS_STEPS.map((label) => (
            <Step key={label}>
              <StepLabel>{label}</StepLabel>
            </Step>
          ))}
        </Stepper>
      )}

      <Typography variant="subtitle1" gutterBottom>Items</Typography>
      <List dense>
        {order.items.map((item, i) => (
          <ListItem key={i}>
            <ListItemText
              primary={item.productId}
              secondary={`Qty: ${item.quantity} × $${item.unitPrice.toFixed(2)} = $${item.subtotal.toFixed(2)}`}
            />
          </ListItem>
        ))}
      </List>

      <Typography variant="h6" sx={{ mt: 1 }}>Total: ${order.totalAmount.toFixed(2)}</Typography>

      <Button variant="outlined" onClick={onNewOrder} sx={{ mt: 3 }} fullWidth>
        Place Another Order
      </Button>
    </Paper>
  );
}
```

**Step 3: Verify compilation**

Run: `cd marketplace-platform/marketplace-frontend && npm run build`

**Step 4: Commit**

```bash
git add marketplace-platform/marketplace-frontend/
git commit -m "feat(frontend): add CheckoutForm dialog and OrderConfirmation with status polling

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 8: Storefront — Wire Up the Page

**Files:**
- Modify: `marketplace-platform/marketplace-frontend/src/pages/Storefront.tsx`

**Step 1: Implement Storefront page with state management**

```typescript
// src/pages/Storefront.tsx
import { useState } from 'react';
import { Box, Typography } from '@mui/material';
import ProductCatalog from '../components/storefront/ProductCatalog';
import Cart from '../components/storefront/Cart';
import CheckoutForm from '../components/storefront/CheckoutForm';
import OrderConfirmation from '../components/storefront/OrderConfirmation';
import { CartItem, Product, Order } from '../types';

type StorefrontView = 'catalog' | 'confirmation';

export default function Storefront() {
  const [cart, setCart] = useState<CartItem[]>([]);
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const [view, setView] = useState<StorefrontView>('catalog');
  const [placedOrder, setPlacedOrder] = useState<Order | null>(null);

  const handleAddToCart = (product: Product, quantity: number) => {
    setCart((prev) => {
      const existing = prev.find((item) => item.product.productId === product.productId);
      if (existing) {
        return prev.map((item) =>
          item.product.productId === product.productId
            ? { ...item, quantity: item.quantity + quantity }
            : item
        );
      }
      return [...prev, { product, quantity }];
    });
  };

  const handleUpdateQuantity = (productId: string, quantity: number) => {
    if (quantity <= 0) return;
    setCart((prev) =>
      prev.map((item) =>
        item.product.productId === productId ? { ...item, quantity } : item
      )
    );
  };

  const handleRemoveItem = (productId: string) => {
    setCart((prev) => prev.filter((item) => item.product.productId !== productId));
  };

  const handleCheckout = () => {
    setCheckoutOpen(true);
  };

  const handleOrderPlaced = (order: Order) => {
    setCheckoutOpen(false);
    setPlacedOrder(order);
    setView('confirmation');
    setCart([]);
  };

  const handleNewOrder = () => {
    setPlacedOrder(null);
    setView('catalog');
  };

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        {view === 'catalog' ? 'Product Catalog' : 'Order Confirmation'}
      </Typography>

      {view === 'catalog' && (
        <>
          <ProductCatalog cart={cart} onAddToCart={handleAddToCart} />
          <Cart
            cart={cart}
            onUpdateQuantity={handleUpdateQuantity}
            onRemoveItem={handleRemoveItem}
            onCheckout={handleCheckout}
          />
          <CheckoutForm
            open={checkoutOpen}
            cart={cart}
            onClose={() => setCheckoutOpen(false)}
            onOrderPlaced={handleOrderPlaced}
          />
        </>
      )}

      {view === 'confirmation' && placedOrder && (
        <OrderConfirmation order={placedOrder} onNewOrder={handleNewOrder} />
      )}
    </Box>
  );
}
```

**Step 2: Verify**

Run: `cd marketplace-platform/marketplace-frontend && npm run build`

**Step 3: Commit**

```bash
git add marketplace-platform/marketplace-frontend/
git commit -m "feat(frontend): wire up Storefront page with cart state management

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 9: Admin Dashboard — Orders Table

**Files:**
- Create: `marketplace-platform/marketplace-frontend/src/components/admin/OrdersTable.tsx`

**Step 1: Create OrdersTable component**

```typescript
// src/components/admin/OrdersTable.tsx
import { useEffect, useState } from 'react';
import {
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Paper, Chip, Typography, IconButton, Box, CircularProgress, Alert,
  Tooltip,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { listOrders } from '../../api/client';
import { Order, OrderStatus } from '../../types';

const STATUS_COLORS: Record<OrderStatus, 'warning' | 'success' | 'error' | 'default' | 'info'> = {
  PENDING: 'warning',
  CONFIRMED: 'success',
  REJECTED: 'error',
  CANCELLED: 'default',
  SHIPPED: 'info',
};

interface OrdersTableProps {
  onSelectOrder: (order: Order) => void;
  selectedOrderId?: string;
}

export default function OrdersTable({ onSelectOrder, selectedOrderId }: OrdersTableProps) {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadOrders = async () => {
    try {
      const data = await listOrders();
      setOrders(data.content);
      setError(null);
    } catch (err) {
      setError('Failed to load orders. Is the Order Service running on port 8080?');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadOrders();
    const interval = setInterval(loadOrders, 5000);
    return () => clearInterval(interval);
  }, []);

  if (loading) return <CircularProgress sx={{ m: 2 }} />;
  if (error) return <Alert severity="error">{error}</Alert>;

  return (
    <Paper sx={{ p: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
        <Typography variant="h6">Orders</Typography>
        <Tooltip title="Refresh">
          <IconButton onClick={loadOrders}><RefreshIcon /></IconButton>
        </Tooltip>
      </Box>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Order ID</TableCell>
              <TableCell>Customer</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Items</TableCell>
              <TableCell align="right">Total</TableCell>
              <TableCell>Created</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {orders.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} align="center">No orders yet</TableCell>
              </TableRow>
            ) : (
              orders.map((order) => (
                <TableRow
                  key={order.id}
                  hover
                  selected={order.id === selectedOrderId}
                  onClick={() => onSelectOrder(order)}
                  sx={{ cursor: 'pointer' }}
                >
                  <TableCell>
                    <Typography variant="body2" fontFamily="monospace">
                      {order.id.substring(0, 8)}...
                    </Typography>
                  </TableCell>
                  <TableCell>{order.customerId}</TableCell>
                  <TableCell>
                    <Chip label={order.status} color={STATUS_COLORS[order.status]} size="small" />
                  </TableCell>
                  <TableCell>{order.items.length}</TableCell>
                  <TableCell align="right">${order.totalAmount.toFixed(2)}</TableCell>
                  <TableCell>{new Date(order.createdAt).toLocaleString()}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
  );
}
```

**Step 2: Verify**

Run: `cd marketplace-platform/marketplace-frontend && npm run build`

**Step 3: Commit**

```bash
git add marketplace-platform/marketplace-frontend/
git commit -m "feat(frontend): add OrdersTable component with auto-refresh and status chips

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 10: Admin Dashboard — Inventory Table

**Files:**
- Create: `marketplace-platform/marketplace-frontend/src/components/admin/InventoryTable.tsx`

**Step 1: Create InventoryTable component**

```typescript
// src/components/admin/InventoryTable.tsx
import { useEffect, useState } from 'react';
import {
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Paper, Typography, IconButton, Box, CircularProgress, Alert, Chip, Tooltip,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { listProducts } from '../../api/client';
import { Product } from '../../types';

export default function InventoryTable() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadProducts = async () => {
    try {
      const data = await listProducts();
      setProducts(data);
      setError(null);
    } catch (err) {
      setError('Failed to load inventory. Is the Inventory Service running on port 8081?');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadProducts();
    const interval = setInterval(loadProducts, 5000);
    return () => clearInterval(interval);
  }, []);

  if (loading) return <CircularProgress sx={{ m: 2 }} />;
  if (error) return <Alert severity="error">{error}</Alert>;

  return (
    <Paper sx={{ p: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
        <Typography variant="h6">Inventory</Typography>
        <Tooltip title="Refresh">
          <IconButton onClick={loadProducts}><RefreshIcon /></IconButton>
        </Tooltip>
      </Box>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Product</TableCell>
              <TableCell>SKU</TableCell>
              <TableCell>Category</TableCell>
              <TableCell align="right">Current Stock</TableCell>
              <TableCell align="right">Reserved</TableCell>
              <TableCell align="right">Available</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {products.map((product) => {
              const available = product.currentStock - product.reservedStock;
              return (
                <TableRow key={product.productId}>
                  <TableCell>{product.name}</TableCell>
                  <TableCell>
                    <Typography variant="body2" fontFamily="monospace">{product.sku}</Typography>
                  </TableCell>
                  <TableCell>
                    <Chip label={product.category} size="small" variant="outlined" />
                  </TableCell>
                  <TableCell align="right">{product.currentStock}</TableCell>
                  <TableCell align="right">
                    {product.reservedStock > 0 ? (
                      <Chip label={product.reservedStock} color="warning" size="small" />
                    ) : (
                      0
                    )}
                  </TableCell>
                  <TableCell align="right">
                    <Chip
                      label={available}
                      color={available <= 0 ? 'error' : available < 20 ? 'warning' : 'success'}
                      size="small"
                    />
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
  );
}
```

**Step 2: Verify**

Run: `cd marketplace-platform/marketplace-frontend && npm run build`

**Step 3: Commit**

```bash
git add marketplace-platform/marketplace-frontend/
git commit -m "feat(frontend): add InventoryTable component with stock level indicators

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 11: Admin Dashboard — Order Detail Panel

**Files:**
- Create: `marketplace-platform/marketplace-frontend/src/components/admin/OrderDetail.tsx`

**Step 1: Create OrderDetail component**

```typescript
// src/components/admin/OrderDetail.tsx
import { useEffect, useState } from 'react';
import {
  Paper, Typography, Box, Chip, Stepper, Step, StepLabel,
  List, ListItem, ListItemText, Divider, Button, Alert,
} from '@mui/material';
import CancelIcon from '@mui/icons-material/Cancel';
import { Order, OrderStatus } from '../../types';
import { getOrder, cancelOrder } from '../../api/client';

const STATUS_COLORS: Record<OrderStatus, 'warning' | 'success' | 'error' | 'default' | 'info'> = {
  PENDING: 'warning',
  CONFIRMED: 'success',
  REJECTED: 'error',
  CANCELLED: 'default',
  SHIPPED: 'info',
};

const STATUS_STEPS = ['PENDING', 'CONFIRMED', 'SHIPPED'];

interface OrderDetailProps {
  order: Order;
  onOrderUpdated: () => void;
}

export default function OrderDetail({ order: initialOrder, onOrderUpdated }: OrderDetailProps) {
  const [order, setOrder] = useState<Order>(initialOrder);
  const [cancelling, setCancelling] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setOrder(initialOrder);
  }, [initialOrder]);

  // Poll for updates
  useEffect(() => {
    const interval = setInterval(async () => {
      try {
        const updated = await getOrder(order.id);
        setOrder(updated);
      } catch {
        // ignore polling errors
      }
    }, 3000);
    return () => clearInterval(interval);
  }, [order.id]);

  const handleCancel = async () => {
    try {
      setCancelling(true);
      setError(null);
      const updated = await cancelOrder(order.id);
      setOrder(updated);
      onOrderUpdated();
    } catch (err: any) {
      setError(err.response?.data || 'Failed to cancel order');
    } finally {
      setCancelling(false);
    }
  };

  const canCancel = order.status === 'PENDING' || order.status === 'CONFIRMED';
  const activeStep = STATUS_STEPS.indexOf(order.status);

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="h6" gutterBottom>Order Detail</Typography>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Box sx={{ mb: 2 }}>
        <Typography variant="body2" color="text.secondary">Order ID</Typography>
        <Typography variant="body1" fontFamily="monospace">{order.id}</Typography>
      </Box>

      <Box sx={{ mb: 2 }}>
        <Typography variant="body2" color="text.secondary">Customer</Typography>
        <Typography variant="body1">{order.customerId}</Typography>
      </Box>

      <Box sx={{ mb: 2 }}>
        <Typography variant="body2" color="text.secondary">Status</Typography>
        <Chip label={order.status} color={STATUS_COLORS[order.status]} sx={{ mt: 0.5 }} />
      </Box>

      {order.status !== 'REJECTED' && order.status !== 'CANCELLED' && (
        <Stepper activeStep={activeStep >= 0 ? activeStep : 0} sx={{ mb: 2 }}>
          {STATUS_STEPS.map((label) => (
            <Step key={label}>
              <StepLabel>{label}</StepLabel>
            </Step>
          ))}
        </Stepper>
      )}

      <Divider sx={{ my: 2 }} />

      <Typography variant="subtitle2" gutterBottom>Items</Typography>
      <List dense>
        {order.items.map((item, i) => (
          <ListItem key={i}>
            <ListItemText
              primary={item.productId}
              secondary={`Qty: ${item.quantity} × $${item.unitPrice.toFixed(2)} = $${item.subtotal.toFixed(2)}`}
            />
          </ListItem>
        ))}
      </List>

      <Divider sx={{ my: 1 }} />
      <Typography variant="h6" sx={{ mt: 1 }}>Total: ${order.totalAmount.toFixed(2)}</Typography>

      <Box sx={{ mt: 1 }}>
        <Typography variant="body2" color="text.secondary">
          Created: {new Date(order.createdAt).toLocaleString()}
        </Typography>
      </Box>

      {canCancel && (
        <Button
          variant="outlined"
          color="error"
          startIcon={<CancelIcon />}
          onClick={handleCancel}
          disabled={cancelling}
          sx={{ mt: 2 }}
          fullWidth
        >
          {cancelling ? 'Cancelling...' : 'Cancel Order'}
        </Button>
      )}
    </Paper>
  );
}
```

**Step 2: Verify**

Run: `cd marketplace-platform/marketplace-frontend && npm run build`

**Step 3: Commit**

```bash
git add marketplace-platform/marketplace-frontend/
git commit -m "feat(frontend): add OrderDetail panel with status stepper and cancel button

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 12: Admin Dashboard — Wire Up the Page

**Files:**
- Modify: `marketplace-platform/marketplace-frontend/src/pages/AdminDashboard.tsx`

**Step 1: Implement AdminDashboard page**

```typescript
// src/pages/AdminDashboard.tsx
import { useState } from 'react';
import { Box, Typography, Grid } from '@mui/material';
import OrdersTable from '../components/admin/OrdersTable';
import InventoryTable from '../components/admin/InventoryTable';
import OrderDetail from '../components/admin/OrderDetail';
import { Order } from '../types';

export default function AdminDashboard() {
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);

  return (
    <Box>
      <Typography variant="h4" gutterBottom>Admin Dashboard</Typography>

      <OrdersTable
        onSelectOrder={setSelectedOrder}
        selectedOrderId={selectedOrder?.id}
      />

      <Grid container spacing={2} sx={{ mt: 1 }}>
        <Grid item xs={12} md={selectedOrder ? 7 : 12}>
          <InventoryTable />
        </Grid>

        {selectedOrder && (
          <Grid item xs={12} md={5}>
            <OrderDetail
              order={selectedOrder}
              onOrderUpdated={() => {}}
            />
          </Grid>
        )}
      </Grid>
    </Box>
  );
}
```

**Step 2: Verify full build**

Run: `cd marketplace-platform/marketplace-frontend && npm run build`
Expected: Build succeeds

**Step 3: Commit**

```bash
git add marketplace-platform/marketplace-frontend/
git commit -m "feat(frontend): wire up Admin Dashboard with orders, inventory, and order detail

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 13: Final Verification + Push

**Step 1: Build frontend**

Run: `cd marketplace-platform/marketplace-frontend && npm run build`
Expected: Build succeeds with no TypeScript errors

**Step 2: Build backend**

Run: `cd marketplace-platform && export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && mvn clean compile`
Expected: BUILD SUCCESS

**Step 3: Push to GitHub**

Run: `git push origin main`
