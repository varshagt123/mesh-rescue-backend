import axios from 'axios';
import { io } from 'socket.io-client';

// Your backend server URL
const API_BASE_URL = 'http://localhost:5000/api'; 
const SOCKET_URL = 'http://localhost:5000';

// Axios instance for REST API calls (Login, Register, SOS, etc.)
export const API = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Automatically attach JWT token to requests if the user is logged in
API.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Socket.IO connection for real-time messaging and offline sync
export const socket = io(SOCKET_URL, {
  autoConnect: false,
  auth: {
    token: localStorage.getItem('token')
  }
});
socket.on('connect', () => {
  console.log('Socket connected:', socket.id);
});

socket.on('connect_error', (error) => {
  console.error('Socket connection error:', error.message);
});