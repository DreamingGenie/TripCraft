import { http } from './http'

export const tripApi = {
  list: () => http.get('/api/trips'),
  get: (id) => http.get(`/api/trips/${id}`),
  create: (body) => http.post('/api/trips', body),
  delete: (id) => http.del(`/api/trips/${id}`),

  addCandidate: (tripId, attractionId) =>
    http.post(`/api/trips/${tripId}/candidates`, { attractionId }),
  removeCandidate: (tripId, candidateId) =>
    http.del(`/api/trips/${tripId}/candidates/${candidateId}`),

  placeBlock: (tripId, body) => http.post(`/api/trips/${tripId}/blocks`, body),
  updateBlock: (tripId, blockId, body) => http.put(`/api/trips/${tripId}/blocks/${blockId}`, body),
  removeBlock: (tripId, blockId) => http.del(`/api/trips/${tripId}/blocks/${blockId}`),

  updateDefaultTransitMode: (tripId, mode) =>
    http.patch(`/api/trips/${tripId}/default-transit-mode`, { mode }),

  getBlocksSummary: (tripId) => http.get(`/api/trips/${tripId}/blocks-summary`),

  /** 공유된 일정 가져오기 — newStartDate(YYYY-MM-DD) 기준으로 날짜 재계산 후 내 일정으로 복제 */
  copy: (tripId, newStartDate) => http.post(`/api/trips/${tripId}/copy`, { newStartDate }),

  // 협업 관련
  listCollaborating: () => http.get('/api/trips/collaborating'),
  getCollaborators: (tripId) => http.get(`/api/trips/${tripId}/collaborators`),
  inviteCollaborator: (tripId, memberId, role = 'EDITOR') =>
    http.post(`/api/trips/${tripId}/collaborators`, { memberId, role }),
  removeCollaborator: (tripId, targetMemberId) =>
    http.del(`/api/trips/${tripId}/collaborators/${targetMemberId}`),

  // 링크 공유: 접근 레벨 설정(소유자) → { access, token } / 토큰으로 조회(비로그인 허용)
  setShareAccess: (tripId, access) => http.put(`/api/trips/${tripId}/share`, { access }),
  getShared: (token) => http.get(`/api/trips/shared/${token}`),
}
