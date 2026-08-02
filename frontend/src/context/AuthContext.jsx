import React, { createContext, useState, useEffect, useCallback } from 'react';
import api, {
  TOKEN_KEY,
  USER_KEY,
  extractErrorMessage,
  extractFieldErrors,
  setUnauthorizedHandler,
} from '../api/client';
import { authApi } from '../api/authApi';

export const AuthContext = createContext();

/**
 * Authentication state.
 *
 * Two changes from the previous version worth noting:
 *
 * 1. It no longer mutates `axios.defaults.headers.common`. The shared client attaches the token per
 *    request instead, so there is no global side effect and no stale header after logout.
 * 2. A token found in localStorage is verified against `GET /api/auth/me` before the user is
 *    treated as signed in. Previously any string in localStorage was trusted, so an expired token
 *    produced a logged-in UI where every request failed with 401.
 */
export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const clearSession = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    setUser(null);
  }, []);

  // Lets the axios interceptor drop the session when the server rejects our token.
  useEffect(() => {
    setUnauthorizedHandler(clearSession);
    return () => setUnauthorizedHandler(null);
  }, [clearSession]);

  useEffect(() => {
    let cancelled = false;

    const restoreSession = async () => {
      const token = localStorage.getItem(TOKEN_KEY);
      const storedUser = localStorage.getItem(USER_KEY);

      if (!token || !storedUser) {
        setLoading(false);
        return;
      }

      // Render immediately from the cached profile so there is no visible flash of the login
      // screen, then confirm with the server and correct course if the token is no longer good.
      try {
        setUser(JSON.parse(storedUser));
      } catch {
        clearSession();
        setLoading(false);
        return;
      }

      try {
        const { data } = await authApi.me();
        if (!cancelled) {
          setUser(data);
          localStorage.setItem(USER_KEY, JSON.stringify(data));
        }
      } catch (error) {
        // Only a definitive rejection ends the session. A network blip or a service still starting
        // up must not log the user out — that would sign everyone out during a deploy.
        const status = error?.response?.status;
        if (!cancelled && (status === 401 || status === 403 || status === 404)) {
          clearSession();
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    restoreSession();
    return () => {
      cancelled = true;
    };
  }, [clearSession]);

  const login = async (email, password) => {
    try {
      const { data } = await authApi.login(email, password);
      const { token, user: userData } = data;

      localStorage.setItem(TOKEN_KEY, token);
      localStorage.setItem(USER_KEY, JSON.stringify(userData));
      setUser(userData);

      return { success: true, user: userData };
    } catch (error) {
      return {
        success: false,
        message: extractErrorMessage(error, 'Login failed. Please check your details.'),
        fieldErrors: extractFieldErrors(error),
      };
    }
  };

  const register = async (name, email, password, role) => {
    try {
      await authApi.register(name, email, password, role);
      return { success: true };
    } catch (error) {
      return {
        success: false,
        message: extractErrorMessage(error, 'Registration failed. Please try again.'),
        fieldErrors: extractFieldErrors(error),
      };
    }
  };

  const resetPassword = async (email, newPassword) => {
    try {
      await authApi.resetPassword(email, newPassword);
      return { success: true };
    } catch (error) {
      return {
        success: false,
        message: extractErrorMessage(error, 'Password reset failed.'),
        fieldErrors: extractFieldErrors(error),
      };
    }
  };

  const logout = () => {
    clearSession();
  };

  /** Normalises the role field, which the API returns as `role` but older data may key as `Role`. */
  const role = user ? user.role || user.Role || null : null;

  return (
    <AuthContext.Provider
      value={{ user, role, login, register, resetPassword, logout, loading, api }}
    >
      {children}
    </AuthContext.Provider>
  );
};
