import axios from 'axios';

/**
 * Single axios instance for the whole app.
 *
 * The previous code called `axios.post('http://localhost:5130/api/...')` from nine different
 * places with the host hard-coded in each one. That made deployment impossible without editing
 * source, and it meant the auth header was configured by mutating the global axios defaults — so
 * any other library sharing that global instance also sent the token.
 *
 * Everything now goes through the gateway on one configurable origin.
 */
const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

const api = axios.create({
  baseURL,
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' },
});

export const TOKEN_KEY = 'token';
export const USER_KEY = 'user';

/**
 * Reads the token on every request rather than setting a default header once at login.
 *
 * Setting it once leaves a stale header behind after logout, and misses the case where another
 * tab logs in — reading storage per request keeps all tabs consistent.
 */
api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** Callback invoked when the server rejects our token, wired up by AuthContext. */
let onUnauthorized = null;

export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

api.interceptors.response.use(
  (response) => response,
  (error) => {
    // A 401 on any request means the token is gone, expired, or was signed with a rotated
    // secret. Without this the app keeps rendering a logged-in shell whose every request fails,
    // which reads as "the site is broken" rather than "please sign in again".
    if (error.response?.status === 401) {
      const isLoginAttempt = error.config?.url?.includes('/api/auth/login');
      if (!isLoginAttempt && onUnauthorized) {
        onUnauthorized();
      }
    }
    return Promise.reject(error);
  },
);

/**
 * Turns any axios failure into a displayable string.
 *
 * Every service returns the same `ApiErrorResponse` shape, so one helper covers all of them.
 * Field-level validation errors are flattened into the message, because the backend's per-field
 * detail is far more useful to the user than a generic "request failed".
 */
export function extractErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  const data = error?.response?.data;

  if (data?.fieldErrors) {
    const messages = Object.values(data.fieldErrors).flat();
    if (messages.length > 0) {
      return messages.join(' ');
    }
  }
  if (data?.message) {
    return data.message;
  }
  if (error?.code === 'ECONNABORTED') {
    return 'The request timed out. Please check your connection and try again.';
  }
  if (!error?.response) {
    return 'Could not reach the server. Please check your connection.';
  }
  return fallback;
}

/** Per-field errors keyed by field name, for inline form display. */
export function extractFieldErrors(error) {
  const fieldErrors = error?.response?.data?.fieldErrors;
  if (!fieldErrors) {
    return {};
  }
  return Object.fromEntries(
    Object.entries(fieldErrors).map(([field, messages]) => [field, messages.join(' ')]),
  );
}

export default api;
