import { http } from './http'

export function searchAttractions({ keyword, region, category, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams()
  if (keyword) params.set('keyword', keyword)
  if (region) params.set('region', region)
  if (category) params.set('category', category)
  params.set('page', page)
  params.set('size', size)
  return http.get(`/api/attractions?${params}`)
}
