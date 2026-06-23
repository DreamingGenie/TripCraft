import { defineStore } from 'pinia'
import { ref } from 'vue'

const STORAGE_KEY = 'tripcraft.activeTripId'

/**
 * 화면 간 공유되는 "현재 일정". 탐색·일정 화면이 같은 일정을 활성으로 유지하도록
 * localStorage 에 영속한다. id 타입은 숫자(서버 trip id).
 */
export const useActiveTripStore = defineStore('activeTrip', () => {
  const stored = localStorage.getItem(STORAGE_KEY)
  const id = ref(stored !== null && stored !== '' ? Number(stored) : null)

  function set(tripId) {
    id.value = tripId ?? null
    if (tripId == null) localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, String(tripId))
  }

  return { id, set }
})
