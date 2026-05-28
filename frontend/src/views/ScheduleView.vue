<template>
  <main id="main">
  <section id="screen-schedule">

    <!-- 후보군 사이드바 -->
    <aside class="candidate-sidebar"
           :class="{ 'drop-delete-zone': sidebarDropActive }"
           @dragover.prevent="onSidebarDragOver"
           @dragleave="sidebarDragOver = false"
           @drop="onDropSidebar">
      <div v-if="sidebarDragOver" class="sidebar-delete-hint">여기에 놓으면 삭제</div>

      <template v-if="!sidebarDragOver">
        <div class="schedule-header-row">
          <select v-if="trips.length" v-model="activeTripId" @change="loadTrip"
                  style="border:none;background:transparent;font-weight:600;cursor:pointer;font-size:14px;color:var(--text-primary)">
            <option v-for="t in trips" :key="t.id" :value="t.id">{{ t.title }}</option>
          </select>
          <span v-else class="schedule-name">일정 없음</span>
        </div>

        <div v-if="!activeTrip" style="padding:20px;color:var(--gray-muted);font-size:12px">
          {{ tripsLoading ? '로딩 중...' : '일정을 선택하세요' }}
        </div>

        <template v-else>
          <div v-for="group in cityGroups" :key="group.city" class="city-group">
            <button class="city-header">
              <span class="city-pin">📍</span>
              <span class="city-name">{{ group.city }}</span>
              <span class="city-count">{{ group.candidates.length }}개</span>
              <span class="city-chevron">▾</span>
            </button>
            <div v-for="c in group.candidates" :key="c.id"
                 class="cand-card" :class="{ placed: c.placed, dragging: c.dragging }"
                 draggable="true"
                 @dragstart="onCandDragStart($event, c)"
                 @dragend="onDragEnd">
              <div class="cand-bar" style="background:#534AB7"></div>
              <div class="cand-info">
                <p class="cand-name">{{ c.attractionName }}</p>
                <p class="cand-cat">{{ c.category }}</p>
              </div>
            </div>
          </div>
        </template>

        <button class="btn-add-from-explore" @click="$router.push('/explore')">
          + 관광지 탐색으로 추가하기
        </button>
      </template>
    </aside>

    <!-- 시간표 -->
    <div class="timetable-main">
      <div class="timeline-toolbar">
        <span class="toolbar-trip-name">{{ activeTrip?.title || '일정을 선택하세요' }}</span>
        <span v-if="activeTrip" class="toolbar-trip-meta">{{ nightsLabel }} · {{ activeTrip.memberCount }}명</span>
        <span class="toolbar-spacer"></span>
        <button class="btn-new-trip" @click="openScheduleModal()">+ 새 일정 만들기</button>
        <button class="btn-save-schedule" @click="toast.show('자동 저장됩니다.')">💾 저장</button>
      </div>

      <div class="hint-bar">왼쪽 후보군 카드를 원하는 날짜·시간대로 드래그해서 놓으세요</div>

      <div class="timetable-wrapper" ref="wrapperEl">
        <div class="timetable-header">
          <div class="th-gutter"></div>
          <div v-for="d in days" :key="d.label" class="th-day">
            <span class="th-badge">{{ d.label }}</span>
            <span class="th-date">{{ d.date }}</span>
          </div>
        </div>

        <div class="timetable-body">
          <div class="time-axis">
            <template v-for="h in 24" :key="h">
              <div class="time-mark" :style="{ top: (h - 1) * 60 + 'px' }">{{ String(h - 1).padStart(2,'0') }}:00</div>
              <div class="time-mark half" :style="{ top: (h - 1) * 60 + 30 + 'px' }">{{ String(h - 1).padStart(2,'0') }}:30</div>
            </template>
          </div>

          <div class="day-cols">
            <div v-for="d in days" :key="d.label"
                 class="day-col" :class="{ 'drag-over': d.dragOver }"
                 @dragover.prevent="onDragOver($event, d)"
                 @dragleave="d.dragOver = false"
                 @drop="onDrop($event, d)">
              <div v-if="d.dragOver && dragPreview" class="drop-preview"
                   :style="{ top: dragPreview.top + 'px', height: '120px' }">
                {{ dragState?.data?.attractionName }}
              </div>
              <div v-for="pill in getTransitPills(d)" :key="`pill-${pill.top}`"
                   class="transit-pill-block"
                   :style="{ top: pill.top + 'px', height: Math.max(pill.durationMinutes, 24) + 'px' }">
                <span class="transit-pill-text">
                  {{ pill.durationMinutes }}분 · {{ displayModes(pill.transportMode) }}
                </span>
              </div>

              <div v-for="ev in d.events" :key="ev.id"
                   class="event-block" :data-color="ev.color"
                   :class="{ 'event-dragging': ev.dragging }"
                   draggable="true"
                   :style="{ top: ev.top + 'px', height: ev.height + 'px' }"
                   @dragstart="onEventDragStart($event, ev, d)"
                   @dragend="onDragEnd">
                <span class="event-name">{{ ev.name }}</span>
                <span class="event-time">{{ ev.timeLabel }}</span>
                <button class="event-del" @click.stop="removeEvent(d, ev)">✕</button>
                <div class="resize-handle" @mousedown.stop="onResizeStart($event, ev)"></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

  </section>
  </main>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, inject } from 'vue'
