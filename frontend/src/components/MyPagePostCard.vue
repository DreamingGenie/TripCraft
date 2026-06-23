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
        <span class="stat"><span class="stat-icon">♥</span> {{ post.likeCount }}</span>
        <span class="stat"><span class="stat-icon">💬</span> {{ post.commentCount ?? 0 }}</span>
        <span class="stat"><span class="stat-icon">👁</span> {{ post.viewCount }}</span>
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
