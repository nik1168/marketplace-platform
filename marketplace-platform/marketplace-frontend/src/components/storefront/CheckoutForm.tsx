import { useState } from 'react';
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  TextField, Button, Typography, List, ListItem, ListItemText,
  Divider, Alert, CircularProgress, Box,
} from '@mui/material';
import type { CartItem, Order } from '../../types';
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
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: string } };
      setError(axiosErr.response?.data || 'Failed to place order. Is the Order Service running?');
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
