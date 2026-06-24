import { http } from './http'

export async function getTransitTime(fromId, toId, hour = 9) {
  return await http.get(`/api/transit?fromId=${fromId}&toId=${toId}&hour=${hour}`)
}

export async function getTransitByMode(fromId, toId, mode, hour = 9) {
  return await http.get(`/api/transit?fromId=${fromId}&toId=${toId}&hour=${hour}&mode=${mode}`)
}

// 좌표 기반(커스텀 장소). 한쪽이라도 attraction id 가 없을 때 사용.
export async function getTransitByCoords(fromLat, fromLng, toLat, toLng, mode, hour = 9) {
  return await http.get(`/api/transit/by-coords?fromLat=${fromLat}&fromLng=${fromLng}&toLat=${toLat}&toLng=${toLng}&hour=${hour}&mode=${mode}`)
}

export async function getTransitDetail(fromId, toId, hour = 9) {
  return await http.get(`/api/transit/detail?fromId=${fromId}&toId=${toId}&hour=${hour}`)
}

export async function selectTransitPath(fromId, toId, hour, pathIndex) {
  return await http.post(`/api/transit/select?fromId=${fromId}&toId=${toId}&hour=${hour}&pathIndex=${pathIndex}`)
}

export async function getDrivingOptions(fromId, toId, hour = 9) {
  return await http.get(`/api/transit/driving-options?fromId=${fromId}&toId=${toId}&hour=${hour}`)
}

export async function getDrivingOption(fromId, toId, hour = 9, optionIndex) {
  const res = await http.get(`/api/transit/driving-options?fromId=${fromId}&toId=${toId}&hour=${hour}&optionIndex=${optionIndex}`)
  return Array.isArray(res) ? res[0] ?? null : null
}

export async function applyDrivingOption(fromId, toId, hour, optionIndex) {
  return await http.post(`/api/transit/select-driving?fromId=${fromId}&toId=${toId}&hour=${hour}&optionIndex=${optionIndex}`)
}

export async function getLaneSegments(fromId, toId, hour = 9) {
  return await http.get(`/api/transit/segments?fromId=${fromId}&toId=${toId}&hour=${hour}`)
}

export async function getWalkingCoords(startLat, startLng, endLat, endLng) {
  return await http.get(`/api/transit/walking-coords?startLat=${startLat}&startLng=${startLng}&endLat=${endLat}&endLng=${endLng}`)
}
