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
