import { http } from './http'

export async function getTransitTime(fromId, toId, hour = 9, transportType = 0) {
  const data = await http.get(
    `/api/transit?fromId=${fromId}&toId=${toId}&hour=${hour}&transportType=${transportType}`
  )
  return data  // { durationMinutes, transportMode } or null
}
