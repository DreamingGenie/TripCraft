<template>
  <div id="not-found-page">
    <div class="nf-inner">
      <div class="nf-illustration">🗺️</div>
      <div class="nf-code">404</div>
      <h1 class="nf-title">페이지를 찾을 수 없어요</h1>
      <p class="nf-sub">존재하지 않는 주소이거나 이동된 페이지입니다.</p>
      <div class="nf-actions">
        <RouterLink class="nf-btn-primary" to="/">홈으로 돌아가기</RouterLink>
        <RouterLink class="nf-btn-ghost" to="/explore">관광지 탐색</RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup>
import { RouterLink } from 'vue-router'
</script>

<style scoped>
#not-found-page {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-page);
  padding: 40px;
}

.nf-inner {
  text-align: center;
  max-width: 440px;
}

.nf-illustration {
  font-size: 64px;
  margin-bottom: 8px;
  display: block;
  line-height: 1;
}

.nf-code {
  font-size: 6rem;
  font-weight: 800;
  color: var(--purple-900);
  opacity: 0.18;
  letter-spacing: -0.05em;
  line-height: 1;
  margin-bottom: -8px;
  user-select: none;
}

.nf-title {
  font-size: 1.35rem;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.02em;
  margin-bottom: 10px;
}

.nf-sub {
  font-size: 14px;
  color: var(--gray-muted);
  line-height: 1.6;
  margin-bottom: 32px;
}

.nf-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}

.nf-btn-primary {
  font-size: 13px;
  font-weight: 500;
  color: #fff;
  background: var(--purple-900);
  border: none;
  border-radius: var(--radius-md);
  padding: 10px 22px;
  text-decoration: none;
  transition: opacity .12s;
}
.nf-btn-primary:hover { opacity: 0.88; }

.nf-btn-ghost {
  font-size: 13px;
  color: var(--gray-dark);
  background: var(--bg-surface);
  border: 0.5px solid var(--gray-border);
  border-radius: var(--radius-md);
  padding: 10px 20px;
  text-decoration: none;
  transition: border-color .12s, color .12s;
}
.nf-btn-ghost:hover { border-color: var(--gray-dark); color: var(--text-primary); }
</style>
