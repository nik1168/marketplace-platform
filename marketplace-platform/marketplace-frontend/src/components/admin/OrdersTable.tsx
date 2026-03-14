import { useEffect, useState } from 'react';
import {
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Paper, Chip, Typography, IconButton, Box, CircularProgress, Alert, Tooltip,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { listOrders } from '../../api/client';
import type { Order, OrderStatus } from '../../types';

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
    } catch {
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
