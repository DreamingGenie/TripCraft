<template>
  <div class="mypage-section">
    <div class="mypage-header">
      <h2 class="mypage-title">방문 지도</h2>
      <p class="map-subtitle">
        <span v-if="!loading" class="visited-count">
          방문 {{ visitedCount }} · 예정 {{ plannedCount }} · 전체 {{ regions.length }}개 지역
        </span>
      </p>
    </div>

    <div class="map-card">
      <div class="map-wrap">
        <div v-if="loading" class="map-loading">로딩 중...</div>

        <svg
          v-else
          viewBox="-3 146 306 330"
          xmlns="http://www.w3.org/2000/svg"
          class="korea-map"
          aria-label="대한민국 방문 지역 지도"
        >
          <defs>
            <clipPath v-for="r in regions" :id="`rgn-clip-${r.code}`" :key="`clip-${r.code}`">
              <path :d="r.svgPath" />
            </clipPath>
          </defs>

          <g
            v-for="r in regions"
            :key="r.code"
            class="region clickable"
            @mouseenter="onEnter(r, $event)"
            @mousemove="onMove"
            @mouseleave="onLeave"
            @click="onClick(r)"
          >
            <!-- 사진: 폴리곤 모양으로 clip + crop(초점/확대) 적용 -->
            <template v-if="coverOf(r.code)">
              <foreignObject
                :x="bbox(r).x"
                :y="bbox(r).y"
                :width="bbox(r).w"
                :height="bbox(r).h"
                :clip-path="`url(#rgn-clip-${r.code})`"
                class="region-photo"
              >
                <div xmlns="http://www.w3.org/1999/xhtml" class="region-photo-inner">
                  <img :src="coverOf(r.code)" :style="cropStyle(r.code)" alt="" />
                </div>
              </foreignObject>
              <path :d="r.svgPath" class="region-path region-outline" />
            </template>

            <!-- 그 외: 상태별 색 -->
            <path
              v-else
              :d="r.svgPath"
              :class="['region-path', statusClass(r.code)]"
            />

            <!-- 작은 광역시: 호버/클릭 hit 영역 확보 -->
            <circle v-if="r.code <= METRO_MAX" :cx="r.cx" :cy="r.cy" r="8" class="region-hit" />

            <!-- 최상단 투명 hit 영역: foreignObject(HTML 사진) 위에서도 호버/클릭 잡히게 -->
            <path :d="r.svgPath" class="region-hit-area" />
          </g>
        </svg>

        <div class="legend">
          <span class="legend-item"><span class="legend-box visited-box"></span> 방문(사진)</span>
          <span class="legend-item"><span class="legend-box planned-box"></span> 예정</span>
          <span class="legend-item"><span class="legend-box none-box"></span> 미방문</span>
        </div>
      </div>
    </div>

    <!-- 호버 말풍선 썸네일 -->
    <teleport to="body">
      <div
        v-if="hover"
        class="map-tip"
        :style="{ left: `${tip.x + 16}px`, top: `${tip.y + 16}px` }"
      >
        <div v-if="hover.cover" class="map-tip-photo">
          <img :src="hover.cover" alt="" />
        </div>
        <div class="map-tip-body">
          <strong class="map-tip-name">{{ hover.name }}</strong>
          <span class="map-tip-status" :class="statusKey(hover.status)">{{ statusLabel(hover.status) }}</span>
          <span v-if="hover.storyCount" class="map-tip-count">여행이야기 {{ hover.storyCount }}개</span>
          <span class="map-tip-hint">클릭해 사진 설정</span>
        </div>
      </div>
    </teleport>

    <!-- 표지 사진 에디터 -->
    <RegionCoverEditor
      v-if="editor"
      :region-code="editor.code"
      :region-name="editor.name"
      :svg-path="editor.svgPath"
      :current-image-url="editor.imageUrl"
      :current-pinned="editor.pinned"
      :current-crop="editor.crop"
      @close="editor = null"
      @changed="load"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { memberApi } from '@/api/member'
import { KOREA_REGIONS } from '@/assets/data/koreaRegions.js'
import RegionCoverEditor from '@/components/RegionCoverEditor.vue'

const METRO_MAX = 30 // code <= 30 = 특별시/광역시(작은 지역) → 라벨 숨기고 호버로

const regions = KOREA_REGIONS
const loading = ref(true)
const mapData = ref({})      // code -> { regionCode, status, storyCount, imageUrl, focusX, focusY, zoom, sourcePostId }
const hover   = ref(null)
const tip     = reactive({ x: 0, y: 0 })
const editor  = ref(null)

const visitedCount = computed(
  () => Object.values(mapData.value).filter((d) => d.status === 'VISITED').length,
)
const plannedCount = computed(
  () => Object.values(mapData.value).filter((d) => d.status === 'PLANNED').length,
)

function info(code)   { return mapData.value[code] || null }
function statusOf(code) { return info(code)?.status || 'NONE' }
function coverOf(code)  { return info(code)?.imageUrl || null }
function cropStyle(code) {
  const d = info(code)
  const fx = d?.focusX ?? 50, fy = d?.focusY ?? 50, zoom = d?.zoom ?? 1
  return {
    objectPosition: `${fx}% ${fy}%`,
    transform: `scale(${zoom})`,
    transformOrigin: `${fx}% ${fy}%`,
  }
}
function statusClass(code) {
  const s = statusOf(code)
  return s === 'VISITED' ? 'visited' : s === 'PLANNED' ? 'planned' : 'none'
}
function statusKey(s)   { return s === 'VISITED' ? 'visited' : s === 'PLANNED' ? 'planned' : 'none' }
function statusLabel(s) { return s === 'VISITED' ? '방문' : s === 'PLANNED' ? '예정' : '미방문' }

