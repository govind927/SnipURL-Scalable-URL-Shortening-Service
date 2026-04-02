import axios from 'axios';

// Base URL — proxied to Spring Boot in dev, set env var in production
const BASE_URL = process.env.REACT_APP_API_URL || '';

const api = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
});

// ── Response interceptor — unified error shape ──────────────────
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const message =
      error.response?.data?.message ||
      error.response?.data?.error ||
      error.message ||
      'Something went wrong';
    return Promise.reject({ message, status: error.response?.status });
  }
);

// ── API methods ─────────────────────────────────────────────────

/**
 * POST /api/shorten
 * @param {string} longUrl
 * @param {string|null} customAlias
 * @param {string|null} expiryTime  ISO datetime string
 */
export const shortenUrl = async (longUrl, customAlias = null, expiryTime = null) => {
  const payload = { longUrl };
  if (customAlias?.trim()) payload.customAlias = customAlias.trim();
  if (expiryTime)          payload.expiryTime  = expiryTime;

  const { data } = await api.post('/api/shorten', payload);
  return data;
};

/**
 * GET /api/stats/:shortCode
 */
export const getStats = async (shortCode) => {
  const { data } = await api.get(`/api/stats/${shortCode}`);
  return data;
};

/**
 * DELETE /api/:shortCode
 */
export const deleteUrl = async (shortCode) => {
  await api.delete(`/api/${shortCode}`);
};
