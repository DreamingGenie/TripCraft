<template>
  <div id="modal-overlay" class="open" @click.self="$emit('close')">
    <div class="modal-card">
      <div class="modal-header">
        <span class="modal-title">새 여행 일정 만들기</span>
        <button class="modal-close" @click="$emit('close')">✕</button>
      </div>

      <div style="margin-bottom:14px">
        <label class="modal-label">여행 이름 <span style="color:#993556">*</span></label>
        <input class="modal-input" v-model="form.title" type="text"
               placeholder="ex. 제주 여름 여행" maxlength="30" />
        <div style="text-align:right;font-size:10px;color:var(--gray-muted);margin-top:2px">
          {{ form.title.length }}/30
        </div>
      </div>

      <div style="margin-bottom:14px">
        <label class="modal-label">여행 기간 <span style="color:#993556">*</span></label>
        <div class="modal-date-row">
          <input class="modal-input" type="date" v-model="form.start" />
          <span style="color:var(--gray-muted);font-size:12px">—</span>
          <input class="modal-input" type="date" v-model="form.end" />
        </div>
        <p style="font-size:10px;color:var(--gray-muted);margin-top:4px">{{ nightsText }}</p>
      </div>

      <div style="margin-bottom:14px">
        <label class="modal-label">여행 인원 (선택)</label>
        <div class="count-row">
          <button class="count-btn" @click="count = Math.max(1, count - 1)">−</button>
          <span class="count-val">{{ count }}명</span>
          <button class="count-btn" @click="count = Math.min(20, count + 1)">+</button>
        </div>
      </div>

      <div style="margin-bottom:20px">
        <label class="modal-label">주요 이동수단</label>
        <div class="transit-mode-selector">
          <button v-for="opt in transitModeOpts" :key="opt.mode"
                  class="transit-mode-btn" type="button"
                  :class="{ active: defaultTransitMode === opt.mode }"
                  @click="defaultTransitMode = opt.mode">
            {{ opt.icon }} {{ opt.label }}
          </button>
        </div>
        <p style="font-size:10px;color:var(--gray-muted);margin-top:4px">
          구간별로 나중에 변경할 수 있어요
        </p>
      </div>

      <div class="modal-btns">
        <button class="btn-cancel" @click="$emit('close')">취소</button>
        <button class="btn-create" :disabled="!isValid || creating" @click="create">만들기</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { useToastStore } from '@/stores/toast'
import { tripApi } from '@/api/trip'

const emit = defineEmits(['close', 'created'])
const toast = useToastStore()

const form = reactive({ title: '', start: '', end: '' })
const count = ref(2)
const creating = ref(false)
const defaultTransitMode = ref('PUBLIC_TRANSIT')

const transitModeOpts = [
  { mode: 'PUBLIC_TRANSIT', icon: '🚌', label: '대중교통' },
  { mode: 'DRIVING', icon: '🚗', label: '자동차' },
  { mode: 'WALKING', icon: '🚶', label: '도보' },
]

const isValid = computed(() => {
  if (!form.title.trim() || !form.start || !form.end) return false
  return new Date(form.end) >= new Date(form.start)
})

const nightsText = computed(() => {
  if (!form.start || !form.end) return ''
  const n = Math.floor((new Date(form.end) - new Date(form.start)) / 86400000)
  return n >= 0 ? `총 ${n}박 ${n + 1}일` : ''
})

async function create() {
  if (creating.value) return
  creating.value = true
  try {
    const newTripId = await tripApi.create({
      title: form.title,
      startDate: form.start,
      endDate: form.end,
      memberCount: count.value,
      defaultTransitMode: defaultTransitMode.value,
    })
    toast.show('새 일정이 생성됐어요!')
    emit('created', { id: newTripId, title: form.title, startDate: form.start, endDate: form.end, memberCount: count.value })
    emit('close')
  } catch (e) {
    toast.show(e.status === 401 ? '로그인이 필요합니다.' : (e.message || '오류가 발생했습니다.'))
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.transit-mode-selector {
  display: flex;
  gap: 8px;
  margin-top: 6px;
}
.transit-mode-btn {
  flex: 1;
  padding: 8px 0;
  border: 1px solid var(--gray-border);
  border-radius: var(--radius-md);
  background: var(--bg-page);
  font-size: 12px;
  font-weight: 500;
  color: var(--gray-dark);
  cursor: pointer;
  transition: background .12s, border-color .12s, color .12s;
  font-family: inherit;
}
.transit-mode-btn:hover {
  border-color: var(--purple-100);
  background: var(--purple-50);
  color: var(--purple-900);
}
.transit-mode-btn.active {
  border-color: var(--purple-900);
  background: var(--purple-50);
  color: var(--purple-900);
  font-weight: 700;
}
</style>
