import {
  Drawer, Box, Typography, List, ListItem, ListItemText,
  IconButton, Divider, Button, Badge, Fab, Chip,
} from '@mui/material';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import { useState } from 'react';
import type { CartItem } from '../../types';

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
                        <Box component="span" sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.5 }}>
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
                          <Typography variant="body2" component="span" sx={{ ml: 1 }}>
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
                <Button variant="contained" color="primary" onClick={() => { onCheckout(); setOpen(false); }} size="large">
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
