import { http } from './http'

export async function getTransitTime(fromId, toId, hour = 9) {
  return await http.get(`/api/transit?fromId=${fromId}&toId=${toId}&hour=${hour}`)
}

export async function getTransitDetail(fromId, toId, hour = 9) {
  return await http.get(`/api/transit/detail?fromId=${fromId}&toId=${toId}&hour=${hour}`)
}

export async function selectTransitPath(fromId, toId, pathIndex, hour = 9) {
  return await http.post(`/api/transit/select?fromId=${fromId}&toId=${toId}&hour=${hour}&pathIndex=${pathIndex}`)
}
