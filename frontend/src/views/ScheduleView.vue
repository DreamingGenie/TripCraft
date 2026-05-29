<template>
  <main id="main">
  <section id="screen-schedule">

    <!-- TOOLBAR -->
    <div class="schedule-toolbar">
      <button class="toolbar-toggle" :class="{ open: sidebarOpen }"
              @click="sidebarOpen = !sidebarOpen" aria-label="후보군 사이드바 토글">
        <span class="toolbar-toggle-icon">
          <span></span><span></span><span></span>
        </span>
      </button>

      <template v-if="trips.length">
        <div class="toolbar-select-wrap">
          <select class="toolbar-select" v-model="activeTripId" @change="loadTrip">
            <option v-for="t in trips" :key="t.id" :value="t.id">{{ t.title }}</option>
          </select>
          <span class="toolbar-select-caret">▼</span>
        </div>
        <span v-if="activeTrip" class="toolbar-divider"></span>
        <span v-if="activeTrip" class="toolbar-meta">
          {{ nightsLabel }}
          <span class="meta-dot">·</span>
          {{ activeTrip.memberCount }}명
        </span>
      </template>
      <span v-else class="toolbar-trip-name-static">일정 없음</span>

      <span class="toolbar-spacer"></span>

      <button class="btn-save-schedule" @click="toast.show('자동 저장됩니다.')">💾 저장</button>
      <button class="btn-new-trip" @click="openScheduleModal()">+ 새 일정</button>
    </div>

    <!-- BODY: 사이드바 + 시간표 -->
    <div class="schedule-body">
      <!-- 후보군 사이드바 -->
      <aside class="candidate-sidebar"
             :class="{ collapsed: !sidebarOpen, 'drop-delete-zone': sidebarDropActive }"
             @dragover.prevent="onSidebarDragOver"
             @dragleave="sidebarDragOver = false"
             @drop="onDropSidebar">
        <div v-if="sidebarDragOver" class="sidebar-delete-hint">여기에 놓으면 삭제</div>

        <template v-if="!sidebarDragOver">
          <div class="cand-sidebar-header">
            <span class="cand-sidebar-title">후보군</span>
            <span v-if="candidates.length" class="cand-sidebar-count">{{ candidates.length }}</span>
          </div>

          <div class="cand-sidebar-body">
            <div v-if="!activeTrip" class="cand-empty">
              {{ tripsLoading ? '로딩 중...' : '일정을 선택하세요' }}
            </div>

            <template v-else>
              <div v-if="!candidates.length" class="cand-empty">
                아직 후보가 없어요.<br>아래에서 추가해보세요.
              </div>
              <div v-for="group in cityGroups" :key="group.city" class="city-group">
                <button class="city-header" @click="toggleCity(group.city)">
                  <span class="city-chevron" :class="{ open: !collapsedCities[group.city] }">▶</span>
                  <span class="city-name">{{ group.city }}</span>
                  <span class="city-count">{{ group.total }}</span>
                </button>
                <Transition name="tree-slide">
                  <div v-if="!collapsedCities[group.city]" class="city-body">
                    <template v-for="sg in group.sgGroups" :key="sg.sg || '__none__'">
                      <template v-if="sg.sg">
                        <button class="sigungu-header" @click.stop="toggleSigungu(group.city, sg.sg)">
                          <span class="cat-chevron" :class="{ open: !collapsedSigungus[`${group.city}__${sg.sg}`] }">▶</span>
                          <span class="sigungu-name">{{ sg.sg }}</span>
                        </button>
                        <Transition name="tree-slide">
                          <div v-if="!collapsedSigungus[`${group.city}__${sg.sg}`]" class="sigungu-body">
                            <div v-for="catGroup in sg.catGroups" :key="catGroup.cat" class="cat-group">
                              <button class="cat-header" @click.stop="toggleCat(`${group.city}__${sg.sg}`, catGroup.cat)">
                                <span class="cat-chevron" :class="{ open: !collapsedCats[`${group.city}__${sg.sg}__${catGroup.cat}`] }">▶</span>
                                <span class="cat-name">{{ catGroup.cat }}</span>
                                <span class="cat-count">({{ catGroup.candidates.length }})</span>
                              </button>
                              <Transition name="tree-slide">
                                <div v-if="!collapsedCats[`${group.city}__${sg.sg}__${catGroup.cat}`]" class="cat-body">
                                  <div v-for="c in catGroup.candidates" :key="c.id"
                                       class="cand-row" :class="{ placed: c.placed }"
                                       draggable="true"
                                       @dragstart="onCandDragStart($event, c)"
                                       @dragend="onDragEnd">
                                    <span class="drag-dot">⠿</span>
                                    <span class="cand-row-name">{{ c.attractionName }}</span>
                                  </div>
                                </div>
                              </Transition>
                            </div>
                          </div>
                        </Transition>
                      </template>
                      <template v-else>
                        <div v-for="catGroup in sg.catGroups" :key="catGroup.cat" class="cat-group">
                          <button class="cat-header" @click.stop="toggleCat(group.city, catGroup.cat)">
                            <span class="cat-chevron" :class="{ open: !collapsedCats[`${group.city}__${catGroup.cat}`] }">▶</span>
                            <span class="cat-name">{{ catGroup.cat }}</span>
                            <span class="cat-count">({{ catGroup.candidates.length }})</span>
                          </button>
                          <Transition name="tree-slide">
                            <div v-if="!collapsedCats[`${group.city}__${catGroup.cat}`]" class="cat-body">
                              <div v-for="c in catGroup.candidates" :key="c.id"
                                   class="cand-row" :class="{ placed: c.placed }"
                                   draggable="true"
                                   @dragstart="onCandDragStart($event, c)"
                                   @dragend="onDragEnd">
                                <span class="drag-dot">⠿</span>
                                <span class="cand-row-name">{{ c.attractionName }}</span>
                              </div>
                            </div>
                          </Transition>
                        </div>
                      </template>
                    </template>
                  </div>
                </Transition>
              </div>
            </template>
          </div>

          <div class="cand-sidebar-footer">
            <RouterLink to="/explore" class="btn-add-from-explore">
              + 관광지 탐색에서 추가하기
            </RouterLink>
          </div>
        </template>
      </aside>

      <!-- 시간표 -->
      <div class="timetable-main">
        <div class="hint-bar">✋ 왼쪽 후보군 카드를 원하는 날짜·시간대로 드래그해서 놓으세요</div>

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
                     :style="{ top: dragPreview.top + 'px', height: dragPreview.height + 'px' }">
                  {{ dragState?.data?.attractionName || dragState?.data?.name }}
                </div>
                <div v-for="pill in getTransitPills(d)" :key="`pill-${pill.top}`"
                     class="transit-pill-block transit-pill-clickable"
                     :class="{ 'transit-pill-none': pill.transportMode === 'NONE' }"
                     :style="{ top: pill.top + 'px', height: Math.max(pill.durationMinutes || 24, 24) + 'px' }"
                     @click="openTransitDetail(pill)">
                  <span class="transit-pill-text">
                    <template v-if="pill.transportMode === 'NONE'">경로 정보 없음</template>
                    <template v-else>{{ pill.durationMinutes }}분 · {{ displayModes(pill.transportMode) }}</template>
                  </span>
                </div>

                <div v-for="ev in d.events" :key="ev.id"
                     class="event-block" :data-color="ev.color"
                     :class="{ 'event-dragging': ev.dragging, 'event-processing': isProcessing && ev.id === processingEvId }"
                     draggable="true"
                     :style="{ top: ev.top + 'px', height: ev.height + 'px' }"
                     @dragstart="onEventDragStart($event, ev, d)"
                     @dragend="onDragEnd">
                  <span class="event-name">{{ ev.name }}</span>
                  <span class="event-time">{{ ev.timeLabel }}</span>
                  <span v-if="isProcessing && ev.id === processingEvId" class="event-spinner"></span>
                  <button class="event-del" @click.stop="removeEvent(d, ev)">✕</button>
                  <div class="resize-handle" @mousedown.stop="onResizeStart($event, ev)"></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

  </section>

  <TransitDetailPanel
    v-if="showTransitDetail"
    :detail="transitDetail"
    :loading="transitDetailLoading"
    @close="showTransitDetail = false; selectedPill = null"
    @select="handleTransitSelect"
  />
  </main>
