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
          <button class="btn-primary" @click="router.push('/community/write')">
            <svg class="ic" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
                 stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M12 20h9" /><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" />
            </svg>
            글쓰기
          </button>
        </div>

        <!-- 공지 배너 -->
        <!-- 검색창: 전체폭 단일 필드(좌측 아이콘·우측 clear), Enter로 검색 → 형제 요소와 폭 통일 -->
        <div class="search-bar">
          <svg class="search-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <circle cx="11" cy="11" r="7" /><path d="m21 21-4.3-4.3" />
          </svg>
          <input
            class="search-input"
            v-model="keyword"
            placeholder="제목·내용으로 검색"
            maxlength="100"
            @keydown.enter="doSearch"
          />
          <button v-if="keyword" class="search-clear" @click="clearSearch" aria-label="검색어 지우기">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M18 6 6 18M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div v-if="notices.length" class="notice-banner" @click="router.push(`/community/${notices[0].id}`)">
          <svg class="notice-icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
               stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m3 11 18-5v12L3 14v-3z" /><path d="M11.6 16.8a3 3 0 1 1-5.8-1.6" />
          </svg>
          <span class="notice-text">{{ notices[0].title }}</span>
          <button class="notice-more" @click.stop>더보기 ›</button>
        </div>

        <div v-if="loading" class="posts-empty">로딩 중...</div>
        <div v-else-if="!posts.length" class="posts-empty">
          아직 게시글이 없어요.<br>첫 글을 남겨보세요.
        </div>
        <div v-else class="posts-grid">
          <PostCard v-for="post in posts" :key="post.id" :post="post" />
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
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { postApi } from '@/api/post'
import PostCard from '@/components/PostCard.vue'

const toast   = useToastStore()
const route   = useRoute()
const router  = useRouter()

const sort    = ref(route.query.sort    || 'latest')
const sorts   = [{ label: '최신순', value: 'latest' }, { label: '인기순', value: 'popular' }]
const keyword = ref(route.query.keyword || '')

const posts   = ref([])
const notices = ref([])
const total   = ref(0)
const page    = ref(Number(route.query.page) || 0)
const loading = ref(false)
const PAGE_SIZE = 12 // 1·2·3열 공배수 → 한 페이지가 완전한 줄로 채워짐

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

function syncUrl() {
  const query = {}
  if (keyword.value) query.keyword = keyword.value
  if (sort.value && sort.value !== 'latest') query.sort = sort.value
  if (page.value > 0) query.page = page.value
  router.replace({ query })
}

function doSearch() {
  page.value = 0
  syncUrl()
  loadPosts()
}

function clearSearch() {
  keyword.value = ''
  page.value = 0
  syncUrl()
  loadPosts()
}

async function loadNotices() {
  try { notices.value = await postApi.notices() } catch {}
}

function changeSort(s) {
  sort.value = s
  page.value = 0
  syncUrl()
  loadPosts()
}

function goPage(p) {
  page.value = p
  syncUrl()
  loadPosts()
}

onMounted(() => {
  loadPosts()
  loadNotices()
  // 일정 페이지에서 "공유하기" 진입 시 전용 작성 화면으로 일정 id 전달(모달 폐지)
  if (route.query.shareTrip) {
    router.replace(`/community/write?shareTrip=${Number(route.query.shareTrip)}`)
  }
})
</script>

<style scoped>
@import '@/assets/css/community.css';
</style>
