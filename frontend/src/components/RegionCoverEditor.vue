<template>
  <div class="editor-backdrop" @click.self="close">
    <div class="editor-panel" role="dialog" aria-modal="true">
      <header class="editor-head">
        <h3 class="editor-title">
          {{ regionName }}
          <span class="editor-sub">{{ mode === 'adjust' ? '사진 위치 조정' : mode === 'images' ? '사진 선택' : '표지 사진' }}</span>
        </h3>
        <button class="editor-close" aria-label="닫기" @click="close">✕</button>
      </header>

      <!-- ===== 선택 화면 ===== -->
      <template v-if="mode === 'select'">
        <!-- 현재 표지 (큰 히어로, 클릭 시 전체화면) -->
        <div v-if="currentImageUrl" class="hero">
          <button class="hero-photo" @click="fullscreen = true" aria-label="전체화면으로 보기">
            <img :src="currentImageUrl" alt="현재 표지" />
            <span class="hero-expand">⛶ 전체화면</span>
          </button>
          <div class="hero-actions">
            <span class="hero-label">
              {{ currentPinned ? '현재 표지' : '자동 표지 (최신 여행이야기)' }}
            </span>
            <div class="hero-buttons">
              <button v-if="currentPinned" class="btn-ghost" @click="adjustCurrent">위치 조정</button>
              <button v-if="currentPinned" class="btn-ghost danger" @click="reset">표지 해제</button>
            </div>
          </div>
        </div>

        <div class="editor-section-head">
          <span>여행이야기에서 고르기</span>
          <label class="btn-upload">
            사진 직접 올리기
            <input type="file" accept="image/*" hidden @change="onUpload" />
          </label>
        </div>
        <p v-if="uploading" class="editor-uploading">업로드 중...</p>

        <div v-if="loading" class="editor-loading">불러오는 중...</div>
        <ul v-else-if="stories.length" class="story-list">
          <li
            v-for="s in stories"
            :key="s.postId"
            class="story-row"
            :class="{ busy: saving }"
            @click="onStoryClick(s)"
          >
            <div class="story-thumb" :class="{ 'is-placeholder': !s.coverImageUrl }"
                 :style="!s.coverImageUrl ? placeholderStyle(s.postId) : null">
              <img v-if="s.coverImageUrl" :src="s.coverImageUrl" :alt="s.title" loading="lazy" />
            </div>
            <div class="story-info">
              <p class="story-row-title">{{ s.title }}</p>
              <span class="story-row-date">{{ formatDate(s.createdAt) }}</span>
            </div>
            <span v-if="s.imageCount > 1" class="story-count">사진 {{ s.imageCount }}장</span>
            <span class="story-arrow">›</span>
          </li>
        </ul>
        <div v-else class="editor-empty">이 지역 여행이야기가 없습니다. 새 사진을 올려보세요.</div>
      </template>

      <!-- ===== 한 글의 사진 선택 ===== -->
      <template v-else-if="mode === 'images'">
        <button class="btn-back" @click="mode = 'select'">← 여행이야기 목록</button>
        <p class="editor-hint">표지로 쓸 사진을 고르세요.</p>
        <ul class="image-grid">
          <li v-for="img in postImages" :key="img.imageUrl" class="image-cell" @click="pick(img)">
            <img :src="img.imageUrl" alt="" loading="lazy" />
          </li>
        </ul>
      </template>

      <!-- ===== crop 조정 ===== -->
      <template v-else>
        <p class="editor-hint">드래그로 위치를 잡고, 슬라이더로 확대하세요.</p>
        <div class="editor-preview-wrap">
          <svg :viewBox="previewViewBox" class="editor-preview" @pointerdown="onDragStart">
            <defs>
              <clipPath id="editor-clip"><path :d="svgPath" /></clipPath>
            </defs>
            <foreignObject :x="bb.x" :y="bb.y" :width="bb.w" :height="bb.h" clip-path="url(#editor-clip)">
              <div xmlns="http://www.w3.org/1999/xhtml" class="editor-photo-inner">
                <img :src="adjustUrl" :style="cropStyle" draggable="false" />
              </div>
            </foreignObject>
            <path :d="svgPath" class="editor-preview-outline" />
          </svg>
        </div>
        <div class="editor-zoom">
          <span>축소</span>
          <input type="range" min="1" max="3" step="0.05" v-model.number="crop.zoom" />
          <span>확대</span>
        </div>
        <div class="editor-adjust-actions">
          <button class="btn-ghost" @click="mode = 'select'">← 사진 변경</button>
          <button class="btn-primary" :disabled="saving" @click="saveCrop">완료</button>
        </div>
      </template>
    </div>

    <!-- 전체화면 보기 -->
    <teleport to="body">
      <div v-if="fullscreen" class="fullscreen" @click="fullscreen = false">
        <img :src="currentImageUrl" alt="표지 전체화면" />
        <button class="fullscreen-close" aria-label="닫기">✕</button>
      </div>
    </teleport>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { memberApi } from '@/api/member'
