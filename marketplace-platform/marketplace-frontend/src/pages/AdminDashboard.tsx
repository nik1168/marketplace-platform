import { useState } from 'react';
import { Box, Typography, Grid } from '@mui/material';
import OrdersTable from '../components/admin/OrdersTable';
import InventoryTable from '../components/admin/InventoryTable';
import OrderDetail from '../components/admin/OrderDetail';
import type { Order } from '../types';

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
        <Grid size={{ xs: 12, md: selectedOrder ? 7 : 12 }}>
          <InventoryTable />
        </Grid>

        {selectedOrder && (
          <Grid size={{ xs: 12, md: 5 }}>
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
