<template>
  <div class="mypage-section">
    <div class="mypage-header">
      <h2 class="mypage-title">좋아요한 글</h2>
    </div>

    <div v-if="loading" class="posts-empty">로딩 중...</div>

    <div v-else-if="!items.length" class="posts-empty">
      좋아요한 글이 없어요.
    </div>

    <div v-else class="posts-list">
      <MyPagePostCard v-for="post in items" :key="post.id" :post="post" />
    </div>

    <div v-if="totalPages > 1" class="pagination">
      <button
        v-for="p in totalPages" :key="p"
        class="page-btn" :class="{ active: p - 1 === page }"
        @click="loadPage(p - 1)"
      >{{ p }}</button>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { likeApi } from '@/api/post'
import { usePostList } from '@/composables/usePostList'
import MyPagePostCard from '@/components/MyPagePostCard.vue'

const { items, page, loading, totalPages, loadPage } = usePostList(likeApi.myList)

onMounted(() => loadPage())
</script>

<style scoped>
@import '@/assets/css/community.css';
</style>
