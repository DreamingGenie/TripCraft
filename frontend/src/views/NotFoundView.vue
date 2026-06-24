<template>
  <div id="not-found-page">
    <div class="nf-inner">
      <div class="nf-illustration">
        <svg width="56" height="56" viewBox="0 0 56 56" fill="none">
          <path d="M21 11L7 16V45L21 40L35 45L49 40V11L35 16L21 11Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/>
          <path d="M21 11V40M35 16V45" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/>
          <circle cx="28" cy="25" r="3.5" stroke="currentColor" stroke-width="2"/>
        </svg>
      </div>
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
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 96px;
  height: 96px;
  margin-bottom: var(--space-2);
  border-radius: var(--radius-2xl);
  background: var(--bg-surface);
  color: var(--purple-900);
  box-shadow: var(--shadow-md);
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
  font-size: var(--text-2xl);
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.02em;
  margin-bottom: var(--space-3);
}

.nf-sub {
  font-size: var(--text-base);
  color: var(--gray-muted);
  line-height: 1.6;
  margin-bottom: var(--space-6);
}

.nf-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}

.nf-btn-primary {
  font-size: var(--text-sm);
  font-weight: 500;
  color: #fff;
  background: var(--purple-900);
  border: none;
  border-radius: var(--radius-md);
  padding: 11px 22px;
  text-decoration: none;
  box-shadow: var(--shadow-sm);
  transition: opacity .12s, box-shadow .12s;
}
.nf-btn-primary:hover { opacity: 0.92; box-shadow: var(--shadow-md); }

.nf-btn-ghost {
  font-size: var(--text-sm);
  color: var(--gray-dark);
  background: var(--bg-surface);
  border: none;
  border-radius: var(--radius-md);
  padding: 11px 20px;
  text-decoration: none;
  box-shadow: var(--shadow-sm);
  transition: color .12s, box-shadow .12s;
}
.nf-btn-ghost:hover { color: var(--purple-900); box-shadow: var(--shadow-md); }
</style>
