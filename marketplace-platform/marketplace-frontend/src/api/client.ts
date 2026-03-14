import axios from 'axios';
import type { Order, Product, CreateOrderRequest } from '../types';

const orderApi = axios.create({
  baseURL: 'http://localhost:8080/api',
});

const inventoryApi = axios.create({
  baseURL: 'http://localhost:8081/api',
});

export const createOrder = async (request: CreateOrderRequest): Promise<Order> => {
  const response = await orderApi.post<Order>('/orders', request);
  return response.data;
};

export const getOrder = async (orderId: string): Promise<Order> => {
  const response = await orderApi.get<Order>(`/orders/${orderId}`);
  return response.data;
};

export const listOrders = async (page = 0, size = 20): Promise<{ content: Order[]; totalElements: number }> => {
  const response = await orderApi.get('/orders', { params: { page, size, sort: 'createdAt,desc' } });
  return response.data;
};

export const cancelOrder = async (orderId: string): Promise<Order> => {
  const response = await orderApi.put<Order>(`/orders/${orderId}/cancel`);
  return response.data;
};

export const listProducts = async (): Promise<Product[]> => {
  const response = await inventoryApi.get<Product[]>('/products');
  return response.data;
};

export const getProduct = async (productId: string): Promise<Product> => {
  const response = await inventoryApi.get<Product>(`/products/${productId}`);
  return response.data;
};
