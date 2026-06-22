<template>
  <main id="main">
  <div id="community-layout">
    <div id="community-content">
      <div id="view-detail">
        <button class="back-btn" @click="router.push('/community')">← 목록으로</button>

        <!-- 에러 상태 (삭제된 글, 잘못된 ID, 권한 없음) -->
        <div v-if="errorState" class="posts-empty">
          <p>{{ errorMessage }}</p>
        </div>

        <div v-else-if="!postDetail" class="posts-empty">로딩 중...</div>
        <article v-else class="detail-article">
          <h2 class="detail-title">{{ postDetail.title }}</h2>
          <div class="detail-meta">
            <div class="avatar avatar-sm">
              <img v-if="postDetail.authorProfileImageUrl" :src="postDetail.authorProfileImageUrl" class="avatar-img" alt="" />
              <span v-else>{{ (postDetail.authorNickname || '?')[0] }}</span>
            </div>
            <div class="detail-meta-info">
              <span class="post-author">{{ postDetail.authorNickname }}</span>
              <span class="detail-date">{{ formatDate(postDetail.createdAt) }}</span>
            </div>
            <div class="detail-actions" v-if="postDetail.mine && auth.isLoggedIn">
              <button class="btn-sm btn-ghost" @click="openEditModal">수정</button>
              <button class="btn-sm btn-danger" @click="deleteConfirm = true">삭제</button>
            </div>
          </div>
          <!-- XSS 방지: DOMPurify로 새니타이징 후 HTML 렌더링 -->
          <div class="detail-body" v-html="sanitize(postDetail.content)"></div>

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
              <div class="trip-card-right-actions">
                <button
                  v-if="auth.isLoggedIn"
                  class="btn-sm btn-copy-trip"
                  @click.stop="openCopyModal">
                  📥 가져오기
                </button>
                <span class="trip-card-toggle">{{ tripSummaryOpen ? '▲' : '▼' }} 일정 보기</span>
              </div>
            </div>

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
            <button class="bookmark-btn" :class="{ bookmarked: postDetail.bookmarked }" @click="toggleBookmark">
              {{ postDetail.bookmarked ? '🔖 북마크됨' : '🔖 북마크' }}
            </button>
          </div>
        </article>

        <!-- 댓글 섹션 — 게시글 로드 성공 시에만 표시 -->
        <section v-if="postDetail" class="comment-section">
          <p class="comment-title">댓글 <span class="comment-count">{{ totalCommentCount }}</span></p>

          <div v-if="comments.length" class="comment-list">
            <div v-for="c in comments" :key="c.id" class="comment-item">
              <div class="comment-header">
                <div class="avatar avatar-sm">
                  <img v-if="c.authorProfileImageUrl" :src="c.authorProfileImageUrl" class="avatar-img" alt="" />
                  <span v-else>{{ (c.authorNickname || '?')[0] }}</span>
                </div>
                <span class="comment-author">{{ c.authorNickname }}</span>
                <span class="comment-date">{{ formatDate(c.createdAt) }}</span>
                <div class="comment-actions">
                  <button class="reply-btn" @click="toggleReply(c.id)">
                    {{ replyInputFor === c.id ? '취소' : '답글' }}
                  </button>
                  <button v-if="c.mine" class="comment-delete" @click="deleteComment(c.id)">삭제</button>
                </div>
              </div>
              <p class="comment-body">{{ c.content }}</p>

              <!-- 대댓글 목록 -->
              <div v-if="c.replies && c.replies.length" class="reply-list">
                <div v-for="r in c.replies" :key="r.id" class="comment-item reply-item">
                  <div class="comment-header">
                    <span class="reply-arrow">↳</span>
                    <div class="avatar avatar-sm">
                      <img v-if="r.authorProfileImageUrl" :src="r.authorProfileImageUrl" class="avatar-img" alt="" />
                      <span v-else>{{ (r.authorNickname || '?')[0] }}</span>
                    </div>
                    <span class="comment-author">{{ r.authorNickname }}</span>
                    <span class="comment-date">{{ formatDate(r.createdAt) }}</span>
                    <button v-if="r.mine" class="comment-delete" @click="deleteComment(r.id)">삭제</button>
                  </div>
                  <p class="comment-body reply-body">{{ r.content }}</p>
                </div>
              </div>

              <!-- 대댓글 입력창 -->
              <div v-if="replyInputFor === c.id" class="reply-form">
                <textarea
                  class="comment-input reply-input"
                  v-model="replyContent"
                  placeholder="답글을 입력하세요 (최대 1000자)"
                  maxlength="1000"
                  rows="2"
                  @keydown.ctrl.enter="submitReply(c.id)"
                ></textarea>
                <button
                  class="btn-primary comment-submit"
                  :disabled="!replyContent.trim() || replySubmitting"
                  @click="submitReply(c.id)">
                  등록
                </button>
              </div>
            </div>
          </div>
          <p v-else class="comment-empty">첫 댓글을 남겨보세요.</p>

          <div class="comment-form">
            <textarea
              class="comment-input"
              v-model="newComment"
              placeholder="댓글을 입력하세요 (최대 1000자)"
              maxlength="1000"
              rows="2"
              @keydown.ctrl.enter="submitComment"
            ></textarea>
            <button class="btn-primary comment-submit"
                    :disabled="!newComment.trim() || commentSubmitting"
                    @click="submitComment">
              등록
            </button>
          </div>
        </section>
      </div>
    </div>
  </div>
  </main>

  <!-- 게시글 수정 모달 -->
  <div v-if="editModal" id="modal-edit" class="modal-overlay">
    <div class="write-modal-box">
      <div class="modal-header">
        <span class="modal-title">게시글 수정</span>
        <button class="modal-close" @click="editModal = false">✕</button>
      </div>
      <div class="modal-body">
        <label class="field-label"><span class="required">*</span> 제목</label>
        <input class="field-input" v-model="editPost.title" placeholder="제목을 입력하세요" style="margin-bottom:16px" />
        <label class="field-label"><span class="required">*</span> 내용</label>
        <TiptapEditor v-model="editPost.content" style="margin-bottom:16px" />
      </div>
      <div class="modal-footer">
        <button class="btn-ghost" @click="editModal = false">취소</button>
        <button class="btn-primary" :disabled="editSubmitting" @click="submitEdit">저장</button>
      </div>
    </div>
  </div>

  <!-- 일정 가져오기 모달 -->
  <div v-if="copyModal" class="modal-overlay" @click.self="copyModal = false">
    <div class="confirm-modal-box copy-modal-box">
      <div class="copy-modal-icon">📥</div>
      <p class="copy-modal-title">일정 가져오기</p>
      <p class="copy-modal-desc">
        내 여행 시작일을 선택하면<br>
        날짜 간격을 유지한 채 새 일정으로 저장돼요.
      </p>
      <div class="copy-modal-field">
        <label class="field-label">여행 시작일</label>
        <input
          type="date"
          class="field-input"
          v-model="copyStartDate"
          :min="today"
        />
      </div>
      <div class="confirm-actions">
        <button class="btn-ghost" @click="copyModal = false">취소</button>
        <button
          class="btn-primary"
          :disabled="!copyStartDate || copySubmitting"
          @click="submitCopyTrip">
          가져오기
        </button>
      </div>
    </div>
  </div>

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
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import { postApi, commentApi, bookmarkApi } from '@/api/post'
import { tripApi } from '@/api/trip'

