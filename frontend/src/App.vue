<template>
  <AppHeader />
  <RouterView />
  <AppToast />
  <ScheduleModal v-if="scheduleModal.visible"
               @close="scheduleModal.visible = false"
               @created="handleTripCreated($event)" />
</template>

<script setup>
import { onMounted, reactive, provide } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppToast from '@/components/AppToast.vue'
import ScheduleModal from '@/components/ScheduleModal.vue'
import { useAuthStore } from '@/stores/auth'
import { useActiveTripStore } from '@/stores/activeTrip'

const auth = useAuthStore()
const router = useRouter()
const activeTripStore = useActiveTripStore()
const scheduleModal = reactive({ visible: false, onCreated: null })

onMounted(() => auth.fetchMe())

/* 전역에서 일정 모달 열기 — provide로 하위 컴포넌트에 공유 */
provide('openScheduleModal', (onCreated) => {
  scheduleModal.visible = true
  scheduleModal.onCreated = onCreated ?? null
})

/* 새 일정 생성 직후: 모든 호출처가 콜백을 안 넘기므로 여기서 새 여행으로 진입·활성화를
   중앙 처리(이전엔 생성만 되고 작업실로 안 들어가 보드가 새 여행을 로드하지 못함 →
   지도 버튼 등 보드 기능 무응답). 호출처가 콜백을 준 경우 추가 동작도 호출. */
function handleTripCreated(trip) {
  scheduleModal.visible = false
  if (trip?.id != null) {
    activeTripStore.set(trip.id)
    router.push(`/plan/${trip.id}`)
  }
  scheduleModal.onCreated?.(trip)
}
</script>
