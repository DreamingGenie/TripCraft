<template>
  <div class="mypage-section">
    <div class="mypage-header">
      <h2 class="mypage-title">방문 지도</h2>
      <p class="map-subtitle">
        <span v-if="!loading" class="visited-count">
          {{ visitedCount }} / {{ KOREA_REGIONS.length }}개 지역 방문
        </span>
      </p>
    </div>

    <div class="map-card">
      <div class="map-wrap">
        <div v-if="loading" class="map-loading">로딩 중...</div>

        <svg
          v-else
          viewBox="0 44 300 532"
          xmlns="http://www.w3.org/2000/svg"
          class="korea-map"
          aria-label="대한민국 방문 지역 지도"
        >
          <g v-for="r in KOREA_REGIONS" :key="r.code">
            <path
              :d="r.svgPath"
              :class="['region-path', { visited: visited.has(r.code) }]"
            />
            <text
              v-if="r.cx && r.cy"
              :x="r.cx"
              :y="r.cy"
              text-anchor="middle"
              dominant-baseline="middle"
              class="region-label"
              :style="visited.has(r.code) ? { fill: '#fff' } : {}"
            >{{ r.name }}</text>
          </g>
        </svg>

        <div class="legend">
          <span class="legend-item">
            <span class="legend-box visited-box"></span> 방문함
          </span>
          <span class="legend-item">
            <span class="legend-box unvisited-box"></span> 미방문
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { memberApi } from '@/api/member'
import { KOREA_REGIONS } from '@/assets/data/koreaRegions.js'

const visited      = ref(new Set())
const loading      = ref(true)
const visitedCount = computed(() => visited.value.size)

onMounted(async () => {
  try {
    const codes = await memberApi.getVisitedRegions()
    visited.value = new Set(codes)
  } catch {
    visited.value = new Set()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.map-subtitle {
  margin: 6px 0 0;
  font-size: 0.9rem;
  color: var(--gray-muted);
}
.visited-count {
  font-weight: 600;
  color: var(--purple-900);
}
.map-card {
  background: var(--bg-surface);
  border: 1px solid var(--gray-border);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-md);
  padding: 28px 24px 22px;
}
.map-wrap {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}
.map-loading {
  color: var(--gray-muted);
  font-size: 0.9rem;
  padding: 40px 0;
}
.korea-map {
  width: 100%;
  max-width: 100%;
  height: auto;
  overflow: visible;
}

.region-path {
  fill: var(--bg-page, #f0f0f0);
  stroke: #fff;
  stroke-width: 1.2;
  stroke-linejoin: round;
  transition: fill 0.25s;
  cursor: default;
}
.region-path.visited {
  fill: var(--purple-900, #3b0764);
}
.region-path:hover {
  opacity: 0.8;
}

.region-label {
  font-size: 14px;
  fill: var(--gray-muted, #6b7280);
  pointer-events: none;
  user-select: none;
  font-weight: 600;
}

.legend {
  display: flex;
  gap: 20px;
  font-size: 0.85rem;
  color: var(--gray-muted);
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
.legend-box {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 3px;
  border: 1.5px solid var(--gray-border);
}
.visited-box {
  background: var(--purple-900, #3b0764);
  border-color: #2e0651;
}
.unvisited-box {
  background: var(--bg-page, #f0f0f0);
}
</style>