const today = new Date().toISOString().slice(0, 10)
import { formatDate, formatTripDate } from '@/utils/format'
import DOMPurify from 'dompurify'
import TiptapEditor from '@/components/TiptapEditor.vue'

/** Tiptap HTML을 안전하게 렌더링 — 허용 태그 외 스크립트·이벤트 핸들러 제거 */
function sanitize(html) {
  if (!html) return ''
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'ul', 'ol', 'li', 'img', 'a'],
    ALLOWED_ATTR: ['src', 'alt', 'href', 'target', 'rel'],
  })
}

const toast  = useToastStore()
const auth   = useAuthStore()
const route  = useRoute()
const router = useRouter()

const postId = Number(route.params.id)

const postDetail    = ref(null)
const deleteConfirm = ref(false)
const errorState    = ref(false)
const errorMessage  = ref('')

// ── 수정 모달 ────────────────────────────────────────────────
const editModal     = ref(false)
const editPost      = reactive({ title: '', content: '' })
const editSubmitting = ref(false)

const tripSummaryOpen    = ref(false)
const tripSummaryLoading = ref(false)
const tripSummary        = ref(null)

const comments          = ref([])
const newComment        = ref('')
const commentSubmitting = ref(false)

// ── 대댓글 ──────────────────────────────────────────────────
const replyInputFor   = ref(null)   // 현재 답글 입력창이 열린 댓글 id
const replyContent    = ref('')
const replySubmitting = ref(false)

/** 댓글 + 대댓글 합산 표시용 */
const totalCommentCount = computed(() =>
  comments.value.reduce((sum, c) => sum + 1 + (c.replies?.length ?? 0), 0)
)

// ── 일정 가져오기 ────────────────────────────────────────────
const copyModal      = ref(false)
const copyStartDate  = ref('')
const copySubmitting = ref(false)

function openCopyModal() {
  copyStartDate.value = ''
  copyModal.value = true
}

async function submitCopyTrip() {
  if (!copyStartDate.value) return
  copySubmitting.value = true
  try {
    await tripApi.copy(postDetail.value.tripId, copyStartDate.value)
    copyModal.value = false
    toast.show('내 일정에 저장됐어요! 일정 페이지에서 확인하세요.')
  } catch (e) {
    toast.show(e.status === 401 ? '로그인이 필요합니다.' : '일정 가져오기에 실패했습니다.')
  } finally {
    copySubmitting.value = false
  }
}