import { formatDate } from '@/utils/format'

const props = defineProps({
  regionCode: { type: Number, required: true },
  regionName: { type: String, required: true },
  svgPath: { type: String, required: true },
  currentImageUrl: { type: String, default: null },
  currentPinned: { type: Boolean, default: false },
  currentCrop: { type: Object, default: () => ({ focusX: 50, focusY: 50, zoom: 1 }) },
})
const emit = defineEmits(['close', 'changed'])

const stories    = ref([])
const postImages = ref([])
const loading    = ref(true)
const uploading  = ref(false)
const saving     = ref(false)
const mode       = ref('select')      // 'select' | 'images' | 'adjust'
const fullscreen = ref(false)
const adjustUrl  = ref(null)
const crop       = reactive({ focusX: 50, focusY: 50, zoom: 1 })
let objectUrl    = null

const bb = computeBBox(props.svgPath)
const previewViewBox = `${bb.x} ${bb.y} ${bb.w} ${bb.h}`

const cropStyle = computed(() => ({
  objectPosition: `${crop.focusX}% ${crop.focusY}%`,
  transform: `scale(${crop.zoom})`,
  transformOrigin: `${crop.focusX}% ${crop.focusY}%`,
}))

onMounted(async () => {
  try {
    stories.value = await memberApi.getRegionStories(props.regionCode)
  } catch {
    stories.value = []
  } finally {
    loading.value = false
  }
})
onBeforeUnmount(() => { if (objectUrl) URL.revokeObjectURL(objectUrl) })

async function onStoryClick(story) {
  if (saving.value) return
  const imgs = await memberApi.getPostImages(props.regionCode, story.postId)
  if (imgs.length === 0) return
  if (imgs.length === 1) {
    await pick(imgs[0])                 // 사진 1장이면 바로 반영
  } else {
    postImages.value = imgs
    mode.value = 'images'               // 여러 장이면 선택 화면
  }
}

async function pick(img) {
  if (saving.value) return
  saving.value = true
  try {
    await memberApi.setRegionCover(props.regionCode, img.imageUrl)
    emit('changed')
    startAdjust(img.imageUrl, { focusX: 50, focusY: 50, zoom: 1 })
  } finally {
    saving.value = false
  }
}

async function onUpload(e) {
  const file = e.target.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    await memberApi.uploadRegionCover(props.regionCode, file)
    emit('changed')
    if (objectUrl) URL.revokeObjectURL(objectUrl)
    objectUrl = URL.createObjectURL(file)
    startAdjust(objectUrl, { focusX: 50, focusY: 50, zoom: 1 })
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}

function adjustCurrent() {
  startAdjust(props.currentImageUrl, props.currentCrop)   // 저장된 위치 그대로 시작
}
function startAdjust(url, c) {
  adjustUrl.value = url
  crop.focusX = c.focusX ?? 50
  crop.focusY = c.focusY ?? 50
  crop.zoom = c.zoom ?? 1
  mode.value = 'adjust'
}

async function saveCrop() {
  saving.value = true
  try {
    await memberApi.updateRegionCrop(props.regionCode, { ...crop })
    emit('changed')
    emit('close')
  } finally {
    saving.value = false
  }
}

async function reset() {
  await memberApi.resetRegionCover(props.regionCode)
  emit('changed')
  emit('close')
}

