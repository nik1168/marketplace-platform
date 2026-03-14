import { useEffect, useState } from 'react';
import {
  Grid, Card, CardContent, CardActions, Typography, Button,
  Chip, TextField, Box, Alert, CircularProgress,
} from '@mui/material';
import AddShoppingCartIcon from '@mui/icons-material/AddShoppingCart';
import { listProducts } from '../../api/client';
import type { Product, CartItem } from '../../types';

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
    } catch {
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
          <Grid size={{ xs: 12, sm: 6, md: 4 }} key={product.productId}>
            <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column', opacity: isOutOfStock ? 0.6 : 1 }}>
              <CardContent sx={{ flexGrow: 1 }}>
                <Typography variant="h6" gutterBottom>{product.name}</Typography>
                <Chip label={product.category} size="small" sx={{ mb: 1 }} />
                <Typography variant="body2" color="text.secondary">SKU: {product.sku}</Typography>
                <Typography variant="h5" color="primary" sx={{ mt: 1 }}>
                  $29.99
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
                  slotProps={{ htmlInput: { min: 1, max: availableStock } }}
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
