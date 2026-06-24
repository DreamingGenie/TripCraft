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

// 좌표 기반 대중교통 경로 단계(커스텀 장소). attraction /detail 과 동일 응답 구조.
export async function getTransitDetailByCoords(fromLat, fromLng, toLat, toLng, hour = 9) {
  return await http.get(`/api/transit/by-coords/detail?fromLat=${fromLat}&fromLng=${fromLng}&toLat=${toLat}&toLng=${toLng}&hour=${hour}`)
}

// 좌표 기반 자동차 단일 옵션(커스텀 장소).
export async function getDrivingOptionByCoords(fromLat, fromLng, toLat, toLng, hour = 9, optionIndex) {
  const res = await http.get(`/api/transit/by-coords/driving-options?fromLat=${fromLat}&fromLng=${fromLng}&toLat=${toLat}&toLng=${toLng}&hour=${hour}&optionIndex=${optionIndex}`)
  return Array.isArray(res) ? res[0] ?? null : null
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

// 통합 경로 구간(구간별 색·도보·역마커용). 어트랙션 / 커스텀 좌표 두 버전.
export async function getRouteSegments(fromId, toId, hour = 9) {
  return await http.get(`/api/transit/route-segments?fromId=${fromId}&toId=${toId}&hour=${hour}`)
}
export async function getRouteSegmentsByCoords(fromLat, fromLng, toLat, toLng, hour = 9) {
  return await http.get(`/api/transit/by-coords/route-segments?fromLat=${fromLat}&fromLng=${fromLng}&toLat=${toLat}&toLng=${toLng}&hour=${hour}`)
}

export async function getWalkingCoords(startLat, startLng, endLat, endLng) {
  return await http.get(`/api/transit/walking-coords?startLat=${startLat}&startLng=${startLng}&endLat=${endLat}&endLng=${endLng}`)
}
