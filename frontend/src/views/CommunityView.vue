<template>
  <main id="main">
  <div id="community-layout">
    <div id="community-content">

      <!-- 목록 뷰 -->
      <div v-if="!selectedPost" id="view-list">
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
        <div v-if="notices.length" class="notice-banner" @click="openPost(notices[0])">
          <span class="notice-icon">📢</span>
          <span class="notice-text">{{ notices[0].title }}</span>
          <button class="notice-more" @click.stop>더보기 ›</button>
        </div>

        <div v-if="loading" class="posts-empty">로딩 중...</div>
        <div v-else class="posts-list">
          <div v-for="post in posts" :key="post.id"
               class="post-card" @click="openPost(post)">
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

      <!-- 상세 뷰 -->
      <div v-else id="view-detail">
        <button class="back-btn" @click="selectedPost = null; postDetail = null">← 목록으로</button>

        <div v-if="!postDetail" class="posts-empty">로딩 중...</div>
        <article v-else class="detail-article">
          <h2 class="detail-title">{{ postDetail.title }}</h2>
          <div class="detail-meta">
            <div class="avatar avatar-sm">{{ (postDetail.authorNickname || '?')[0] }}</div>
            <div class="detail-meta-info">
              <span class="post-author">{{ postDetail.authorNickname }}</span>
              <span class="detail-date">{{ formatDate(postDetail.createdAt) }}</span>
            </div>
            <div class="detail-actions" v-if="postDetail.mine">
              <button class="btn-sm btn-danger" @click="deleteConfirm = true">삭제</button>
            </div>
          </div>
          <div class="detail-body" style="white-space:pre-wrap">{{ postDetail.content }}</div>

          <!-- 연결된 일정 카드 -->
          <div v-if="postDetail.tripId" class="trip-card">
            <div class="trip-card-header" @click="toggleTripSummary">
              <div class="trip-card-left">
                <div class="trip-card-icon">🗓</div>
                <div class="trip-card-info">
                  <span class="trip-card-title">{{ postDetail.tripTitle }}</span>
                  <span class="trip-card-meta">
                    {{ postDetail.tripStartDate }} ~ {{ postDetail.tripEndDate }}
                    · {{ postDetail.tripMemberCount }}명
                  </span>
                </div>
              </div>
              <span class="trip-card-toggle">{{ tripSummaryOpen ? '▲' : '▼' }} 일정 보기</span>
            </div>

            <!-- 펼쳐진 일정 -->
            <div v-if="tripSummaryOpen" class="trip-summary">
              <div v-if="tripSummaryLoading" class="trip-summary-loading">로딩 중...</div>
              <template v-else-if="tripSummary">
                <div v-for="day in tripSummary.days" :key="day.date" class="trip-day">
                  <div class="trip-day-label">📅 {{ formatTripDate(day.date) }}</div>
                  <div v-if="day.blocks.length" class="trip-day-blocks">
                    <div v-for="(block, i) in day.blocks" :key="i" class="trip-block-item">
                      <span class="trip-block-time">{{ block.startTime ? block.startTime.slice(0,5) : '--:--' }}</span>
                      <span class="trip-block-name">{{ block.attractionName }}</span>
                      <span class="trip-block-duration">{{ block.durationMinutes }}분</span>
                    </div>
                  </div>
                  <div v-else class="trip-day-empty">일정 없음</div>
                </div>
              </template>
              <div v-else class="trip-summary-loading">일정 정보를 불러올 수 없습니다.</div>
            </div>
          </div>

          <div class="like-section">
            <button class="like-btn" :class="{ liked: postDetail.liked }" @click="toggleLike">
              <span class="like-icon">♥</span> {{ postDetail.likeCount }}
            </button>
          </div>
        </article>

        <section class="comment-section">
          <p class="comment-title">댓글 <span class="comment-count">{{ comments.length }}</span></p>

          <!-- 댓글 목록 -->
          <div v-if="comments.length" class="comment-list">
            <div v-for="c in comments" :key="c.id" class="comment-item">
              <div class="comment-header">
                <div class="avatar avatar-sm">{{ (c.authorNickname || '?')[0] }}</div>
                <span class="comment-author">{{ c.authorNickname }}</span>
                <span class="comment-date">{{ formatDate(c.createdAt) }}</span>
                <button v-if="c.mine" class="comment-delete" @click="deleteComment(c.id)">삭제</button>
              </div>
              <p class="comment-body">{{ c.content }}</p>
            </div>
          </div>
          <p v-else class="comment-empty">첫 댓글을 남겨보세요.</p>

          <!-- 댓글 입력 -->
          <div class="comment-form">
            <textarea
              class="comment-input"
              v-model="newComment"
              placeholder="댓글을 입력하세요 (최대 1000자)"
              maxlength="1000"
              rows="2"
              @keydown.ctrl.enter="submitComment"
            ></textarea>
            <button class="btn-primary comment-submit" :disabled="!newComment.trim() || commentSubmitting" @click="submitComment">
              등록
            </button>
          </div>
        </section>
      </div>
    </div>
  </div>
  </main>

  <!-- 게시글 삭제 확인 팝업 -->
  <div v-if="deleteConfirm" class="modal-overlay" @click.self="deleteConfirm = false">
    <div class="confirm-modal-box">
      <div class="confirm-icon">🗑</div>
      <p class="confirm-msg">
        게시글을 삭제하면 <strong>공유된 일정도 더 이상 확인할 수 없게 됩니다.</strong><br>
        정말 삭제하시겠습니까?
      </p>
      <div class="confirm-actions">
        <button class="btn-ghost" @click="deleteConfirm = false">취소</button>
        <button class="btn-danger" @click="confirmDeletePost">삭제</button>
      </div>
    </div>
  </div>

  <!-- 글쓰기 모달 -->
  <div v-if="writeModal" id="modal-write" class="modal-overlay" @click.self="writeModal = false">
    <div class="write-modal-box">
      <div class="modal-header">
        <span class="modal-title">새 글 작성</span>
        <button class="modal-close" @click="writeModal = false">✕</button>
      </div>

      <!-- 일정 공유 경고 (모달 내부 인라인) -->
      <div v-if="tripShareWarning" class="trip-share-warning">
        <div class="confirm-icon">⚠️</div>
        <p class="confirm-msg">이 일정을 공유하면 <strong>다른 사람들이 일정 내용(장소, 날짜 등)을 확인</strong>할 수 있습니다.<br>계속 진행하시겠습니까?</p>
        <div class="confirm-actions">
          <button class="btn-ghost" @click="cancelTripSelect">취소</button>
          <button class="btn-primary" :disabled="submitting" @click="doSubmitPost">확인 후 등록</button>
        </div>
      </div>

      <template v-else>
        <div class="modal-body">
          <label class="field-label"><span class="required">*</span> 제목</label>
          <input class="field-input" v-model="newPost.title" placeholder="제목을 입력하세요" style="margin-bottom:16px" />
          <label class="field-label"><span class="required">*</span> 내용</label>
          <textarea class="field-textarea" v-model="newPost.body" rows="8" placeholder="내용을 입력하세요" style="margin-bottom:16px"></textarea>
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
import { useRoute } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { postApi, commentApi } from '@/api/post'
import { tripApi } from '@/api/trip'

