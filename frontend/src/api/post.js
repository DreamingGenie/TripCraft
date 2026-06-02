import { http } from './http'

export const postApi = {
  list: ({ page = 0, size = 10, sort = 'latest' } = {}) =>
    http.get(`/api/posts?page=${page}&size=${size}&sort=${sort}`),
  get: (id) => http.get(`/api/posts/${id}`),
  create: (body) => http.post('/api/posts', body),
  delete: (id) => http.del(`/api/posts/${id}`),
  toggleLike: (id) => http.post(`/api/posts/${id}/likes`),
  notices: () => http.get('/api/notices'),
}

export const commentApi = {
  list:   (postId)          => http.get(`/api/posts/${postId}/comments`),
  create: (postId, content) => http.post(`/api/posts/${postId}/comments`, { content }),
  delete: (postId, id)      => http.del(`/api/posts/${postId}/comments/${id}`),
}
