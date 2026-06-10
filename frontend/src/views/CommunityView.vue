<template>
  <main id="main">
  <div id="community-layout">
    <div id="community-content">
      <div id="view-list">
        <div class="list-toolbar">
          <span class="board-title">여행 이야기</span>
          <div class="sort-group">
            <button v-for="s in sorts" :key="s.value"
                    class="sort-btn" :class="{ active: sort === s.value }"
                    @click="changeSort(s.value)">{{ s.label }}</button>
          </div>
          <button class="btn-primary" @click="openWriteModal">✏ 글쓰기</button>
        </div>

        <!-- 공지 배너 -->
        <!-- 검색창 -->
        <div class="search-bar">
          <input
            class="search-input"
            v-model="keyword"
            placeholder="제목·내용으로 검색"
            maxlength="100"
            @keydown.enter="doSearch"
          />
          <button class="search-btn" @click="doSearch">검색</button>
          <button v-if="keyword" class="search-clear" @click="clearSearch">✕</button>
        </div>

        <div v-if="notices.length" class="notice-banner" @click="router.push(`/community/${notices[0].id}`)">
          <span class="notice-icon">📢</span>
          <span class="notice-text">{{ notices[0].title }}</span>
          <button class="notice-more" @click.stop>더보기 ›</button>
        </div>

        <div v-if="loading" class="posts-empty">로딩 중...</div>
        <div v-else class="posts-list">
          <div v-for="post in posts" :key="post.id"
               class="post-card" @click="router.push(`/community/${post.id}`)">
            <div class="post-card-left">
              <div class="post-meta">
                <div class="avatar avatar-sm">{{ (post.authorNickname || '?')[0] }}</div>
                <span class="post-author">{{ post.authorNickname }}</span>
                <span class="meta-dot">·</span>
                <span class="post-date">{{ formatDate(post.createdAt) }}</span>
              </div>
              <p class="post-title">{{ post.title }}</p>
            </div>
            <div class="post-card-right">
              <div class="post-stats">
                <span class="stat"><span class="stat-icon">♥</span> {{ post.likeCount }}</span>
                <span class="stat"><span class="stat-icon">💬</span> {{ post.commentCount ?? 0 }}</span>
                <span class="stat"><span class="stat-icon">👁</span> {{ post.viewCount }}</span>
              </div>
            </div>
          </div>
          <div v-if="!posts.length" class="posts-empty">
            아직 게시글이 없어요.<br>첫 글을 남겨보세요.
          </div>
        </div>

        <div v-if="totalPages > 1" class="pagination">
          <button class="page-btn" :disabled="page === 0" @click="goPage(page - 1)">‹</button>
          <button v-for="p in totalPages" :key="p"
                  class="page-btn" :class="{ active: page === p - 1 }"
                  @click="goPage(p - 1)">{{ p }}</button>
          <button class="page-btn" :disabled="page >= totalPages - 1" @click="goPage(page + 1)">›</button>
        </div>
      </div>
    </div>
  </div>
  </main>

  <!-- 글쓰기 모달 -->
  <div v-if="writeModal" id="modal-write" class="modal-overlay">
    <div class="write-modal-box">
      <div class="modal-header">
        <span class="modal-title">새 글 작성</span>
        <button class="modal-close" @click="writeModal = false">✕</button>
      </div>

      <!-- 일정 공유 경고 (모달 내부 인라인) -->
      <div v-if="tripShareWarning" class="trip-share-warning">
        <div class="confirm-icon">⚠️</div>
        <p class="confirm-msg">
          이 일정을 공유하면 <strong>다른 사람들이 일정 내용(장소, 날짜 등)을 확인</strong>할 수 있습니다.<br>
          계속 진행하시겠습니까?
        </p>
        <div class="confirm-actions">
          <button class="btn-ghost" @click="tripShareWarning = false">취소</button>
          <button class="btn-primary" :disabled="submitting" @click="doSubmitPost">확인 후 등록</button>
        </div>
      </div>

      <template v-else>
        <div class="modal-body">
          <label class="field-label"><span class="required">*</span> 제목</label>
          <input class="field-input" v-model="newPost.title" placeholder="제목을 입력하세요" style="margin-bottom:16px" />
          <label class="field-label"><span class="required">*</span> 내용</label>
          <TiptapEditor v-model="newPost.body" style="margin-bottom:16px" />
          <label class="field-label">일정 연결 <span class="field-optional">(선택)</span></label>
          <select class="field-input" v-model="newPost.tripId">
            <option :value="null">연결 안 함</option>
            <option v-for="t in myTrips" :key="t.id" :value="t.id">
              {{ t.title }} ({{ t.startDate }} ~ {{ t.endDate }})
            </option>
          </select>
        </div>
        <div class="modal-footer">
          <button class="btn-ghost" @click="writeModal = false">취소</button>
          <button class="btn-primary" :disabled="submitting" @click="submitPost">등록</button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { postApi } from '@/api/post'
