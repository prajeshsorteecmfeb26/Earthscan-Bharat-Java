import api from './client';

/** land-service endpoints. */
export const landApi = {
  /**
   * Searches listings.
   *
   * Undefined values are stripped so an untouched filter is genuinely absent from the query string
   * rather than sent as `district=undefined`, which the backend would treat as a literal filter and
   * match nothing.
   */
  search: (criteria = {}, page = 0, size = 12, sort = 'landIntelligenceScore,desc') => {
    const params = { page, size, sort };
    Object.entries(criteria).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '' && value !== 'All') {
        params[key] = value;
      }
    });
    return api.get('/api/lands', { params });
  },

  findById: (id) => api.get(`/api/lands/${id}`),

  analysis: (id) => api.get(`/api/lands/${id}/analysis`),

  soilTypes: () => api.get('/api/lands/soil-types'),

  districts: () => api.get('/api/lands/districts'),

  myListings: () => api.get('/api/lands/mine'),

  create: (payload) => api.post('/api/lands', payload),

  update: (id, payload) => api.put(`/api/lands/${id}`, payload),

  updateStatus: (id, status) =>
    api.patch(`/api/lands/${id}/status`, null, { params: { status } }),

  setVerified: (id, verified) =>
    api.patch(`/api/lands/${id}/verified`, null, { params: { verified } }),

  remove: (id) => api.delete(`/api/lands/${id}`),
};

/** Saved searches and shortlisted listings. Always scoped to the caller by the server. */
export const savedSearchApi = {
  list: () => api.get('/api/saved-searches'),

  create: (payload) => api.post('/api/saved-searches', payload),

  remove: (id) => api.delete(`/api/saved-searches/${id}`),
};
