<template>
  <main id="main">
    <div id="community-layout">
      <div id="community-content">
        <div class="mypage-header">
          <h2 class="mypage-title">북마크한 글</h2>
        </div>

        <div v-if="loading" class="posts-empty">로딩 중...</div>

        <div v-else-if="!items.length" class="posts-empty">
          북마크한 글이 없어요.
        </div>

        <div v-else class="post-list">
          <div
            v-for="post in items"
            :key="post.id"
            class="post-card"
            :class="{ 'post-deleted': post.deleted }"
            @click="post.deleted ? null : router.push(`/community/${post.id}`)"
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
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { bookmarkApi } from '@/api/post'
import { formatDate } from '@/utils/format'
import { usePostList } from '@/composables/usePostList'

const router = useRouter()
const { items, page, loading, totalPages, loadPage } = usePostList(bookmarkApi.myList)

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