import { useToastStore } from '@/stores/toast'
import { tripApi } from '@/api/trip'

const toast = useToastStore()
const openScheduleModal = inject('openScheduleModal')
const wrapperEl = ref(null)

const HOUR_PX = 60
const SNAP = 30

const trips = ref([])
const tripsLoading = ref(false)
const activeTripId = ref(null)
const activeTrip = ref(null)
const candidates = ref([])
const days = ref([])

// drag state
let dragState = null // { type: 'candidate'|'event', data, fromDay? }
const dragPreview = ref(null)
const sidebarDragOver = ref(false)
const sidebarDropActive = computed(() => sidebarDragOver.value && dragState?.type === 'event')

const TRANSPORT_DISPLAY = {
  BUS: '버스', SUBWAY: '지하철', RAIL: 'KTX/기차', EXPRESSBUS: '고속버스',
  WALK: '도보', CAR: '자동차', AIRPLANE: '항공', FERRY: '해운', NONE: '-',
}
function displayModes(modeStr) {
  if (!modeStr) return ''
  return modeStr.split(',').map(m => TRANSPORT_DISPLAY[m] || m).join(' → ')
}

const SIDO_NAME = {
  1:'서울', 2:'인천', 3:'대전', 4:'대구', 5:'광주', 6:'부산', 7:'울산', 8:'세종',
  31:'경기', 32:'강원', 33:'충북', 34:'충남', 35:'경북', 36:'경남', 37:'전북', 38:'전남', 39:'제주',
}

const cityGroups = computed(() => {
  const groups = {}
  for (const c of candidates.value) {
    const city = c.cityName || SIDO_NAME[c.cityCode] || '기타'
    if (!groups[city]) groups[city] = []
    groups[city].push(c)
  }
  return Object.entries(groups).map(([city, cands]) => ({ city, candidates: cands }))
})

const nightsLabel = computed(() => {
  if (!activeTrip.value) return ''
  const s = new Date(activeTrip.value.startDate + 'T00:00:00')
  const e = new Date(activeTrip.value.endDate + 'T00:00:00')
  const nights = Math.round((e - s) / 86400000)
  return `${nights}박 ${nights + 1}일`
})

function timeToTop(timeStr) {
  if (!timeStr) return 540
  const [h, m] = timeStr.split(':').map(Number)
  return h * 60 + m
}

