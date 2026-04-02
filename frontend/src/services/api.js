import axios from 'axios';

const BASE_URL = process.env.REACT_APP_API_URL || '';

const api = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
});

// Attach JWT token to every request automatically
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Unified error shape
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/';
    }
    const message =
      error.response?.data?.message ||
      error.response?.data?.error ||
      error.message ||
      'Something went wrong';
    return Promise.reject({ message, status: error.response?.status });
  }
);

// ── URL API ─────────────────────────────────────────────────────
export const shortenUrl = async (longUrl, customAlias = null, expiryTime = null) => {
  const payload = { longUrl };
  if (customAlias?.trim()) payload.customAlias = customAlias.trim();
  if (expiryTime)          payload.expiryTime  = expiryTime;
  const { data } = await api.post('/api/shorten', payload);
  return data;
};

export const getStats = async (shortCode) => {
  const { data } = await api.get(`/api/stats/${shortCode}`);
  return data;
};

export const deleteUrl = async (shortCode) => {
  await api.delete(`/api/my-urls/${shortCode}`);
};

// ── Auth API ─────────────────────────────────────────────────────
export const register = async (name, email, password) => {
  const { data } = await api.post('/api/auth/register', { name, email, password });
  return data;
};

export const login = async (email, password) => {
  const { data } = await api.post('/api/auth/login', { email, password });
  return data;
};

// ── Dashboard API ────────────────────────────────────────────────
export const getMyUrls = async () => {
  const { data } = await api.get('/api/my-urls');
  return data;
};

// ── Preview & QR API ─────────────────────────────────────────────
export const getPreview = async (shortCode) => {
  const { data } = await api.get(`/api/preview/${shortCode}`);
  return data;
};

export const getQrCodeUrl = (shortCode) =>
  `${BASE_URL}/api/qr/${shortCode}`;