// ── 일정 요약 토글 ──────────────────────────────────────────
async function toggleTripSummary() {
  if (tripSummaryOpen.value) { tripSummaryOpen.value = false; return }
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

// ── 북마크 토글 ─────────────────────────────────────────────
async function toggleBookmark() {
  if (!auth.isLoggedIn) { toast.show('로그인이 필요합니다.'); return }
  try {
    await bookmarkApi.toggle(postDetail.value.id)
    postDetail.value.bookmarked = !postDetail.value.bookmarked
  } catch {
    toast.show('오류가 발생했습니다.')
  }
}

// ── 좋아요 토글 ─────────────────────────────────────────────
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

// ── 게시글 수정 ─────────────────────────────────────────────
function openEditModal() {
  editPost.title   = postDetail.value.title
  editPost.content = postDetail.value.content
  editModal.value  = true
}

/** Tiptap HTML에서 태그를 제거한 순수 텍스트 추출 */
function extractText(html) {
  const div = document.createElement('div')
  div.innerHTML = html
  return div.textContent?.trim() || ''
}

async function submitEdit() {
  if (!editPost.title.trim() || !extractText(editPost.content)) {
    toast.show('제목과 내용을 입력해주세요.')
    return
  }
  editSubmitting.value = true
  try {
    await postApi.update(postId, { title: editPost.title, content: editPost.content })
    // 상세 데이터 갱신 — 수정된 제목·내용 즉시 반영
    postDetail.value.title   = editPost.title
    postDetail.value.content = editPost.content
    editModal.value = false
    toast.show('게시글이 수정됐어요.')
  } catch (e) {
    toast.show(e.status === 403 ? '수정 권한이 없습니다.' : '수정에 실패했습니다.')
  } finally {
    editSubmitting.value = false
  }
}

// ── 게시글 삭제 ─────────────────────────────────────────────
async function confirmDeletePost() {
  deleteConfirm.value = false
  if (!postDetail.value) return
  try {
    await postApi.delete(postDetail.value.id)
    toast.show('게시글이 삭제됐어요.')
    router.push('/community')
  } catch (e) {
    toast.show(e.message || '삭제 실패')
  }
}

// ── 댓글 등록 ───────────────────────────────────────────────
async function submitComment() {
  if (!newComment.value.trim()) return
  commentSubmitting.value = true
  try {
    await commentApi.create(postId, newComment.value.trim())
    newComment.value = ''
    comments.value = await commentApi.list(postId)
  } catch (e) {
    toast.show(e.status === 401 ? '로그인이 필요합니다.' : '댓글 등록 실패')
  } finally {
    commentSubmitting.value = false
  }
}

// ── 대댓글 입력 토글 ─────────────────────────────────────────
function toggleReply(commentId) {
  if (!auth.isLoggedIn) {
    toast.show('로그인이 필요합니다.')
    return
  }
  if (replyInputFor.value === commentId) {
    replyInputFor.value = null
    replyContent.value = ''
  } else {
    replyInputFor.value = commentId
    replyContent.value = ''
    nextTick(() => {
      document.querySelector('.reply-input')?.focus()
    })
  }
}

// ── 대댓글 등록 ─────────────────────────────────────────────
async function submitReply(parentId) {
  if (!replyContent.value.trim()) return
  replySubmitting.value = true
  try {
    await commentApi.create(postId, replyContent.value.trim(), parentId)
    replyContent.value = ''
    replyInputFor.value = null
    comments.value = await commentApi.list(postId)
  } catch (e) {
    toast.show(e.status === 401 ? '로그인이 필요합니다.' : '답글 등록 실패')
  } finally {
    replySubmitting.value = false
  }
}

// ── 댓글 삭제 ───────────────────────────────────────────────
async function deleteComment(commentId) {
  try {
    await commentApi.delete(postId, commentId)
    comments.value = comments.value.filter(c => c.id !== commentId)
  } catch (e) {
    toast.show(e.status === 401 ? '로그인이 필요합니다.' : '댓글 삭제 실패')
  }
}

// ── 초기 로드 ───────────────────────────────────────────────
onMounted(async () => {
  // URL 파라미터 유효성 검증
  if (isNaN(postId) || postId <= 0) {
    errorState.value = true
    errorMessage.value = '올바르지 않은 게시글 주소입니다.'
    return
  }
  try {
    ;[postDetail.value, comments.value] = await Promise.all([
      postApi.get(postId),
      commentApi.list(postId),
    ])
  } catch (e) {
    errorState.value = true
    if (e.status === 404) {
      errorMessage.value = '존재하지 않거나 삭제된 게시글입니다.'
    } else if (e.status === 403) {
      errorMessage.value = '접근 권한이 없는 게시글입니다.'
    } else {
      errorMessage.value = '게시글을 불러올 수 없습니다.'
    }
  }
})
</script>

<style scoped>
@import '@/assets/css/community.css';
</style>