// 드래그로 초점 이동
let drag = null
function onDragStart(e) {
  const rect = e.currentTarget.getBoundingClientRect()
  drag = { x: e.clientX, y: e.clientY, fx: crop.focusX, fy: crop.focusY, w: rect.width, h: rect.height }
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', onDragEnd)
}
function onDragMove(e) {
  if (!drag) return
  crop.focusX = clamp(drag.fx - ((e.clientX - drag.x) / drag.w) * 100, 0, 100)
  crop.focusY = clamp(drag.fy - ((e.clientY - drag.y) / drag.h) * 100, 0, 100)
}
function onDragEnd() {
  drag = null
  window.removeEventListener('pointermove', onDragMove)
  window.removeEventListener('pointerup', onDragEnd)
}

function close() { emit('close') }
function clamp(v, lo, hi) { return Math.min(hi, Math.max(lo, v)) }

function computeBBox(path) {
  const nums = path.match(/-?\d+\.?\d*/g).map(Number)
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
  for (let i = 0; i < nums.length; i += 2) {
    const x = nums[i], y = nums[i + 1]
    if (x < minX) minX = x
    if (x > maxX) maxX = x
    if (y < minY) minY = y
    if (y > maxY) maxY = y
  }
  return { x: minX, y: minY, w: maxX - minX, h: maxY - minY }
}

const PLACEHOLDER_GRADIENTS = [
  'linear-gradient(135deg, #6b62cf, #534ab7)',
  'linear-gradient(135deg, #f0a868, #d97742)',
  'linear-gradient(135deg, #5fa8d3, #2c7da0)',
  'linear-gradient(135deg, #7cb083, #4a8c5a)',
  'linear-gradient(135deg, #c97b9e, #993556)',
  'linear-gradient(135deg, #e0b94a, #c19320)',
]
function placeholderStyle(id) {
  return { background: PLACEHOLDER_GRADIENTS[(Number(id) || 0) % PLACEHOLDER_GRADIENTS.length] }
}
</script>

