import { useState } from 'react';
import { Box, Typography } from '@mui/material';
import ProductCatalog from '../components/storefront/ProductCatalog';
import Cart from '../components/storefront/Cart';
import CheckoutForm from '../components/storefront/CheckoutForm';
import OrderConfirmation from '../components/storefront/OrderConfirmation';
import type { CartItem, Product, Order } from '../types';

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
