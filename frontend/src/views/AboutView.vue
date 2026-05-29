<template>
  <div id="about-page">
    <!-- Hero -->
    <section class="about-hero">
      <div class="about-hero-inner">
        <span class="about-eyebrow">About</span>
        <h1 class="about-headline">TripCraft 소개</h1>
        <p class="about-sub">관광지 탐색부터 여행 일정 확정까지, 원스톱 여행 플래닝 서비스</p>
      </div>
    </section>

    <!-- 프로젝트 설명 -->
    <section class="about-section">
      <div class="about-inner">
        <h2 class="about-section-title">서비스 소개</h2>
        <p class="about-desc">
          TripCraft는 관광지 탐색부터 여행 일정 확정까지 원스톱으로 제공하는 여행 플래닝 서비스입니다.
          전국 수십만 개의 관광 데이터를 기반으로 원하는 장소를 찾고, 드래그 앤 드롭으로 일정을 구성하며,
          ODsay API를 통해 장소 간 이동 시간을 자동으로 계산해드립니다.
        </p>

        <h3 class="about-badge-title">기술 스택</h3>
        <div class="tech-badges">
          <span class="tech-badge badge-java">Java 21</span>
          <span class="tech-badge badge-spring">Spring Boot 3</span>
          <span class="tech-badge badge-vue">Vue 3</span>
          <span class="tech-badge badge-mybatis">MyBatis</span>
          <span class="tech-badge badge-mysql">MySQL 8.0</span>
          <span class="tech-badge badge-naver">Naver Maps</span>
          <span class="tech-badge badge-odsay">ODsay API</span>
        </div>
      </div>
    </section>

    <!-- 팀 소개 -->
    <section class="about-section about-section-alt">
      <div class="about-inner">
        <h2 class="about-section-title">팀 소개</h2>
        <div class="team-grid">
          <div class="team-card">
            <div class="team-avatar">전</div>
            <div class="team-info">
              <div class="team-name">전진</div>
              <div class="team-role">팀원</div>
            </div>
          </div>
          <div class="team-card">
            <div class="team-avatar">송</div>
            <div class="team-info">
              <div class="team-name">송정기</div>
              <div class="team-role">팀원</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="about-cta">
      <RouterLink class="about-cta-btn" to="/explore">탐색 시작하기 →</RouterLink>
    </section>
  </div>
</template>

<script setup>
import { RouterLink } from 'vue-router'
</script>

<style scoped>
#about-page {
  overflow-y: auto;
  flex: 1;
  min-height: 0;
  background: var(--bg-page);
}

.about-hero {
  background: var(--bg-surface);
  border-bottom: 0.5px solid var(--gray-border);
  padding: 56px 40px 48px;
}

.about-hero-inner {
  max-width: 720px;
  margin: 0 auto;
}

.about-eyebrow {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  color: var(--purple-900);
  letter-spacing: .6px;
  text-transform: uppercase;
  margin-bottom: 10px;
}

.about-headline {
  font-size: 2.25rem;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.03em;
  margin-bottom: 12px;
}

.about-sub {
  font-size: 15px;
  color: var(--gray-dark);
  line-height: 1.6;
}

.about-section {
  padding: 52px 40px;
  background: var(--bg-page);
}

.about-section-alt {
  background: var(--bg-surface);
  border-top: 0.5px solid var(--gray-border);
  border-bottom: 0.5px solid var(--gray-border);
}

.about-inner {
  max-width: 720px;
  margin: 0 auto;
}

.about-section-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
  letter-spacing: -0.01em;
}

.about-desc {
  font-size: 14px;
  color: var(--gray-dark);
  line-height: 1.75;
  margin-bottom: 32px;
}

.about-badge-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--gray-muted);
  text-transform: uppercase;
  letter-spacing: .5px;
  margin-bottom: 12px;
}

.tech-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tech-badge {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  padding: 5px 14px;
  border-radius: var(--radius-full);
  border: 0.5px solid;
}

.badge-java    { color: #c0392b; background: #fef2f2; border-color: #fca5a5; }
.badge-spring  { color: #15803d; background: #f0fdf4; border-color: #86efac; }
.badge-vue     { color: #16a34a; background: #f0fdf4; border-color: #4ade80; }
.badge-mybatis { color: var(--blue-600); background: var(--blue-50); border-color: var(--blue-100); }
.badge-mysql   { color: #0369a1; background: #f0f9ff; border-color: #7dd3fc; }
.badge-naver   { color: #15803d; background: #f0fdf4; border-color: #86efac; }
.badge-odsay   { color: var(--purple-900); background: var(--purple-50); border-color: var(--purple-100); }

.team-grid {
  display: flex;
  gap: 16px;
}

.team-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: var(--bg-page);
  border: 0.5px solid var(--gray-border);
  border-radius: var(--radius-xl);
  padding: 18px 24px;
  flex: 1;
  max-width: 260px;
}

.team-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--purple-900);
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.team-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 3px;
}

.team-role {
  font-size: 12px;
  color: var(--gray-muted);
}

.about-cta {
  padding: 48px 40px;
  text-align: center;
  background: var(--bg-page);
}

.about-cta-btn {
  display: inline-flex;
  align-items: center;
  font-size: 14px;
  font-weight: 500;
  color: #fff;
  background: var(--purple-900);
  border: none;
  border-radius: var(--radius-lg);
  padding: 12px 28px;
  text-decoration: none;
  transition: opacity .12s, transform .1s;
}
.about-cta-btn:hover { opacity: 0.88; transform: translateY(-1px); }
</style>