</template>

<script setup>
import { ref, computed, reactive, onMounted, onUnmounted, inject } from 'vue'
import { useToastStore } from '@/stores/toast'
import { tripApi } from '@/api/trip'
import { getTransitDetail, selectTransitPath } from '@/api/transit'
import TransitDetailPanel from '@/components/TransitDetailPanel.vue'

const toast = useToastStore()
const openScheduleModal = inject('openScheduleModal')
const wrapperEl = ref(null)

const HOUR_PX = 60
const SNAP = 30

// ── UI state (사이드바 토글) ──
const sidebarOpen = ref(true)

const trips = ref([])
const tripsLoading = ref(false)
const activeTripId = ref(null)
const activeTrip = ref(null)
const candidates = ref([])
const days = ref([])

const showTransitDetail = ref(false)
const transitDetail = ref(null)
const transitDetailLoading = ref(false)
const selectedPill = ref(null)

let dragState = null
const dragPreview = ref(null)
const isProcessing = ref(false)
const processingEvId = ref(null)
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

const collapsedCities = reactive({})
const collapsedCats = reactive({})
const collapsedSigungus = reactive({})

function toggleCity(city) { collapsedCities[city] = !collapsedCities[city] }
function toggleCat(key, cat) {
  const k = `${key}__${cat}`
  collapsedCats[k] = !collapsedCats[k]
}
function toggleSigungu(city, sg) {
  const key = `${city}__${sg}`
  collapsedSigungus[key] = !collapsedSigungus[key]
}

