import { http } from './http'

export async function getTransitTime(fromId, toId, hour = 9) {
  return await http.get(`/api/transit?fromId=${fromId}&toId=${toId}&hour=${hour}`)
  // { durationMinutes, transportMode, transferCount, fare } | null
}