const toast = useToastStore()
const route = useRoute()

const sort = ref('latest')
const sorts = [{ label: '최신순', value: 'latest' }, { label: '인기순', value: 'popular' }]

const posts = ref([])
const notices = ref([])
const total = ref(0)
const page = ref(0)
const loading = ref(false)
const PAGE_SIZE = 10

const selectedPost = ref(null)
const postDetail = ref(null)
const writeModal = ref(false)
const submitting = ref(false)
const newPost = reactive({ title: '', body: '', tripId: null })
const myTrips = ref([])
const tripShareWarning = ref(false)
const deleteConfirm = ref(false)

// 일정 요약 펼치기
const tripSummaryOpen = ref(false)
const tripSummaryLoading = ref(false)
const tripSummary = ref(null)

// 댓글
const comments = ref([])
const newComment = ref('')
const commentSubmitting = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

function formatDate(dt) {
  if (!dt) return ''
  // LocalDateTime은 타임존 없이 직렬화되므로 UTC로 명시
  const d = new Date(dt.includes('Z') || dt.includes('+') ? dt : dt + 'Z')
  const diff = Date.now() - d.getTime()
  if (diff < 60000) return '방금'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}분 전`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}시간 전`
  return `${d.getMonth() + 1}/${d.getDate()}`
}

