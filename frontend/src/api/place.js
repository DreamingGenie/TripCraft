import { http } from './http'

// 장소 검색(Kakao Local) → [{ name, address, category, latitude, longitude }]
export const placeApi = {
  search: (query) => http.get(`/api/places/search?query=${encodeURIComponent(query)}`),
}

// 내 장소(개인 장소) CRUD
export const myPlaceApi = {
  list: () => http.get('/api/my-places'),
  create: (body) => http.post('/api/my-places', body),
  remove: (id) => http.del(`/api/my-places/${id}`),
}
