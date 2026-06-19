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
    </div>
  </main>
</template>

<script setup>
import { onMounted } from 'vue'
import { bookmarkApi } from '@/api/post'
import { usePostList } from '@/composables/usePostList'
import MyPagePostCard from '@/components/MyPagePostCard.vue'

const { items, page, loading, totalPages, loadPage } = usePostList(bookmarkApi.myList)

onMounted(() => loadPage())
</script>

<style scoped>
@import '@/assets/css/community.css';
</style>