// path별 bbox 메모이즈 (사진을 지역 크기에 맞춰 cover-fit)
const bboxCache = {}
function bbox(r) {
  if (bboxCache[r.code]) return bboxCache[r.code]
  const nums = r.svgPath.match(/-?\d+\.?\d*/g).map(Number)
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
  for (let i = 0; i < nums.length; i += 2) {
    const x = nums[i], y = nums[i + 1]
    if (x < minX) minX = x
    if (x > maxX) maxX = x
    if (y < minY) minY = y
    if (y > maxY) maxY = y
  }
  return (bboxCache[r.code] = { x: minX, y: minY, w: maxX - minX, h: maxY - minY })
}

async function load() {
  loading.value = true
  try {
    const items = await memberApi.getMyMap()
    const m = {}
    for (const it of items) m[it.regionCode] = it
    mapData.value = m
  } catch {
    mapData.value = {}
  } finally {
    loading.value = false
  }
}
onMounted(load)

function onEnter(r, e) {
  const d = info(r.code)
  hover.value = {
    code: r.code,
    name: r.name,
    status: statusOf(r.code),
    storyCount: d?.storyCount || 0,
    cover: d?.imageUrl || null,
  }
  onMove(e)
}
function onMove(e) { tip.x = e.clientX; tip.y = e.clientY }
function onLeave()  { hover.value = null }

function onClick(r) {
  const d = info(r.code)
  hover.value = null
  editor.value = {
    code: r.code,
    name: r.name,
    svgPath: r.svgPath,
    imageUrl: d?.imageUrl || null,
    pinned: !!d?.pinned,
    crop: { focusX: d?.focusX ?? 50, focusY: d?.focusY ?? 50, zoom: d?.zoom ?? 1 },
  }
}
</script>

<style scoped>
.map-subtitle {
  margin: 6px 0 0;
  font-size: var(--text-sm);
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
  font-size: var(--text-sm);
  padding: 40px 0;
}
.korea-map {
  width: 100%;
  max-width: 480px;
  height: auto;
  overflow: visible;
}

.region.clickable { cursor: pointer; }

.region-path {
  stroke: #fff;
  stroke-width: 1.2;
  stroke-linejoin: round;
  transition: fill 0.25s, opacity 0.2s;
}
.region-path.none     { fill: var(--bg-page, #eceaf1); }
.region-path.planned  { fill: #cbb8e6; }
.region-path.visited  { fill: var(--purple-900, #3b0764); }
.region-outline {
  fill: none;
  stroke: #fff;
  stroke-width: 1.2;
  stroke-linejoin: round;
  pointer-events: none;
}
.region-photo { transition: opacity 0.2s; pointer-events: none; }
.region:hover .region-photo { opacity: 0.85; }
.region-photo-inner { width: 100%; height: 100%; overflow: hidden; }
.region-photo-inner img { width: 100%; height: 100%; object-fit: cover; display: block; }
.region-hit-area { fill: transparent; pointer-events: all; }
.region:hover .region-path:not(.region-outline) { opacity: 0.82; }
.region-hit { fill: transparent; }

.legend {
  display: flex;
  gap: 18px;
  font-size: var(--text-sm);
  color: var(--gray-muted);
  flex-wrap: wrap;
}
.legend-item { display: flex; align-items: center; gap: 6px; }
.legend-box {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 3px;
  border: 1.5px solid var(--gray-border);
}
.visited-box { background: var(--purple-900, #3b0764); border-color: #2e0651; }
.planned-box { background: #cbb8e6; }
.none-box    { background: var(--bg-page, #eceaf1); }

/* 호버 말풍선 (body로 teleport) */
.map-tip {
  position: fixed;
  z-index: 1100;
  pointer-events: none;
  background: var(--bg-surface, #fff);
  border: 1px solid var(--gray-border, #e5e7eb);
  border-radius: 12px;
  box-shadow: var(--shadow-lg, 0 12px 32px rgba(0, 0, 0, 0.22));
  overflow: hidden;
  width: 200px;
}
.map-tip-photo {
  width: 100%;
  aspect-ratio: 4 / 3;
  background: #eee;
}
.map-tip-photo img { width: 100%; height: 100%; object-fit: cover; display: block; }
.map-tip-body { padding: 9px 11px; display: flex; flex-direction: column; gap: 3px; }
.map-tip-name { font-size: 14px; font-weight: 700; color: var(--gray-strong, #1f2937); }
.map-tip-status { font-size: 12px; font-weight: 600; }
.map-tip-status.visited { color: var(--purple-900, #3b0764); }
.map-tip-status.planned { color: #8b6fc0; }
.map-tip-status.none    { color: var(--gray-muted, #9ca3af); }
.map-tip-count { font-size: 12px; color: var(--gray-muted, #6b7280); }
.map-tip-hint  { font-size: 11px; color: var(--purple-700, #6d28d9); }
</style>
