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
          viewBox="0 0 600 740"
          xmlns="http://www.w3.org/2000/svg"
          class="korea-map"
          aria-label="대한민국 방문 지역 지도"
        >
          <g v-for="r in regions" :key="r.code">
            <path
              :d="r.d"
              :fill-rule="r.fillRule || 'nonzero'"
              :class="['region-path', { visited: visited.has(r.code) }]"
            />
            <!-- 각 path의 중심 좌표에 이름 표시 -->
            <text
              :x="r.labelX"
              :y="r.labelY"
              text-anchor="middle"
              dominant-baseline="middle"
              class="region-label"
              :style="visited.has(r.code) ? { fill: '#fff' } : {}"
            >{{ r.name }}</text>
          </g>

          <!-- 제주 해협 구분선 -->
          <line x1="60" y1="490" x2="540" y2="490"
                stroke="var(--gray-border, #ddd)" stroke-width="1.2" stroke-dasharray="6 4" />
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
import { KOREA_REGIONS } from '@/assets/data/koreaMap.js'

/**
 * SVG path의 대략적인 중심 좌표 계산
 * path의 첫 M 이후 좌표들의 평균을 사용
 */
function pathCenter(d) {
  const coords = [...d.matchAll(/([0-9.]+),([0-9.]+)/g)].map(m => [+m[1], +m[2]])
  if (!coords.length) return { x: 0, y: 0 }
  // 첫 번째 path만 사용 (섬 제외)
  const firstPath = d.split('M')[1]
  const firstCoords = [...(firstPath || '').matchAll(/([0-9.]+),([0-9.]+)/g)].map(m => [+m[1], +m[2]])
  const pts = firstCoords.length ? firstCoords : coords
  const x = pts.reduce((s, c) => s + c[0], 0) / pts.length
  const y = pts.reduce((s, c) => s + c[1], 0) / pts.length
  return { x: Math.round(x), y: Math.round(y) }
}

const regions = KOREA_REGIONS.map(r => {
  const { x, y } = pathCenter(r.d)
  return { ...r, labelX: x, labelY: y }
})

const visited      = ref(new Set())
const loading      = ref(false)
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
  max-width: 520px;
  margin: 0 auto;
  padding: 0 16px 40px;
}
.mypage-header {
  padding: 24px 0 16px;
  border-bottom: 1px solid var(--gray-border, #e5e7eb);
  margin-bottom: 24px;
}
.mypage-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-primary);
}
.map-subtitle {
  margin: 6px 0 0;
  font-size: 0.9rem;
  color: var(--gray-muted);
}
.visited-count {
  font-weight: 600;
  color: var(--purple-900);
}
.map-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}
.map-loading {
  color: var(--gray-muted);
  font-size: 0.9rem;
}
.korea-map {
  width: 100%;
  max-width: 420px;
  height: auto;
  overflow: visible;
}

/* 시도 path */
.region-path {
  fill: var(--bg-page, #f5f5f7);
  stroke: var(--gray-border, #d1d5db);
  stroke-width: 1.5;
  stroke-linejoin: round;
  transition: fill 0.25s;
  cursor: default;
}
.region-path.visited {
  fill: var(--purple-900, #3b0764);
  stroke: #2e0651;
}
.region-path:hover {
  opacity: 0.85;
}

/* 이름 레이블 */
.region-label {
  font-size: 10px;
  fill: var(--gray-muted, #6b7280);
  pointer-events: none;
  user-select: none;
  font-weight: 500;
}

/* 범례 */
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
  background: var(--bg-page, #f5f5f7);
}
</style>
