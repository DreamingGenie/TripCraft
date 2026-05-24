<template>
  <main id="main">
  <section id="screen-schedule">

    <!-- 후보군 사이드바 -->
    <aside class="candidate-sidebar">
      <div class="schedule-header-row">
        <span class="schedule-name">제주 여름 여행</span>
        <button class="schedule-switch-btn">▾</button>
      </div>

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
          <div class="cand-bar" :style="{ background: c.color }"></div>
          <div class="cand-info">
            <p class="cand-name" :class="{ placed: c.placed }">{{ c.name }}</p>
            <p class="cand-cat">{{ c.category }}</p>
          </div>
          <span v-if="c.favorited" class="star" style="font-size:11px">★</span>
        </div>
      </div>

      <button class="btn-add-from-explore" @click="$router.push('/explore')">
        + 관광지 탐색으로 추가하기
      </button>
    </aside>

    <!-- 시간표 -->
    <div class="timetable-main">
      <div class="timeline-toolbar">
        <span class="toolbar-trip-name">제주 여름 여행</span>
        <span class="toolbar-trip-meta">3박 4일 · 2명</span>
        <span class="toolbar-spacer"></span>
        <button class="btn-new-trip" @click="openScheduleModal()">+ 새 일정 만들기</button>
        <button class="btn-save-schedule" @click="toast.show('일정이 저장됐어요.')">💾 저장</button>
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
                {{ dragging?.name }}
              </div>
              <div v-for="ev in d.events" :key="ev.id"
                   class="event-block" :data-color="ev.color"
                   :style="{ top: ev.top + 'px', height: ev.height + 'px' }">
                <span class="event-name">{{ ev.name }}</span>
                <span class="event-time">{{ ev.timeLabel }}</span>
                <button class="event-del" @click="removeEvent(d, ev)">✕</button>
              </div>
              <div v-for="tr in d.transits" :key="tr.id"
                   class="transit-block" :style="{ top: tr.top + 'px', height: tr.height + 'px' }">
                {{ tr.label }}
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
import { ref, reactive, onMounted, inject } from 'vue'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const openScheduleModal = inject('openScheduleModal')
const wrapperEl = ref(null)

const HOUR_PX = 60
const SNAP = 30

const days = reactive([
  { label: 'Day 1', date: '07.10 목', dragOver: false, events: [
    { id: 1, name: '불국사',   color: 'purple', top: 540, height: 120, timeLabel: '09:00 – 11:00' },
    { id: 2, name: '광장시장', color: 'pink',   top: 690, height: 90,  timeLabel: '11:30 – 13:00' },
  ], transits: [{ id: 1, top: 660, height: 25, label: '🚌 버스 25분' }] },
  { label: 'Day 2', date: '07.11 금', dragOver: false, events: [
    { id: 3, name: '첨성대',   color: 'teal',   top: 600, height: 90,  timeLabel: '10:00 – 11:30' },
    { id: 4, name: '동궁과월지',color: 'purple', top: 840, height: 120, timeLabel: '14:00 – 16:00' },
  ], transits: [{ id: 2, top: 690, height: 15, label: '🚶 도보 15분' }] },
  { label: 'Day 3', date: '07.12 토', dragOver: false, events: [], transits: [] },
  { label: 'Day 4', date: '07.13 일', dragOver: false, events: [], transits: [] },
])

const cityGroups = reactive([
  { city: '서울', candidates: [
    { id: 1, name: '경복궁',       category: '관광지 · 서울', color: '#534AB7', favorited: true,  placed: false, dragging: false },
    { id: 2, name: '광장시장',     category: '음식점 · 서울', color: '#993556', favorited: false, placed: true,  dragging: false },
    { id: 3, name: '남산서울타워', category: '관광지 · 서울', color: '#534AB7', favorited: true,  placed: false, dragging: false },
  ]},
  { city: '경주', candidates: [
    { id: 4, name: '불국사',       category: '관광지 · 경주', color: '#534AB7', favorited: true,  placed: true,  dragging: false },
    { id: 5, name: '석굴암',       category: '관광지 · 경주', color: '#534AB7', favorited: false, placed: false, dragging: false },
    { id: 6, name: '첨성대',       category: '문화시설 · 경주',color: '#0F6E56', favorited: false, placed: true,  dragging: false },
    { id: 7, name: '동궁과월지',   category: '관광지 · 경주', color: '#534AB7', favorited: false, placed: true,  dragging: false },
  ]},
])

let dragging = null
const dragPreview = ref(null)

const colorMap = { '#534AB7': 'purple', '#993556': 'pink', '#0F6E56': 'teal', '#185FA5': 'blue', '#854F0B': 'amber' }

function onDragStart(e, candidate) {
  dragging = candidate
  candidate.dragging = true
  e.dataTransfer.effectAllowed = 'move'
}

function onDragEnd(candidate) {
  candidate.dragging = false
  days.forEach(d => { d.dragOver = false })
  dragPreview.value = null
}

function onDragOver(e, day) {
  if (!dragging) return
  day.dragOver = true
  const col = e.currentTarget
  const body = col.closest('.timetable-body')
  const wrapper = wrapperEl.value
  const relY = e.clientY - col.getBoundingClientRect().top + wrapper.scrollTop - body.offsetTop
  const snapped = Math.round(relY / SNAP) * SNAP
  dragPreview.value = { top: snapped }
}

function onDrop(e, day) {
  if (!dragging) return
  day.dragOver = false
  const col = e.currentTarget
  const body = col.closest('.timetable-body')
  const wrapper = wrapperEl.value
  const relY = e.clientY - col.getBoundingClientRect().top + wrapper.scrollTop - body.offsetTop
  const top = Math.round(relY / SNAP) * SNAP
  const startMin = top
  const endMin = startMin + 120
  day.events.push({
    id: Date.now(), name: dragging.name,
    color: colorMap[dragging.color] || 'purple',
    top, height: 120,
    timeLabel: `${minsToTime(startMin)} – ${minsToTime(endMin)}`,
  })
  dragging.placed = true
  toast.show(`"${dragging.name}" 일정에 추가됐어요`)
  dragPreview.value = null
  dragging = null
}

function removeEvent(day, ev) {
  const idx = day.events.indexOf(ev)
  if (idx !== -1) { day.events.splice(idx, 1); toast.show('장소가 삭제됐어요') }
}

function minsToTime(px) {
  const h = Math.floor(px / 60) % 24
  const m = px % 60
  return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}`
}

onMounted(() => {
  if (wrapperEl.value) wrapperEl.value.scrollTop = 8 * HOUR_PX
})
</script>
