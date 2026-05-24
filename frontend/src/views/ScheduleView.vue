<template>
  <main id="main">
  <section id="screen-schedule">

    <!-- 후보군 사이드바 -->
    <aside class="candidate-sidebar">
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
               :draggable="!c.placed"
               @dragstart="onDragStart($event, c)"
               @dragend="onDragEnd(c)">
            <div class="cand-bar" style="background:#534AB7"></div>
            <div class="cand-info">
              <p class="cand-name" :class="{ placed: c.placed }">{{ c.attractionName }}</p>
              <p class="cand-cat">{{ c.category }}</p>
            </div>
          </div>
        </div>
      </template>

      <button class="btn-add-from-explore" @click="$router.push('/explore')">
        + 관광지 탐색으로 추가하기
      </button>
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
                {{ dragging?.attractionName }}
              </div>
              <div v-for="ev in d.events" :key="ev.id"
                   class="event-block" :data-color="ev.color"
                   :style="{ top: ev.top + 'px', height: ev.height + 'px' }">
                <span class="event-name">{{ ev.name }}</span>
                <span class="event-time">{{ ev.timeLabel }}</span>
                <button class="event-del" @click="removeEvent(d, ev)">✕</button>
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
import { ref, computed, onMounted, inject } from 'vue'
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
  const parts = timeStr.split(':')
  const h = parseInt(parts[0])
  const m = parseInt(parts[1])
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
        return {
          id: b.id,
          candidateId: b.candidateId,
          name: cand?.attractionName || '',
          color: 'purple',
          top: timeToTop(b.startTime),
          height: b.durationMinutes || 120,
          timeLabel: b.startTime ? `${b.startTime.slice(0,5)} – ${addMins(b.startTime, b.durationMinutes)}` : '',
          blockId: b.id,
        }
      }),
      transits: [],
    })
  }
  return result
}

async function loadTrip() {
  if (!activeTripId.value) return
  try {
    const trip = await tripApi.get(activeTripId.value)
    activeTrip.value = trip
    candidates.value = trip.candidates.map(c => ({
      ...c,
      placed: c.blocks && c.blocks.length > 0,
      dragging: false,
    }))
    days.value = buildDays(trip)
  } catch {
    toast.show('일정 로드 실패')
  }
}

let dragging = null
const dragPreview = ref(null)

function onDragStart(e, candidate) {
  dragging = candidate
  candidate.dragging = true
  e.dataTransfer.effectAllowed = 'move'
}

function onDragEnd(candidate) {
  candidate.dragging = false
  days.value.forEach(d => { d.dragOver = false })
  dragPreview.value = null
}

function onDragOver(e, day) {
  if (!dragging) return
  day.dragOver = true
  const col = e.currentTarget
  const wrapper = wrapperEl.value
  const relY = e.clientY - col.getBoundingClientRect().top + wrapper.scrollTop
  dragPreview.value = { top: Math.round(relY / SNAP) * SNAP }
}

async function onDrop(e, day) {
  if (!dragging) return
  day.dragOver = false
  const col = e.currentTarget
  const wrapper = wrapperEl.value
  const relY = e.clientY - col.getBoundingClientRect().top + wrapper.scrollTop
  const top = Math.round(relY / SNAP) * SNAP
  const startTime = topToTime(top)

  try {
    const blockId = await tripApi.placeBlock(activeTripId.value, {
      candidateId: dragging.id,
      tripDate: day.isoDate,
      startTime: startTime + ':00',
      durationMinutes: 120,
      displayOrder: day.events.length + 1,
    })
    day.events.push({
      id: blockId,
      candidateId: dragging.id,
      name: dragging.attractionName,
      color: 'purple',
      top,
      height: 120,
      timeLabel: `${startTime} – ${addMins(startTime + ':00', 120)}`,
      blockId,
    })
    dragging.placed = true
    toast.show(`"${dragging.attractionName}" 일정에 추가됐어요`)
  } catch (e) {
    toast.show(e.message || '추가 실패')
  }
  dragPreview.value = null
  dragging = null
}

async function removeEvent(day, ev) {
  try {
    await tripApi.removeBlock(activeTripId.value, ev.blockId || ev.id)
    const idx = day.events.indexOf(ev)
    if (idx !== -1) day.events.splice(idx, 1)
    const cand = candidates.value.find(c => c.id === ev.candidateId)
    if (cand) {
      const hasOtherBlocks = days.value.some(d => d.events.some(e => e.candidateId === ev.candidateId && e.id !== ev.id))
      if (!hasOtherBlocks) cand.placed = false
    }
    toast.show('장소가 삭제됐어요')
  } catch (e) {
    toast.show(e.message || '삭제 실패')
  }
}

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
