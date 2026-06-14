<template>
  <main id="main">
    <div id="community-layout">
      <div id="community-content">
        <div class="mypage-header">
          <h2 class="mypage-title">내가 쓴 글</h2>
        </div>

        <div v-if="loading" class="posts-empty">로딩 중...</div>

        <div v-else-if="!items.length" class="posts-empty">
          작성한 글이 없어요.
        </div>

        <div v-else class="post-list">
          <div
            v-for="post in items"
            :key="post.id"
            class="post-card"
            @click="router.push(`/community/${post.id}`)"
          >
            <div class="post-card-body">
              <h3 class="post-title">{{ post.title }}</h3>
              <div class="post-meta-row">
                <span class="post-date">{{ formatDate(post.createdAt) }}</span>
              </div>
              <div class="post-stats">
                <span>👁 {{ post.viewCount }}</span>
                <span>♥ {{ post.likeCount }}</span>
                <span>💬 {{ post.commentCount }}</span>
              </div>
            </div>
            <button class="delete-btn" @click.stop="confirmDelete(post)">삭제</button>
          </div>
        </div>

        <!-- 페이지네이션 -->
        <div v-if="totalPages > 1" class="pagination">
          <button
            v-for="p in totalPages" :key="p"
            class="page-btn" :class="{ active: p - 1 === page }"
            @click="loadPage(p - 1)"
          >{{ p }}</button>
        </div>
      </div>
    </div>
  </main>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { myPostApi, postApi } from '@/api/post'
import { formatDate } from '@/utils/format'

const router = useRouter()

const items      = ref([])
const page       = ref(0)
const total      = ref(0)
const pageSize   = 10
const loading    = ref(false)

const totalPages = computed(() => Math.ceil(total.value / pageSize))

async function loadPage(p = 0) {
  loading.value = true
  page.value = p
  try {
    const res = await myPostApi.list({ page: p, size: pageSize })
    items.value = res.items
    total.value = res.total
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

async function confirmDelete(post) {
  if (!confirm(`"${post.title}" 글을 삭제할까요?`)) return
  try {
    await postApi.delete(post.id)
    await loadPage(page.value)
  } catch {
    alert('삭제에 실패했어요.')
  }
}

onMounted(() => loadPage())
</script>

<style scoped>
@import '@/assets/css/community.css';

.mypage-header {
  padding: 24px 0 16px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 20px;
}
.mypage-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--color-text);
}
.post-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.post-card-body {
  flex: 1;
  min-width: 0;
}
.delete-btn {
  flex-shrink: 0;
  padding: 6px 14px;
  border: 1px solid var(--color-danger, #e53e3e);
  border-radius: 6px;
  background: transparent;
  color: var(--color-danger, #e53e3e);
  font-size: 0.85rem;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.delete-btn:hover {
  background: var(--color-danger, #e53e3e);
  color: #fff;
}
</style>