import { tripApi } from '@/api/trip'
import { formatDate } from '@/utils/format'
import TiptapEditor from '@/components/TiptapEditor.vue'

const toast   = useToastStore()
const route   = useRoute()
const router  = useRouter()

const sort    = ref('latest')
const sorts   = [{ label: '최신순', value: 'latest' }, { label: '인기순', value: 'popular' }]
const keyword = ref('')

const posts   = ref([])
const notices = ref([])
const total   = ref(0)
const page    = ref(0)
const loading = ref(false)
const PAGE_SIZE = 10

const writeModal      = ref(false)
const submitting      = ref(false)
const newPost         = reactive({ title: '', body: '', tripId: null })
const myTrips         = ref([])
const tripShareWarning = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

async function loadPosts() {
  loading.value = true
  try {
    const data = await postApi.list({ page: page.value, size: PAGE_SIZE, sort: sort.value, keyword: keyword.value })
    posts.value = data.items
    total.value = data.total
  } catch {
    toast.show('게시글 로드 실패')
  } finally {
    loading.value = false
  }
}

function doSearch() {
  page.value = 0
  loadPosts()
}

function clearSearch() {
  keyword.value = ''
  page.value = 0
  loadPosts()
}

async function loadNotices() {
  try { notices.value = await postApi.notices() } catch {}
}

function changeSort(s) {
  sort.value = s
  page.value = 0
  loadPosts()
}

function goPage(p) {
  page.value = p
  loadPosts()
}

async function openWriteModal() {
  newPost.title = ''
  newPost.body  = ''
  newPost.tripId = null
  writeModal.value = true
  try { myTrips.value = await tripApi.list() } catch { myTrips.value = [] }
}

/** Tiptap HTML에서 태그를 제거한 순수 텍스트 추출 */
function extractText(html) {
  const div = document.createElement('div')
  div.innerHTML = html
  return div.textContent?.trim() || ''
}

async function submitPost() {
  if (!newPost.title.trim() || !extractText(newPost.body)) {
    toast.show('제목과 내용을 입력해주세요.')
    return
  }
  if (newPost.tripId) { tripShareWarning.value = true; return }
  await doSubmitPost()
}

async function doSubmitPost() {
  submitting.value = true
  try {
    const body = { title: newPost.title, content: newPost.body }
    if (newPost.tripId) body.tripId = newPost.tripId
    const newId = await postApi.create(body)
    tripShareWarning.value = false
    writeModal.value = false
    toast.show('게시글이 등록됐어요.')
    // 등록된 글 상세 페이지로 바로 이동
    router.push(`/community/${newId}`)
  } catch (e) {
    toast.show(e.status === 401 ? '로그인이 필요합니다.' : '오류가 발생했습니다.')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loadPosts()
  loadNotices()
  // 일정 페이지에서 "공유하기" 버튼으로 진입한 경우 모달 자동 오픈
  if (route.query.shareTrip) {
    await openWriteModal()
    const tripId = Number(route.query.shareTrip)
    if (myTrips.value.some(t => t.id === tripId)) newPost.tripId = tripId
  }
})
</script>

<style scoped>
@import '@/assets/css/community.css';
</style>
