import api from './client';

/** auth-service endpoints, routed through the gateway. */
export const authApi = {
  login: (email, password) => api.post('/api/auth/login', { email, password }),

  register: (name, email, password, role) =>
    api.post('/api/auth/register', { name, email, password, role }),

  resetPassword: (email, newPassword) =>
    api.post('/api/auth/reset-password', { email, newPassword }),

  /** Re-validates a token held in localStorage on page load. */
  me: () => api.get('/api/auth/me'),
};

/** Admin endpoints. All require the Admin role; the server enforces it. */
export const adminApi = {
  listUsers: () => api.get('/api/admin/users'),

  searchUsers: (params) => api.get('/api/admin/users/page', { params }),

  stats: () => api.get('/api/admin/stats'),

  updateRole: (id, role) => api.put(`/api/admin/users/${id}`, { role }),

  setEnabled: (id, enabled) =>
    api.patch(`/api/admin/users/${id}/enabled`, null, { params: { enabled } }),

  deleteUser: (id) => api.delete(`/api/admin/users/${id}`),
};