const cityGroups = computed(() => {
  const groups = {}
  for (const c of candidates.value) {
    const city = c.cityName || SIDO_NAME[c.cityCode] || '기타'
    const sg = c.sigunguName || '__none__'
    const cat = c.category || '기타'
    if (!groups[city]) groups[city] = {}
    if (!groups[city][sg]) groups[city][sg] = { cats: {} }
    if (!groups[city][sg].cats[cat]) groups[city][sg].cats[cat] = []
    groups[city][sg].cats[cat].push(c)
  }
  return Object.entries(groups).map(([city, sgMap]) => ({
    city,
    total: Object.values(sgMap).reduce((s, sg) =>
      s + Object.values(sg.cats).reduce((cs, arr) => cs + arr.length, 0), 0),
    sgGroups: Object.entries(sgMap).map(([sg, sgData]) => ({
      sg: sg === '__none__' ? null : sg,
      catGroups: Object.entries(sgData.cats).map(([cat, cands]) => ({ cat, candidates: cands })),
    })),
  }))
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
    if (curr.transitMode) {
      const prevCand = candidates.value.find(c => c.id === prev.candidateId)
      const currCand = candidates.value.find(c => c.id === curr.candidateId)
      const pillTop = prev.top + prev.height
      pills.push({
        top: pillTop,
        durationMinutes: curr.transitDurationMinutes,
        transportMode: curr.transitMode,
        fromAttractionId: prevCand?.attractionId,
        toAttractionId: currCand?.attractionId,
        departureHour: Math.min(Math.floor(pillTop / 60), 23),
      })
    }
  }
  return pills
}

async function openTransitDetail(pill) {
  if (pill.transportMode === 'NONE') {
    toast.show('이 구간의 대중교통 경로 정보가 없습니다. 택시 또는 자가용을 이용하세요.')
    return
  }
  if (!pill.fromAttractionId || !pill.toAttractionId) {
    toast.show('경로 정보를 불러올 수 없어요')
    return
  }
  selectedPill.value = pill
  showTransitDetail.value = true
  transitDetailLoading.value = true
  transitDetail.value = null
  try {
    transitDetail.value = await getTransitDetail(pill.fromAttractionId, pill.toAttractionId)
  } catch {
    toast.show('경로 정보를 불러오지 못했어요')
    showTransitDetail.value = false
  } finally {
    transitDetailLoading.value = false
  }
}

