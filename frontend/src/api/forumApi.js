import api from './client';

/** forum-service endpoints. Post ids are MongoDB ObjectId strings, not integers. */
export const forumApi = {
  listPosts: () => api.get('/api/forum/posts'),

  feed: (category, page = 0, size = 20) =>
    api.get('/api/forum/posts/feed', { params: { category, page, size } }),

  unanswered: (page = 0, size = 20) =>
    api.get('/api/forum/posts/unanswered', { params: { page, size } }),

  findById: (id) => api.get(`/api/forum/posts/${id}`),

  categories: () => api.get('/api/forum/categories'),

  createPost: (payload) => api.post('/api/forum/posts', payload),

  addComment: (postId, content) =>
    api.post(`/api/forum/posts/${postId}/comments`, { content }),

  setResolved: (postId, resolved) =>
    api.patch(`/api/forum/posts/${postId}/resolved`, null, { params: { resolved } }),

  deletePost: (postId) => api.delete(`/api/forum/posts/${postId}`),

  deleteComment: (postId, commentId) =>
    api.delete(`/api/forum/posts/${postId}/comments/${commentId}`),
};
