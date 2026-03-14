import { useEffect, useState } from 'react';
import {
  Paper, Typography, Box, Chip, Stepper, Step, StepLabel,
  List, ListItem, ListItemText, Divider, Button, Alert,
} from '@mui/material';
import CancelIcon from '@mui/icons-material/Cancel';
import type { Order, OrderStatus } from '../../types';
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
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: string } };
      setError(axiosErr.response?.data || 'Failed to cancel order');
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
