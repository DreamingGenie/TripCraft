<template>
  <!-- 근거: 에디터·이미지·표지 큰 작업은 모달보다 전용 화면이 집중·공간·뒤로가기·링크공유에 유리 -->
  <main id="main">
  <div id="community-layout">
    <div id="community-content">
      <div id="view-write">
        <button class="back-btn" @click="goBack">← {{ isEdit ? '돌아가기' : '목록으로' }}</button>

        <header class="write-header">
          <h1 class="write-heading">{{ isEdit ? '글 수정' : '새 여행 이야기' }}</h1>
        </header>

        <div v-if="loadError" class="posts-empty">{{ loadError }}</div>

        <form v-else class="write-form" @submit.prevent="onSubmit">
          <label class="field-label"><span class="required">*</span> 제목</label>
          <input class="field-input write-field" v-model="form.title" placeholder="제목을 입력하세요" />

          <label class="field-label">대표사진 <span class="field-optional">(선택)</span></label>
          <div class="cover-uploader write-field">
            <div v-if="form.coverImageUrl" class="cover-preview">
              <img :src="form.coverImageUrl" alt="대표사진 미리보기" />
              <button type="button" class="cover-remove" @click="removeCover" aria-label="대표사진 제거">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
                     stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M18 6 6 18M6 6l12 12" />
                </svg>
              </button>
            </div>
            <button v-else type="button" class="cover-select" :disabled="coverUploading"
                    @click="coverInput?.click()">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
                   stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="9" cy="9" r="2" />
                <path d="m21 15-3.6-3.6a2 2 0 0 0-2.8 0L6 20" />
              </svg>
              <span>{{ coverUploading ? '업로드 중…' : '대표사진 추가' }}</span>
            </button>
            <input ref="coverInput" type="file" accept="image/jpeg,image/png,image/gif,image/webp"
                   style="display:none" @change="handleCoverUpload" />
          </div>

          <label class="field-label"><span class="required">*</span> 내용</label>
          <TiptapEditor v-model="form.body" class="write-field" />

          <label class="field-label">일정 연결 <span class="field-optional">(선택)</span></label>
          <select class="field-input write-field" v-model="form.tripId">
            <option :value="null">연결 안 함</option>
            <option v-for="t in myTrips" :key="t.id" :value="t.id">
              {{ t.title }} ({{ t.startDate }} ~ {{ t.endDate }})
            </option>
          </select>

          <!-- 일정 공유 경고: select 아래 인라인 노출 -->
          <div v-if="form.tripId" class="write-trip-warn">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
                 stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z" />
              <path d="M12 9v4M12 17h.01" />
            </svg>
            <span>이 일정을 공유하면 다른 사람들이 일정 내용(장소·날짜 등)을 확인할 수 있습니다.</span>
          </div>

          <div class="write-actions">
            <button type="button" class="btn-ghost" @click="goBack">취소</button>
            <button type="submit" class="btn-primary" :disabled="submitting">
              {{ isEdit ? '저장' : '등록' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
  </main>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { postApi, imageApi } from '@/api/post'
import { tripApi } from '@/api/trip'
import TiptapEditor from '@/components/TiptapEditor.vue'

const toast  = useToastStore()
const route  = useRoute()
const router = useRouter()

// 라우트 파라미터 id 유무로 작성/수정 공용 분기
const editId = computed(() => (route.params.id ? Number(route.params.id) : null))
const isEdit = computed(() => editId.value != null)

const form = reactive({ title: '', body: '', tripId: null, coverImageUrl: '' })
const myTrips        = ref([])
const submitting     = ref(false)
const coverInput     = ref(null)
const coverUploading = ref(false)
const loadError      = ref('')

function goBack() {
  // 수정이면 해당 글 상세로, 작성이면 목록으로
  router.push(isEdit.value ? `/community/${editId.value}` : '/community')
}

// ── 대표사진 업로드 (Tiptap 이미지와 동일 엔드포인트 재사용, type=cover) ──
async function handleCoverUpload(e) {
  const file = e.target.files[0]
  if (!file) return
  e.target.value = '' // 같은 파일 재선택 허용
  coverUploading.value = true
  try {
    // type='cover' → 서버가 post_cover_draft로 저장(회원당 1장, 기존 교체)
    form.coverImageUrl = await imageApi.upload(file, 'cover')
  } catch (err) {
    toast.show(err.message || '대표사진 업로드에 실패했습니다.')
  } finally {
    coverUploading.value = false
  }
}

async function removeCover() {
  try { await imageApi.deleteCoverDraft() } catch {}
  form.coverImageUrl = ''
}

/** Tiptap HTML에서 태그를 제거한 순수 텍스트 추출 */
function extractText(html) {
  const div = document.createElement('div')
  div.innerHTML = html
  return div.textContent?.trim() || ''
}

async function onSubmit() {
  if (!form.title.trim() || !extractText(form.body)) {
    toast.show('제목과 내용을 입력해주세요.')
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      // coverImageUrl는 서버가 draft에서 읽으므로 본문/제목/일정만 전달 (기존 흐름 유지)
      await postApi.update(editId.value, { title: form.title, content: form.body })
      toast.show('게시글이 수정됐어요.')
      router.push(`/community/${editId.value}`)
    } else {
      const body = { title: form.title, content: form.body }
      if (form.tripId) body.tripId = form.tripId
      const newId = await postApi.create(body)
      toast.show('게시글이 등록됐어요.')
      router.push(`/community/${newId}`)
    }
  } catch (e) {
    if (e.status === 401) toast.show('로그인이 필요합니다.')
    else if (e.status === 403) toast.show('수정 권한이 없습니다.')
    else toast.show('오류가 발생했습니다.')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  if (isEdit.value) {
    // 수정: 기존 글 프리필 (cover는 draft 흐름이라 미리보기로만 노출)
    try {
      const p = await postApi.get(editId.value)
      form.title = p.title
      form.body  = p.content
      form.tripId = p.tripId ?? null
      form.coverImageUrl = p.coverImageUrl || ''
    } catch (e) {
      loadError.value = e.status === 403 ? '수정 권한이 없습니다.' : '게시글을 불러올 수 없습니다.'
      return
    }
  } else {
    // 작성: 이전에 올렸다가 등록하지 않은 대표사진 draft 초기화
    try { await imageApi.deleteCoverDraft() } catch {}
  }
  try { myTrips.value = await tripApi.list() } catch { myTrips.value = [] }
  // 일정 페이지 "공유하기" 진입 시 일정 자동 선택
  if (!isEdit.value && route.query.shareTrip) {
    const tripId = Number(route.query.shareTrip)
    if (myTrips.value.some(t => t.id === tripId)) form.tripId = tripId
  }
})
</script>

<style scoped>
@import '@/assets/css/community.css';

/* 전용 작성/수정 화면 — 콘텐츠 페이지 중앙 폭(~720) 안에서 세로 폼 */
.write-header { margin-bottom: 20px; }
.write-heading {
  font-size: var(--text-2xl);
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--text-primary);
  margin: 0;
}
.write-form { display: flex; flex-direction: column; }
.write-field { margin-bottom: 18px; }
.write-trip-warn {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  margin: -6px 0 18px;
  background: var(--amber-50, #fff8ec);
  border: 1px solid var(--amber-100, #f5e0bd);
  border-radius: var(--radius-md);
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--text-secondary);
}
.write-trip-warn svg { color: var(--amber-600); flex-shrink: 0; margin-top: 1px; }
.write-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 8px;
}
</style>
