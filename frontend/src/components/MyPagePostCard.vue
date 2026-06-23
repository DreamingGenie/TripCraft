<template>
  <div
    class="post-card"
    :class="{ 'post-deleted': post.deleted }"
    @click="!post.deleted && router.push(`/community/${post.id}`)"
  >
    <div class="post-card-left">
      <p v-if="post.deleted" class="post-deleted-msg">삭제된 글입니다.</p>
      <template v-else>
        <div class="post-meta">
          <div class="avatar avatar-sm">
            <img v-if="post.authorProfileImageUrl" :src="post.authorProfileImageUrl" class="avatar-img" alt="" />
            <span v-else>{{ (post.authorNickname || '?')[0] }}</span>
          </div>
          <span class="post-author">{{ post.authorNickname }}</span>
          <span class="meta-dot">·</span>
          <span class="post-date">{{ formatDate(post.createdAt) }}</span>
        </div>
        <p class="post-title">{{ post.title }}</p>
      </template>
    </div>

    <div class="post-card-right">
      <div v-if="!post.deleted" class="post-stats">
        <span class="stat">
          <svg class="stat-icon" viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor"
               stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M20.8 5.6a5.5 5.5 0 0 0-7.8 0L12 6.6l-1-1a5.5 5.5 0 0 0-7.8 7.8L12 22l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z" />
          </svg>
          {{ post.likeCount }}
        </span>
        <span class="stat">
          <svg class="stat-icon" viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor"
               stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 9 9 0 0 1-4-1L3 20l1.1-4A8.4 8.4 0 0 1 12 3a8.4 8.4 0 0 1 9 8.5Z" />
          </svg>
          {{ post.commentCount ?? 0 }}
        </span>
        <span class="stat">
          <svg class="stat-icon" viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor"
               stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M1.5 12S5 5 12 5s10.5 7 10.5 7-3.5 7-10.5 7S1.5 12 1.5 12Z" /><circle cx="12" cy="12" r="3" />
          </svg>
          {{ post.viewCount }}
        </span>
      </div>
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { formatDate } from '@/utils/format'

defineProps({ post: { type: Object, required: true } })

const router = useRouter()
</script>

<style scoped>
/* .post-card / .post-meta / .post-title / .post-stats 등은 community.css(전역)에서 제공 */
.post-deleted {
  opacity: 0.55;
  cursor: default;
}
.post-deleted:hover {
  box-shadow: none;
  border-color: var(--gray-border);
  transform: none;
}
.post-deleted-msg {
  color: var(--gray-muted);
  font-size: 13px;
  padding: 6px 0;
}
.post-card-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