<style scoped>
.editor-backdrop {
  position: fixed; inset: 0; background: rgba(0, 0, 0, 0.45);
  display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 20px;
}
.editor-panel {
  background: var(--bg-surface, #fff); border-radius: var(--radius-2xl, 20px);
  box-shadow: var(--shadow-lg, 0 20px 50px rgba(0, 0, 0, 0.25));
  width: min(720px, 100%); max-height: 88vh; display: flex; flex-direction: column;
  padding: 22px 24px 24px; overflow-y: auto;
}
.editor-head { display: flex; align-items: center; justify-content: space-between; }
.editor-title { margin: 0; font-size: var(--text-lg, 18px); font-weight: 700; color: var(--purple-900, #3b0764); }
.editor-sub { font-size: 13px; font-weight: 500; color: var(--gray-muted, #6b7280); margin-left: 8px; }
.editor-close { border: none; background: transparent; font-size: 18px; cursor: pointer; color: var(--gray-muted, #6b7280); }
.editor-hint { margin: 12px 0 14px; font-size: var(--text-sm, 13px); color: var(--gray-muted, #6b7280); }
.editor-loading, .editor-empty { padding: 40px 0; text-align: center; color: var(--gray-muted, #6b7280); font-size: 13px; }

/* 현재 표지 히어로 */
.hero { margin-top: 14px; }
.hero-photo {
  display: block; width: 100%; aspect-ratio: 16 / 9; border: none; padding: 0; cursor: zoom-in;
  border-radius: 14px; overflow: hidden; position: relative; background: #eee;
}
.hero-photo img { width: 100%; height: 100%; object-fit: cover; display: block; }
.hero-expand {
  position: absolute; right: 10px; bottom: 10px; background: rgba(0, 0, 0, 0.55);
  color: #fff; font-size: 12px; font-weight: 600; padding: 5px 10px; border-radius: 999px;
}
.hero-actions { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; gap: 10px; }
.hero-label { font-size: 13px; font-weight: 600; color: var(--purple-900, #3b0764); }
.hero-buttons { display: flex; gap: 6px; }

.editor-section-head {
  display: flex; align-items: center; justify-content: space-between;
  margin: 22px 0 12px; font-size: var(--text-sm, 13px); font-weight: 700; color: var(--gray-strong, #374151);
}
.btn-upload {
  display: inline-block; padding: 8px 13px; border-radius: var(--radius-md, 10px);
  background: var(--purple-50, #f3eefc); color: var(--purple-900, #3b0764);
  font-size: 13px; font-weight: 600; cursor: pointer; border: 1px dashed var(--purple-300, #c4b5fd);
}
.editor-uploading { font-size: 12px; color: var(--gray-muted, #6b7280); margin: 0 0 10px; }

/* 여행이야기 목록 */
.story-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.story-row {
  display: flex; align-items: center; gap: 12px; padding: 8px; cursor: pointer;
  border: 1px solid var(--gray-border, #e5e7eb); border-radius: 12px;
}
.story-row:hover { border-color: var(--purple-900, #3b0764); background: var(--purple-50, #faf8ff); }
.story-row.busy { pointer-events: none; opacity: 0.6; }
.story-thumb { width: 72px; height: 54px; flex-shrink: 0; border-radius: 8px; overflow: hidden; background: #eee; }
.story-thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.story-info { flex: 1; min-width: 0; }
.story-row-title { margin: 0 0 3px; font-size: 14px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.story-row-date { font-size: 12px; color: var(--gray-muted, #6b7280); }
.story-count {
  font-size: 11px; font-weight: 600; color: var(--purple-900, #3b0764);
  background: var(--purple-50, #f3eefc); padding: 3px 8px; border-radius: 999px; white-space: nowrap;
}
.story-arrow { color: var(--gray-muted, #9ca3af); font-size: 18px; }

/* 한 글의 사진 그리드 */
.btn-back { border: none; background: transparent; cursor: pointer; font-size: 13px; font-weight: 600; color: var(--gray-strong, #374151); padding: 8px 0; }
.image-grid { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 12px; }
.image-cell { aspect-ratio: 4 / 3; border-radius: 12px; overflow: hidden; cursor: pointer; border: 1px solid var(--gray-border, #e5e7eb); }
.image-cell:hover { border-color: var(--purple-900, #3b0764); }
.image-cell img { width: 100%; height: 100%; object-fit: cover; display: block; }

/* crop */
.editor-preview-wrap { display: flex; justify-content: center; padding: 8px 0 4px; }
.editor-preview { width: 100%; max-width: 360px; height: auto; touch-action: none; cursor: grab; }
.editor-preview:active { cursor: grabbing; }
.editor-photo-inner { width: 100%; height: 100%; overflow: hidden; }
.editor-photo-inner img { width: 100%; height: 100%; object-fit: cover; display: block; user-select: none; }
.editor-preview-outline { fill: none; stroke: var(--purple-900, #3b0764); stroke-width: 1.5; vector-effect: non-scaling-stroke; }
.editor-zoom { display: flex; align-items: center; gap: 10px; margin: 14px 0 18px; font-size: 12px; color: var(--gray-muted, #6b7280); }
.editor-zoom input { flex: 1; }
.editor-adjust-actions { display: flex; justify-content: space-between; align-items: center; }

.btn-ghost { border: none; background: transparent; cursor: pointer; font-size: 13px; font-weight: 600; color: var(--gray-strong, #374151); padding: 8px 6px; }
.btn-ghost.danger { color: #b91c1c; }
.btn-primary { border: none; border-radius: var(--radius-md, 10px); background: var(--purple-900, #3b0764); color: #fff; font-size: 14px; font-weight: 600; padding: 10px 22px; cursor: pointer; }
.btn-primary:disabled { opacity: 0.6; cursor: default; }

/* 전체화면 */
.fullscreen {
  position: fixed; inset: 0; background: rgba(0, 0, 0, 0.92); z-index: 2000;
  display: flex; align-items: center; justify-content: center; cursor: zoom-out; padding: 24px;
}
.fullscreen img { max-width: 100%; max-height: 100%; object-fit: contain; }
.fullscreen-close { position: fixed; top: 18px; right: 22px; background: transparent; border: none; color: #fff; font-size: 26px; cursor: pointer; }
</style>
