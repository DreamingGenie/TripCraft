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
}
