<template>
  <AppHeader />
  <RouterView />
  <AppToast />
  <ScheduleModal v-if="scheduleModal.visible"
               @close="scheduleModal.visible = false"
               @created="scheduleModal.onCreated?.($event)" />
</template>

<script setup>
import { onMounted, reactive } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import AppToast from '@/components/AppToast.vue'
import ScheduleModal from '@/components/ScheduleModal.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const scheduleModal = reactive({ visible: false, onCreated: null })

onMounted(() => auth.fetchMe())

/* 전역에서 일정 모달 열기 — provide로 하위 컴포넌트에 공유 */
import { provide } from 'vue'
provide('openScheduleModal', (onCreated) => {
  scheduleModal.visible = true
  scheduleModal.onCreated = onCreated ?? null
})
</script>
