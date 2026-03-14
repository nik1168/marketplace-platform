import { useEffect, useState } from 'react';
import {
  Box, Typography, Chip, Paper, Stepper, Step, StepLabel,
  Button, List, ListItem, ListItemText, Alert, CircularProgress,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import type { Order, OrderStatus } from '../../types';
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
        if (['CONFIRMED', 'REJECTED', 'CANCELLED', 'SHIPPED'].includes(updated.status)) {
          setPolling(false);
        }
      } catch {
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
          <>
            <CircularProgress size={16} sx={{ ml: 2 }} />
            <Typography variant="caption" sx={{ ml: 1 }}>Waiting for confirmation...</Typography>
          </>
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
