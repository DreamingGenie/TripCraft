<template>
  <!-- 현재여행 칩: 로그인 + 여행이 1개 이상일 때만 노출 (§2.2/§2.4) -->
  <div v-if="auth.isLoggedIn && trips.length" class="trip-chip-wrap" ref="wrapRef">
    <button class="trip-chip" @click="toggle" :aria-expanded="open">
      <span class="trip-chip-label">{{ currentTitle }}</span>
      <svg class="trip-chip-caret" width="12" height="12" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <polyline points="6 9 12 15 18 9" />
      </svg>
    </button>

    <div v-show="open" class="trip-chip-menu" @click="open = false">
      <button
        v-for="t in trips"
        :key="t.id"
        class="trip-chip-item"
        :class="{ current: t.id === activeTripStore.id }"
        @click="selectTrip(t.id)"
      >
        <span class="trip-chip-item-title">{{ t.title }}</span>
        <span v-if="t.startDate" class="trip-chip-item-dates">{{ t.startDate }} ~ {{ t.endDate }}</span>
      </button>
      <button class="trip-chip-item trip-chip-new" @click="newTrip">+ 새 일정</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, inject } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useActiveTripStore } from '@/stores/activeTrip'
import { tripApi } from '@/api/trip'

const router = useRouter()
const auth = useAuthStore()
const activeTripStore = useActiveTripStore()
const openScheduleModal = inject('openScheduleModal', () => {})

const trips = ref([])
const open = ref(false)
const wrapRef = ref(null)

const currentTitle = computed(() => {
  const t = trips.value.find((t) => t.id === activeTripStore.id)
  return t ? t.title : '여행 선택'
})

async function loadTrips() {
  if (!auth.isLoggedIn) {
    trips.value = []
    return
  }
  try {
    trips.value = await tripApi.list()
    // 현재 여행이 비었거나 목록에 없으면 첫 여행으로 폴백
    const cur = activeTripStore.id
    const exists = cur != null && trips.value.some((t) => t.id === cur)
    if (!exists && trips.value.length) activeTripStore.set(trips.value[0].id)
  } catch {
    trips.value = []
  }
}

function toggle() {
  open.value = !open.value
}

function selectTrip(id) {
  activeTripStore.set(id)
  router.push(`/plan/${id}`)
}

function newTrip() {
  openScheduleModal()
}

function onOutsideClick(e) {
  if (wrapRef.value && !wrapRef.value.contains(e.target)) open.value = false
}

// 로그인 상태 변화(로그인/로그아웃) 시 1회 재로드 (과한 폴링 금지)
watch(() => auth.isLoggedIn, loadTrips)

onMounted(() => {
  loadTrips()
  document.addEventListener('click', onOutsideClick, true)
})
onBeforeUnmount(() => document.removeEventListener('click', onOutsideClick, true))
</script>

<style scoped>
.trip-chip-wrap {
  position: relative;
}
.trip-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 200px;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--purple-900);
  background: var(--purple-50);
  border: 0.5px solid var(--purple-100);
  border-radius: var(--radius-full);
  padding: 6px 12px;
  white-space: nowrap;
  transition: box-shadow 0.12s, background 0.12s;
}
.trip-chip:hover {
  box-shadow: var(--shadow-sm);
}
.trip-chip-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.trip-chip-caret {
  flex-shrink: 0;
  color: var(--purple-900);
}

.trip-chip-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 240px;
  max-height: 360px;
  overflow-y: auto;
  background: var(--bg-surface);
  border: 0.5px solid var(--gray-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  z-index: 100;
  padding: 4px;
}
.trip-chip-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  width: 100%;
  text-align: left;
  padding: 9px 12px;
  border: none;
  background: none;
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  transition: background 0.1s;
}
.trip-chip-item:hover {
  background: var(--bg-page);
}
.trip-chip-item.current {
  background: var(--purple-50);
}
.trip-chip-item-title {
  font-size: var(--text-sm);
  font-weight: 600;
}
.trip-chip-item-dates {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}
.trip-chip-new {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--purple-900);
  border-top: 0.5px solid var(--gray-border);
  border-radius: 0 0 var(--radius-sm) var(--radius-sm);
  margin-top: 2px;
}
</style>
