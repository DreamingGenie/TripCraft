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

      <div style="margin-bottom:20px">
        <label class="modal-label">여행 인원 (선택)</label>
        <div class="count-row">
          <button class="count-btn" @click="count = Math.max(1, count - 1)">−</button>
          <span class="count-val">{{ count }}명</span>
          <button class="count-btn" @click="count = Math.min(20, count + 1)">+</button>
        </div>
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
import { useRouter } from 'vue-router'
import { tripApi } from '@/api/trip'

const emit = defineEmits(['close'])
const toast = useToastStore()
const router = useRouter()

const form = reactive({ title: '', start: '', end: '' })
const count = ref(2)
const creating = ref(false)

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
    await tripApi.create({
      title: form.title,
      startDate: form.start,
      endDate: form.end,
      memberCount: count.value,
    })
    toast.show('새 일정이 생성됐어요!')
    emit('close')
    router.push('/schedule')
  } catch (e) {
    toast.show(e.status === 401 ? '로그인이 필요합니다.' : (e.message || '오류가 발생했습니다.'))
  } finally {
    creating.value = false
  }
}
</script>
