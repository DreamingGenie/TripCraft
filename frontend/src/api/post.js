import { http } from './http'

export const postApi = {
  list: ({ page = 0, size = 10, sort = 'latest', keyword = '' } = {}) => {
    const params = new URLSearchParams({ page, size, sort })
    if (keyword) params.set('keyword', keyword)
    return http.get(`/api/posts?${params}`)
  },
  get: (id) => http.get(`/api/posts/${id}`),
  create: (body) => http.post('/api/posts', body),
  update: (id, body) => http.patch(`/api/posts/${id}`, body),
  delete: (id) => http.del(`/api/posts/${id}`),
  toggleLike: (id) => http.post(`/api/posts/${id}/likes`),
  notices: () => http.get('/api/notices'),
}

export const bookmarkApi = {
  toggle: (postId) => http.post(`/api/posts/${postId}/bookmark`),
  myList: ({ page = 0, size = 10 } = {}) =>
    http.get(`/api/bookmarks/me?page=${page}&size=${size}`),
}

export const likeApi = {
  myList: ({ page = 0, size = 10 } = {}) =>
    http.get(`/api/likes/me?page=${page}&size=${size}`),
}

export const commentApi = {
  list:   (postId)          => http.get(`/api/posts/${postId}/comments`),
  /** parentId: 최상위 댓글이면 null, 대댓글이면 부모 댓글 id */
  create: (postId, content, parentId = null) =>
    http.post(`/api/posts/${postId}/comments`, { content, parentId }),
  delete: (postId, id)      => http.del(`/api/posts/${postId}/comments/${id}`),
}
