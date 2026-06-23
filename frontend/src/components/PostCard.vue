<template>
  <article
    class="story-card"
    :class="{ 'story-deleted': post.deleted }"
    @click="!post.deleted && router.push(`/community/${post.id}`)"
  >
    <template v-if="post.deleted">
      <div class="story-deleted-body">
        <p class="story-deleted-msg">삭제된 글입니다.</p>
      </div>
    </template>

    <template v-else>
      <div class="story-cover" :class="{ 'is-placeholder': !post.coverImageUrl }"
           :style="!post.coverImageUrl ? placeholderStyle(post.id) : null">
        <img v-if="post.coverImageUrl" :src="post.coverImageUrl" :alt="post.title" loading="lazy" />
        <svg v-else class="story-cover-glyph" viewBox="0 0 24 24" width="40" height="40" fill="none"
             stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="9" cy="9" r="2" />
          <path d="m21 15-3.6-3.6a2 2 0 0 0-2.8 0L6 20" />
        </svg>
        <span v-if="post.tripId" class="story-trip-badge">
          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor"
               stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z" /><circle cx="12" cy="10" r="3" />
          </svg>
          일정
        </span>
      </div>
      <div class="story-body">
        <h3 class="story-title">{{ post.title }}</h3>
        <div class="story-meta">
          <div class="avatar avatar-sm">
            <img v-if="post.authorProfileImageUrl" :src="post.authorProfileImageUrl" class="avatar-img" alt="" />
            <span v-else>{{ (post.authorNickname || '?')[0] }}</span>
          </div>
          <span class="post-author">{{ post.authorNickname }}</span>
          <span class="meta-dot">·</span>
          <span class="post-date">{{ formatDate(post.createdAt) }}</span>
        </div>
        <div class="story-stats">
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
          <span class="story-actions" @click.stop>
            <slot name="actions" />
          </span>
        </div>
      </div>
    </template>
  </article>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { formatDate } from '@/utils/format'

defineProps({ post: { type: Object, required: true } })

const router = useRouter()

// 커버 이미지가 없을 때 글 id 기반 분류색 그라데이션 플레이스홀더
const PLACEHOLDER_GRADIENTS = [
  'linear-gradient(135deg, #6b62cf, #534ab7)',
  'linear-gradient(135deg, #f0a868, #d97742)',
  'linear-gradient(135deg, #5fa8d3, #2c7da0)',
  'linear-gradient(135deg, #7cb083, #4a8c5a)',
  'linear-gradient(135deg, #c97b9e, #993556)',
  'linear-gradient(135deg, #e0b94a, #c19320)',
]
function placeholderStyle(id) {
  const g = PLACEHOLDER_GRADIENTS[(Number(id) || 0) % PLACEHOLDER_GRADIENTS.length]
  return { background: g }
}
</script>

<style scoped>
/* .story-card / .story-cover / .story-body 등은 community.css(전역)에서 제공 */
.story-deleted {
  opacity: 0.55;
  cursor: default;
}
.story-deleted:hover {
  box-shadow: var(--shadow-sm);
  border-color: transparent;
  transform: none;
}
.story-deleted-body {
  padding: 40px 16px;
  text-align: center;
}
.story-deleted-msg {
  color: var(--gray-muted);
  font-size: 13px;
  margin: 0;
}
.story-stats {
  position: relative;
}
.story-actions {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
}
</style>
