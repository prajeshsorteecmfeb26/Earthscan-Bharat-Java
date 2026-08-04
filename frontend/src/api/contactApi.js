import api from './client';

export const contactApi = {
  submitQuery: (data) => api.post('/api/contact', data),
  getQueries: () => api.get('/api/admin/contact-queries'),
  replyQuery: (id, reply) => api.put(`/api/admin/contact-queries/${id}/reply`, { reply })
};

export default contactApi;
