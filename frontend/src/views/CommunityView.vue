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
          <button class="btn-primary" @click="writeModal = true">✏ 글쓰기</button>
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
              <button class="btn-sm btn-danger" @click="deletePost">삭제</button>
            </div>
          </div>
          <div class="detail-body" style="white-space:pre-wrap">{{ postDetail.content }}</div>
          <div class="like-section">
            <button class="like-btn" :class="{ liked: postDetail.liked }" @click="toggleLike">
              <span class="like-icon">♥</span> {{ postDetail.likeCount }}
            </button>
          </div>
        </article>

        <section class="comment-section">
          <p class="comment-title">댓글 <span class="comment-count">준비 중</span></p>
        </section>
      </div>
    </div>
  </div>
  </main>

  <!-- 글쓰기 모달 -->
  <div v-if="writeModal" id="modal-write" class="modal-overlay" @click.self="writeModal = false">
    <div class="write-modal-box">
      <div class="modal-header">
        <span class="modal-title">새 글 작성</span>
        <button class="modal-close" @click="writeModal = false">✕</button>
      </div>
      <div class="modal-body">
        <label class="field-label"><span class="required">*</span> 제목</label>
        <input class="field-input" v-model="newPost.title" placeholder="제목을 입력하세요" style="margin-bottom:16px" />
        <label class="field-label"><span class="required">*</span> 내용</label>
        <textarea class="field-textarea" v-model="newPost.body" rows="10" placeholder="내용을 입력하세요"></textarea>
      </div>
      <div class="modal-footer">
        <button class="btn-ghost" @click="writeModal = false">취소</button>
        <button class="btn-primary" :disabled="submitting" @click="submitPost">등록</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { useToastStore } from '@/stores/toast'
import { postApi } from '@/api/post'

const toast = useToastStore()

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
const newPost = reactive({ title: '', body: '' })

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

function formatDate(dt) {
  if (!dt) return ''
  const d = new Date(dt)
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

async function openPost(post) {
  selectedPost.value = post
  postDetail.value = null
  try {
    postDetail.value = await postApi.get(post.id)
    post.viewCount = postDetail.value.viewCount
  } catch {
    toast.show('게시글 로드 실패')
    selectedPost.value = null
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

async function deletePost() {
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

async function submitPost() {
  if (!newPost.title.trim() || !newPost.body.trim()) {
    toast.show('제목과 내용을 입력해주세요.')
    return
  }
  submitting.value = true
  try {
    await postApi.create({ title: newPost.title, content: newPost.body })
    newPost.title = newPost.body = ''
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

onMounted(() => {
  loadPosts()
  loadNotices()
})
</script>
