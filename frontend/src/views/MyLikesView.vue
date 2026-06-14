<template>
  <main id="main">
    <div id="community-layout">
      <div id="community-content">
        <div class="mypage-header">
          <h2 class="mypage-title">좋아요한 글</h2>
        </div>

        <div v-if="loading" class="posts-empty">로딩 중...</div>

        <div v-else-if="!items.length" class="posts-empty">
          좋아요한 글이 없어요.
        </div>

        <div v-else class="post-list">
          <div
            v-for="post in items"
            :key="post.id"
            class="post-card"
            @click="post.deleted ? null : router.push(`/community/${post.id}`)"
            :class="{ 'post-deleted': post.deleted }"
          >
            <div class="post-card-body">
              <p v-if="post.deleted" class="post-deleted-msg">삭제된 글입니다.</p>
              <template v-else>
                <h3 class="post-title">{{ post.title }}</h3>
                <div class="post-meta-row">
                  <span class="post-author">{{ post.authorNickname }}</span>
                  <span class="post-date">{{ formatDate(post.createdAt) }}</span>
                </div>
                <div class="post-stats">
                  <span>👁 {{ post.viewCount }}</span>
                  <span>♥ {{ post.likeCount }}</span>
                  <span>💬 {{ post.commentCount }}</span>
                </div>
              </template>
            </div>
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
import { likeApi } from '@/api/post'
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
    const res = await likeApi.myList({ page: p, size: pageSize })
    items.value = res.items
    total.value = res.total
  } catch {
    items.value = []
  } finally {
    loading.value = false
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
.post-deleted {
  opacity: 0.5;
  cursor: default;
}
.post-deleted-msg {
  color: var(--color-text-muted);
  font-size: 0.9rem;
  padding: 8px 0;
}
</style>
