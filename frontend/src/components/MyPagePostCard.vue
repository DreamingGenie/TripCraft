<template>
  <div
    class="post-card"
    :class="{ 'post-deleted': post.deleted }"
    @click="!post.deleted && router.push(`/community/${post.id}`)"
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
    <slot name="actions" />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { formatDate } from '@/utils/format'

defineProps({ post: { type: Object, required: true } })

const router = useRouter()
</script>

<style scoped>
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
