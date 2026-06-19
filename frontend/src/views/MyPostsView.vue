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
          <MyPagePostCard v-for="post in items" :key="post.id" :post="post">
            <template #actions>
              <button class="delete-btn" @click.stop="confirmDelete(post)">삭제</button>
            </template>
          </MyPagePostCard>
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
import { myPostApi, postApi } from '@/api/post'
import { usePostList } from '@/composables/usePostList'
import MyPagePostCard from '@/components/MyPagePostCard.vue'

const { items, page, loading, totalPages, loadPage } = usePostList(myPostApi.list)

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
