import api from './client';

/** notification-service endpoints. */
export const notificationApi = {
  list: (unreadOnly = false, page = 0, size = 20) =>
    api.get('/api/notifications', { params: { unreadOnly, page, size } }),

  unreadCount: () => api.get('/api/notifications/unread-count'),

  markRead: (id) => api.patch(`/api/notifications/${id}/read`),

  markAllRead: () => api.patch('/api/notifications/read-all'),

  remove: (id) => api.delete(`/api/notifications/${id}`),
};
