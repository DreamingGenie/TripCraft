<template>
  <main id="main">
    <div class="map-layout">
      <div class="mypage-header">
        <h2 class="mypage-title">방문 지도</h2>
        <p class="map-subtitle">
          <span v-if="!loading" class="visited-count">
            {{ visitedCount }} / 17개 지역 방문
          </span>
        </p>
      </div>

      <div class="map-wrap">
        <div v-if="loading" class="map-loading">로딩 중...</div>

        <svg
          v-else
          viewBox="0 0 340 450"
          xmlns="http://www.w3.org/2000/svg"
          class="korea-map"
          aria-label="대한민국 방문 지역 지도"
        >
          <g v-for="r in regions" :key="r.code">
            <rect
              :x="r.x"
              :y="r.y"
              :width="TW"
              :height="TH"
              :rx="6"
              :class="['region-tile', { visited: visited.has(r.code) }]"
            />
            <text
              :x="r.x + TW / 2"
              :y="r.y + TH / 2 + 1"
              text-anchor="middle"
              dominant-baseline="middle"
              class="region-label"
              :style="visited.has(r.code) ? { fill: '#fff' } : {}"
            >{{ r.name }}</text>
          </g>

          <!-- 제주 바다 구분선 -->
          <line x1="20" y1="415" x2="320" y2="415"
                stroke="var(--color-border)" stroke-width="1" stroke-dasharray="4 4" />
        </svg>

        <!-- 범례 -->
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
  </main>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { http } from '@/api/http'

/* 타일 크기 */
const TW = 72
const TH = 44

/*
 * 격자 좌표: x = 20 + col * 78,  y = 20 + row * 50
 * col 0-3 (서→동),  row 0-7 (북→남)
 *
 * TourAPI sido_code 기준
 *   1=서울 2=인천 3=대전 4=대구 5=광주
 *   6=부산 7=울산 8=세종
 *   31=경기 32=강원 33=충북 34=충남
 *   35=전북 36=전남 37=경북 38=경남 39=제주
 */
const regions = [
  { code: 32, name: '강원',  col: 3, row: 0 },
  { code:  2, name: '인천',  col: 0, row: 1 },
  { code:  1, name: '서울',  col: 1, row: 1 },
  { code: 31, name: '경기',  col: 1, row: 2 },
  { code: 33, name: '충북',  col: 2, row: 2 },
  { code: 37, name: '경북',  col: 3, row: 2 },
  { code: 34, name: '충남',  col: 0, row: 3 },
  { code:  8, name: '세종',  col: 1, row: 3 },
  { code:  3, name: '대전',  col: 2, row: 3 },
  { code:  4, name: '대구',  col: 3, row: 3 },
  { code: 35, name: '전북',  col: 0, row: 4 },
  { code:  7, name: '울산',  col: 3, row: 4 },
  { code: 36, name: '전남',  col: 0, row: 5 },
  { code:  5, name: '광주',  col: 1, row: 5 },
  { code: 38, name: '경남',  col: 2, row: 5 },
  { code:  6, name: '부산',  col: 3, row: 5 },
  { code: 39, name: '제주',  col: 1, row: 7 },
].map(r => ({ ...r, x: 20 + r.col * 78, y: 20 + r.row * 50 }))

const visited  = ref(new Set())
const loading  = ref(false)

const visitedCount = computed(() => visited.value.size)

onMounted(async () => {
  loading.value = true
  try {
    const codes = await http.get('/api/members/me/visited-regions')
    visited.value = new Set(codes)
  } catch {
    visited.value = new Set()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.map-layout {
  max-width: 480px;
  margin: 0 auto;
  padding: 0 16px 40px;
}
.mypage-header {
  padding: 24px 0 16px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 24px;
}
.mypage-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--color-text);
}
.map-subtitle {
  margin: 6px 0 0;
  font-size: 0.9rem;
  color: var(--color-text-muted);
}
.visited-count {
  font-weight: 600;
  color: var(--color-primary, #3b82f6);
}
.map-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}
.map-loading {
  color: var(--color-text-muted);
  font-size: 0.9rem;
}
.korea-map {
  width: 100%;
  max-width: 360px;
  height: auto;
}

/* 타일 */
.region-tile {
  fill: var(--color-bg-subtle, #f0f2f5);
  stroke: var(--color-border);
  stroke-width: 1.5;
  transition: fill 0.2s;
}
.region-tile.visited {
  fill: var(--color-primary, #3b82f6);
  stroke: var(--color-primary-dark, #2563eb);
}

/* 레이블 */
.region-label {
  font-size: 11px;
  fill: var(--color-text-muted);
  pointer-events: none;
  user-select: none;
}

/* 범례 */
.legend {
  display: flex;
  gap: 20px;
  font-size: 0.85rem;
  color: var(--color-text-muted);
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
.legend-box {
  display: inline-block;
  width: 16px;
  height: 16px;
  border-radius: 3px;
  border: 1.5px solid var(--color-border);
}
.visited-box {
  background: var(--color-primary, #3b82f6);
  border-color: var(--color-primary-dark, #2563eb);
}
.unvisited-box {
  background: var(--color-bg-subtle, #f0f2f5);
}
</style>
