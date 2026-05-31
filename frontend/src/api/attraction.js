import { http } from './http'

export function fetchRegions() {
  return http.get('/api/attractions/regions')
}

export function searchAttractions({ keyword, region, sigungu, category, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams()
  if (keyword) params.set('keyword', keyword)
  if (region) params.set('region', region)
  if (sigungu?.length) params.set('sigungu', sigungu.join(','))
  if (category) params.set('category', category)
  params.set('page', page)
  params.set('size', size)
  return http.get(`/api/attractions?${params}`)
}

export function fetchAttractionDetail(id) {
  return http.get(`/api/attractions/${id}`)
}
