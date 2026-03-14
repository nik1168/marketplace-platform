import { useEffect, useState } from 'react';
import {
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Paper, Typography, IconButton, Box, CircularProgress, Alert, Chip, Tooltip,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { listProducts } from '../../api/client';
import type { Product } from '../../types';

export default function InventoryTable() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadProducts = async () => {
    try {
      const data = await listProducts();
      setProducts(data);
      setError(null);
    } catch {
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