async function handleTransitSelect(pathIndex) {
  if (!selectedPill.value) return
  try {
    await selectTransitPath(selectedPill.value.fromAttractionId, selectedPill.value.toAttractionId, pathIndex)
    showTransitDetail.value = false
    selectedPill.value = null
    await loadTrip()
    toast.show('경로가 저장됐어요')
  } catch {
    toast.show('경로 저장 실패')
  }
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

function onCandDragStart(e, candidate) {
  if (isProcessing.value) { e.preventDefault(); return }
  dragState = { type: 'candidate', data: candidate, grabOffsetY: 0 }
  candidate.dragging = true
  e.dataTransfer.effectAllowed = 'move'
}

function onEventDragStart(e, ev, day) {
  if (isProcessing.value || resizeState) { e.preventDefault(); return }
  const grabOffsetY = e.clientY - e.currentTarget.getBoundingClientRect().top
  dragState = { type: 'event', data: ev, fromDay: day, grabOffsetY }
  ev.dragging = true
  e.dataTransfer.effectAllowed = 'move'
}

let resizeState = null

function onResizeStart(e, ev) {
  if (isProcessing.value) return
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
  days.value.forEach(d => {
    d.events.forEach(ev => { ev.dragging = false })
    d.dragOver = false
  })
  if (dragState?.type === 'candidate') dragState.data.dragging = false
  sidebarDragOver.value = false
  dragPreview.value = null
  dragState = null
}

function onDragOver(e, day) {
  if (!dragState) return
  day.dragOver = true
  const relY = e.clientY - e.currentTarget.getBoundingClientRect().top - (dragState.grabOffsetY ?? 0)
  const height = dragState.type === 'event' ? dragState.data.height : 60
  dragPreview.value = { top: Math.round(Math.max(0, relY) / SNAP) * SNAP, height }
}

async function onDrop(e, day) {
  if (!dragState) return
  day.dragOver = false
  const relY = e.clientY - e.currentTarget.getBoundingClientRect().top - (dragState.grabOffsetY ?? 0)
  const top = Math.round(Math.max(0, relY) / SNAP) * SNAP
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

  const tempEv = {
    id: `temp-${Date.now()}`,
    candidateId: candidate.id,
    tripDate: day.isoDate,
    displayOrder: day.events.length + 1,
    name: candidate.attractionName,
    color: 'purple',
    top, height: 60,
    timeLabel: `${startTime} – ${addMins(startTime, 60)}`,
    dragging: false,
    transitDurationMinutes: null, transitMode: null,
  }
  day.events.push(tempEv)
  candidate.placed = true

  isProcessing.value = true
  processingEvId.value = tempEv.id
  try {
    await tripApi.placeBlock(activeTripId.value, {
      candidateId: candidate.id,
      tripDate: day.isoDate,
      startTime: startTime + ':00',
      durationMinutes: 60,
      displayOrder: tempEv.displayOrder,
    })
    toast.show(`"${candidate.attractionName}" 추가됐어요`)
    await loadTrip()
  } catch (err) {
    const idx = day.events.findIndex(e => e.id === tempEv.id)
    if (idx !== -1) day.events.splice(idx, 1)
    candidate.placed = false
    toast.show(err.message || '추가 실패')
  } finally {
    isProcessing.value = false
    processingEvId.value = null
  }
}

async function moveEvent(day, top, startTime) {
  const { data: ev, fromDay } = dragState
  if (fromDay === day && top === ev.top) { ev.dragging = false; return }

  const newDisplayOrder = fromDay === day ? ev.displayOrder ?? 1 : day.events.length + 1
  ev.dragging = false
  const fromIdx = fromDay.events.indexOf(ev)
  if (fromIdx !== -1) fromDay.events.splice(fromIdx, 1)
  ev.top = top
  ev.tripDate = day.isoDate
  ev.timeLabel = `${startTime} – ${addMins(startTime, ev.height)}`
  day.events.push(ev)

  isProcessing.value = true
  processingEvId.value = ev.id
  try {
    await tripApi.updateBlock(activeTripId.value, ev.id, {
      tripDate: day.isoDate,
      startTime: startTime + ':00',
      durationMinutes: ev.height,
      displayOrder: newDisplayOrder,
    })
    await loadTrip()
  } catch (err) {
    toast.show(err.message || '이동 실패')
    await loadTrip()
  } finally {
    isProcessing.value = false
    processingEvId.value = null
  }
}

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

async function removeEventFrom(day, ev) {
  isProcessing.value = true
  try {
    await tripApi.removeBlock(activeTripId.value, ev.id)
    toast.show('장소를 일정에서 제거했어요')
    await loadTrip()
  } catch (err) {
    toast.show(err.message || '삭제 실패')
  } finally {
    isProcessing.value = false
    processingEvId.value = null
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