function topToTime(top) {
  const h = Math.floor(top / 60) % 24
  const m = top % 60
  return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}`
}

function addMins(timeStr, mins) {
  const [h, m] = timeStr.split(':').map(Number)
  const total = h * 60 + m + (mins || 120)
  return `${String(Math.floor(total / 60) % 24).padStart(2,'0')}:${String(total % 60).padStart(2,'0')}`
}

function buildDays(trip) {
  const start = new Date(trip.startDate + 'T00:00:00')
  const end = new Date(trip.endDate + 'T00:00:00')
  const result = []
  const allBlocks = trip.candidates.flatMap(c => c.blocks || [])
  const DAY_NAMES = ['일','월','화','수','목','금','토']

  for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
    const dateStr = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
    const dayNum = result.length + 1
    const dateLabel = `${String(d.getMonth()+1).padStart(2,'0')}.${String(d.getDate()).padStart(2,'0')} ${DAY_NAMES[d.getDay()]}`
    const dayBlocks = allBlocks.filter(b => b.tripDate === dateStr)

    result.push({
      label: `Day ${dayNum}`,
      date: dateLabel,
      isoDate: dateStr,
      dragOver: false,
      events: dayBlocks.map(b => {
        const cand = trip.candidates.find(c => c.id === b.candidateId)
        return buildEvent(b, cand)
      }),
    })
  }
  return result
}

function buildEvent(b, cand) {
  const top = timeToTop(b.startTime)
  const height = b.durationMinutes || 120
  const startStr = b.startTime ? b.startTime.slice(0, 5) : topToTime(top)
  return {
    id: b.id,
    candidateId: b.candidateId,
    tripDate: b.tripDate,
    displayOrder: b.displayOrder ?? 1,
    name: cand?.attractionName || '',
    color: 'purple',
    top,
    height,
    timeLabel: `${startStr} – ${addMins(startStr, height)}`,
    dragging: false,
    transitDurationMinutes: b.transitDurationMinutes ?? null,
    transitMode: b.transitMode ?? null,
  }
}

function getTransitPills(day) {
  const sorted = [...day.events].sort((a, b) => a.top - b.top)
  const pills = []
  for (let i = 1; i < sorted.length; i++) {
    const prev = sorted[i - 1]
    const curr = sorted[i]
    if (curr.transitDurationMinutes) {
      pills.push({
        top: prev.top + prev.height,
        durationMinutes: curr.transitDurationMinutes,
        transportMode: curr.transitMode,
        transferCount: 0,
      })
    }
  }
  return pills
}

async function loadTrip() {
  if (!activeTripId.value) return
  try {
    const trip = await tripApi.get(activeTripId.value)
    activeTrip.value = trip
    candidates.value = trip.candidates.map(c => ({
      ...c,
      placed: (c.blocks?.length ?? 0) > 0,
      dragging: false,
    }))
    days.value = buildDays(trip)
  } catch {
    toast.show('일정 로드 실패')
  }
}

// ── 드래그 시작 ──
function onCandDragStart(e, candidate) {
  dragState = { type: 'candidate', data: candidate }
  candidate.dragging = true
  e.dataTransfer.effectAllowed = 'move'
}

function onEventDragStart(e, ev, day) {
  if (resizeState) { e.preventDefault(); return }
  dragState = { type: 'event', data: ev, fromDay: day }
  ev.dragging = true
  e.dataTransfer.effectAllowed = 'move'
}

// ── 리사이즈 (체류 시간 조정) ──
let resizeState = null // { ev, startY, startHeight }

function onResizeStart(e, ev) {
  resizeState = { ev, startY: e.clientY, startHeight: ev.height }
  document.addEventListener('mousemove', onResizeMove)
  document.addEventListener('mouseup', onResizeEnd)
}

function onResizeMove(e) {
  if (!resizeState) return
  const { ev, startY, startHeight } = resizeState
  const delta = e.clientY - startY
  ev.height = Math.max(SNAP, Math.round((startHeight + delta) / SNAP) * SNAP)
  const startStr = topToTime(ev.top)
  ev.timeLabel = `${startStr} – ${addMins(startStr, ev.height)}`
}

async function onResizeEnd() {
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', onResizeEnd)
  if (!resizeState) return
  const { ev } = resizeState
  resizeState = null
  try {
    await tripApi.updateBlock(activeTripId.value, ev.id, {
      tripDate: ev.tripDate,
      startTime: topToTime(ev.top) + ':00',
      durationMinutes: ev.height,
      displayOrder: ev.displayOrder ?? 1,
    })
  } catch (err) {
    toast.show(err.message || '체류 시간 수정 실패')
  }
}

function onDragEnd() {
  if (dragState?.type === 'candidate') dragState.data.dragging = false
  if (dragState?.type === 'event') dragState.data.dragging = false
  days.value.forEach(d => { d.dragOver = false })
  sidebarDragOver.value = false
  dragPreview.value = null
  dragState = null
}

// ── 타임라인 드래그오버 ──
function onDragOver(e, day) {
  if (!dragState) return
  day.dragOver = true
  const relY = e.clientY - e.currentTarget.getBoundingClientRect().top + wrapperEl.value.scrollTop
  dragPreview.value = { top: Math.round(relY / SNAP) * SNAP }
}

// ── 타임라인 드롭 ──
async function onDrop(e, day) {
  if (!dragState) return
  day.dragOver = false
  const relY = e.clientY - e.currentTarget.getBoundingClientRect().top + wrapperEl.value.scrollTop
  const top = Math.round(relY / SNAP) * SNAP
  const startTime = topToTime(top)

  if (dragState.type === 'candidate') {
    await dropCandidate(day, top, startTime)
  } else {
    await moveEvent(day, top, startTime)
  }

  dragPreview.value = null
  dragState = null
}

async function dropCandidate(day, top, startTime) {
  const candidate = dragState.data
  try {
    await tripApi.placeBlock(activeTripId.value, {
      candidateId: candidate.id,
      tripDate: day.isoDate,
      startTime: startTime + ':00',
      durationMinutes: 120,
      displayOrder: day.events.length + 1,
    })
    toast.show(`"${candidate.attractionName}" 추가됐어요`)
    await loadTrip()
  } catch (err) {
    toast.show(err.message || '추가 실패')
  }
}

async function moveEvent(day, top, startTime) {
  const { data: ev, fromDay } = dragState
  if (fromDay === day && top === ev.top) return
  try {
    await tripApi.updateBlock(activeTripId.value, ev.id, {
      tripDate: day.isoDate,
      startTime: startTime + ':00',
      durationMinutes: ev.height,
      displayOrder: fromDay === day ? ev.displayOrder ?? 1 : day.events.length + 1,
    })
    await loadTrip()
  } catch (err) {
    toast.show(err.message || '이동 실패')
  }
}

// ── 사이드바 드롭 (이벤트 삭제) ──
function onSidebarDragOver() {
  sidebarDragOver.value = dragState?.type === 'event'
}

async function onDropSidebar() {
  if (dragState?.type !== 'event') { sidebarDragOver.value = false; return }
  const { data: ev, fromDay } = dragState
  sidebarDragOver.value = false
  await removeEventFrom(fromDay, ev)
  dragState = null
}

// ── 이벤트 삭제 공통 ──
async function removeEventFrom(day, ev) {
  try {
    await tripApi.removeBlock(activeTripId.value, ev.id)
    toast.show('장소를 일정에서 제거했어요')
    await loadTrip()
  } catch (err) {
    toast.show(err.message || '삭제 실패')
  }
}

async function removeEvent(day, ev) {
  await removeEventFrom(day, ev)
}

onUnmounted(() => {
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', onResizeEnd)
})

onMounted(async () => {
  tripsLoading.value = true
  try {
    trips.value = await tripApi.list()
    if (trips.value.length) {
      activeTripId.value = trips.value[0].id
      await loadTrip()
    }
  } catch {
    // 비로그인
  } finally {
    tripsLoading.value = false
  }
  if (wrapperEl.value) wrapperEl.value.scrollTop = 8 * HOUR_PX
})
</script>