async function loadPosts() {
  loading.value = true
  try {
    const data = await postApi.list({ page: page.value, size: PAGE_SIZE, sort: sort.value })
    posts.value = data.items
    total.value = data.total
  } catch {
    toast.show('게시글 로드 실패')
  } finally {
    loading.value = false
  }
}

async function loadNotices() {
  try {
    notices.value = await postApi.notices()
  } catch {}
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

async function toggleTripSummary() {
  if (tripSummaryOpen.value) {
    tripSummaryOpen.value = false
    return
  }
  tripSummaryOpen.value = true
  if (tripSummary.value) return
  tripSummaryLoading.value = true
  try {
    tripSummary.value = await tripApi.getBlocksSummary(postDetail.value.tripId)
  } catch {
    tripSummary.value = null
  } finally {
    tripSummaryLoading.value = false
  }
}

function formatTripDate(dateStr) {
  const d = new Date(dateStr + 'T00:00:00')
  return `${d.getMonth() + 1}월 ${d.getDate()}일`
}

async function openPost(post) {
  selectedPost.value = post
  postDetail.value = null
  comments.value = []
  newComment.value = ''
  tripSummaryOpen.value = false
  tripSummary.value = null
  try {
    // 게시글 상세와 댓글 목록을 병렬 조회
    ;[postDetail.value, comments.value] = await Promise.all([
      postApi.get(post.id),
      commentApi.list(post.id),
    ])
    post.viewCount = postDetail.value.viewCount
  } catch {
    toast.show('게시글 로드 실패')
    selectedPost.value = null
  }
}

async function submitComment() {
  if (!newComment.value.trim()) return
  commentSubmitting.value = true
  try {
    await commentApi.create(postDetail.value.id, newComment.value.trim())
    newComment.value = ''
    comments.value = await commentApi.list(postDetail.value.id)
  } catch (e) {
    toast.show(e.status === 401 ? '로그인이 필요합니다.' : '댓글 등록 실패')
  } finally {
    commentSubmitting.value = false
  }
}

async function deleteComment(commentId) {
  try {
    await commentApi.delete(postDetail.value.id, commentId)
    comments.value = comments.value.filter(c => c.id !== commentId)
  } catch (e) {
    toast.show(e.status === 401 ? '로그인이 필요합니다.' : '댓글 삭제 실패')
  }
}

async function toggleLike() {
  if (!postDetail.value) return
  try {
    await postApi.toggleLike(postDetail.value.id)
    postDetail.value.liked = !postDetail.value.liked
    postDetail.value.likeCount += postDetail.value.liked ? 1 : -1
  } catch (e) {
    toast.show(e.status === 401 ? '로그인이 필요합니다.' : '오류가 발생했습니다.')
  }
}

async function confirmDeletePost() {
  deleteConfirm.value = false
  if (!postDetail.value) return
  try {
    await postApi.delete(postDetail.value.id)
    toast.show('게시글이 삭제됐어요.')
    selectedPost.value = null
    postDetail.value = null
    loadPosts()
  } catch (e) {
    toast.show(e.message || '삭제 실패')
  }
}

function cancelTripSelect() {
  tripShareWarning.value = false
}

async function openWriteModal() {
  newPost.title = ''
  newPost.body = ''
  newPost.tripId = null
  writeModal.value = true
  try {
    myTrips.value = await tripApi.list()
  } catch {
    myTrips.value = []
  }
}

async function submitPost() {
  if (!newPost.title.trim() || !newPost.body.trim()) {
    toast.show('제목과 내용을 입력해주세요.')
    return
  }
  // 일정이 선택된 경우 경고 확인 먼저
  if (newPost.tripId) {
    tripShareWarning.value = true
    return
  }
  await doSubmitPost()
}

async function doSubmitPost() {
  submitting.value = true
  try {
    const body = { title: newPost.title, content: newPost.body }
    if (newPost.tripId) body.tripId = newPost.tripId
    await postApi.create(body)
    newPost.title = newPost.body = ''
    newPost.tripId = null
    tripShareWarning.value = false
    writeModal.value = false
    toast.show('게시글이 등록됐어요.')
    page.value = 0
    loadPosts()
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
    if (myTrips.value.some(t => t.id === tripId)) {
      newPost.tripId = tripId
      // 경고는 등록 버튼 클릭 시 표시 — 여기서는 선택만 해둠
    }
  }
})
</script>

<style scoped>
@import '@/assets/css/community.css';
</style>
