<template>
  <div id="landing-page">
    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-inner">
        <div class="hero-text">
          <span class="hero-eyebrow">Travel Planner</span>
          <h1 class="hero-headline">
            여행을 함께,<br>
            <em>더 스마트하게</em>
          </h1>
          <p class="hero-sub">
            관광지 탐색부터 이동 시간 계산까지,
            TripCraft가 여행 계획을 도와드립니다.
          </p>
          <div class="hero-ctas">
            <RouterLink class="hero-cta-primary" to="/explore">
              지금 탐색하기 →
            </RouterLink>
            <RouterLink class="hero-cta-ghost" to="/auth">로그인</RouterLink>
          </div>
        </div>

        <!-- Mock UI -->
        <div class="hero-mock">
          <div class="mock-window">
            <div class="mock-titlebar">
              <span class="mock-dot mock-dot-r"></span>
              <span class="mock-dot mock-dot-y"></span>
              <span class="mock-dot mock-dot-g"></span>
              <span class="mock-url">tripcraft.app/explore</span>
            </div>
            <div class="mock-body">
              <div class="mock-sidebar">
                <div class="mock-nav-item active"></div>
                <div class="mock-nav-item"></div>
                <div class="mock-nav-item"></div>
                <div class="mock-nav-item active-teal"></div>
              </div>
              <div class="mock-content">
                <div class="mock-search"></div>
                <div class="mock-chip-row">
                  <div class="mock-chip"></div>
                  <div class="mock-chip"></div>
                  <div class="mock-chip"></div>
                </div>
                <div class="mock-cards">
                  <div class="mock-card">
                    <div class="mock-card-img c1"></div>
                    <div class="mock-card-line"></div>
                    <div class="mock-card-line short"></div>
                  </div>
                  <div class="mock-card">
                    <div class="mock-card-img c2"></div>
                    <div class="mock-card-line"></div>
                    <div class="mock-card-line short"></div>
                  </div>
                  <div class="mock-card">
                    <div class="mock-card-img c3"></div>
                    <div class="mock-card-line"></div>
                    <div class="mock-card-line short"></div>
                  </div>
                  <div class="mock-card">
                    <div class="mock-card-img c4"></div>
                    <div class="mock-card-line"></div>
                    <div class="mock-card-line short"></div>
                  </div>
                </div>
              </div>
              <div class="mock-map">
                <div class="mock-map-pin" style="top:40%;left:35%"></div>
                <div class="mock-map-pin" style="top:55%;left:60%;background:var(--teal-600)"></div>
                <div class="mock-map-pin" style="top:30%;left:55%"></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Features Section -->
    <section class="features-section">
      <div class="features-inner">
        <span class="section-eyebrow">Features</span>
        <h2 class="section-title">여행의 모든 과정을 한 곳에서</h2>
        <p class="section-sub">복잡했던 여행 계획을 TripCraft가 간단하게 만들어드립니다.</p>
        <div class="features-grid">
          <div class="feature-card">
            <span class="feature-icon">🔍</span>
            <div class="feature-title">관광지 탐색</div>
            <p class="feature-desc">전국 관광지, 음식점, 숙박을 한 곳에서 검색하고 지도로 확인하세요.</p>
          </div>
          <div class="feature-card">
            <span class="feature-icon">📅</span>
            <div class="feature-title">일정 드래그</div>
            <p class="feature-desc">드래그 앤 드롭으로 원하는 날짜와 시간에 간편하게 타임라인을 구성하세요.</p>
          </div>
          <div class="feature-card">
            <span class="feature-icon">🚌</span>
            <div class="feature-title">이동 시간 계산</div>
            <p class="feature-desc">ODsay API로 장소 간 대중교통 이동 시간을 자동으로 계산해드립니다.</p>
          </div>
          <div class="feature-card">
            <span class="feature-icon">💬</span>
            <div class="feature-title">커뮤니티</div>
            <p class="feature-desc">완성된 여행 일정을 공유하고 다른 여행자들과 피드백을 나눠보세요.</p>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="cta-section">
      <div class="cta-inner">
        <h2 class="cta-title">지금 바로 시작하세요</h2>
        <p class="cta-sub">회원가입 없이도 관광지를 탐색할 수 있어요. 일정 저장은 로그인 후 이용 가능합니다.</p>
        <RouterLink class="cta-btn" to="/explore">
          무료로 시작하기 →
        </RouterLink>
      </div>
    </section>

    <!-- Footer -->
    <footer class="landing-footer">
      <RouterLink class="footer-logo" to="/">TripCraft</RouterLink>
      <span class="footer-copy">© 2025 전진 · 송정기</span>
      <nav class="footer-links">
        <RouterLink class="footer-link" to="/about">소개</RouterLink>
        <RouterLink class="footer-link" to="/community">커뮤니티</RouterLink>
      </nav>
    </footer>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

onMounted(async () => {
  await auth.fetchMe()
  if (auth.isLoggedIn) router.replace('/explore')
})
</script>

<style scoped>
@import '@/assets/css/landing.css';
</style>
