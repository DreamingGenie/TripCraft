<template>
  <div id="calendar-layout" @click="closePicker">
    <!-- ── 캘린더 본문 ── -->
    <div id="calendar-content">
      <div v-if="!embedded" class="cal-page-title">내 여행 일지</div>

      <!-- 월 네비게이션 -->
      <div class="cal-nav">
        <button class="cal-nav-btn" @click="prevMonth">&#8249;</button>
        <div class="cal-nav-center">
          <button class="cal-month-btn" @click.stop="togglePicker">
            {{ currentYear }}년 {{ currentMonth + 1 }}월
            <span v-if="tripsInMonth.length > 0" class="cal-trip-count">
              ({{ tripsInMonth.length }})
            </span>
          </button>

          <!-- 연도/월 선택 피커 -->
          <div v-if="showPicker" class="cal-picker" @click.stop>
            <div class="cal-picker-year">
              <button @click="pickerYear--">&#8249;</button>
              <span>{{ pickerYear }}년</span>
              <button @click="pickerYear++">&#8250;</button>
            </div>
            <div class="cal-picker-months">
              <button
                v-for="m in 12" :key="m"
                class="cal-picker-month-btn"
                :class="{ active: m - 1 === currentMonth && pickerYear === currentYear }"
                @click="goToMonth(pickerYear, m - 1)"
              >{{ m }}월</button>
            </div>
          </div>
        </div>
        <button class="cal-nav-btn" @click="nextMonth">&#8250;</button>
      </div>

      <!-- 캘린더 그리드 -->
      <div class="cal-body">
        <!-- 요일 헤더 -->
        <div class="cal-dow-row">
          <div
            v-for="(d, i) in ['일','월','화','수','목','금','토']"
            :key="d"
            class="cal-dow"
            :class="{ 'dow-sun': i === 0, 'dow-sat': i === 6 }"
          >{{ d }}</div>
        </div>

        <!-- 주(週) 반복 -->
        <div v-for="(week, wi) in weeksWithStrips" :key="wi" class="cal-week">
          <!-- 날짜 셀 행 -->
          <div class="cal-dates-row">
            <div
              v-for="(day, di) in week.days"
              :key="di"
              class="cal-cell"
              :class="{
                'other-month': day.otherMonth,
                'is-today': isToday(day),
                'is-selected': selectedDate && isSameDay(day, selectedDate),
                'is-sun': di === 0,
                'is-sat': di === 6,
              }"
              @click="selectDay(day)"
            >
              <span class="cal-date-num">{{ day.date }}</span>
            </div>
          </div>

          <!-- 일정 띠 (스트립) -->
          <div v-if="week.strips.length > 0" class="cal-strips-container">
            <div v-for="(lane, li) in week.strips" :key="li" class="cal-strip-lane">
              <div
                v-for="strip in lane"
                :key="strip.tripId"
                class="cal-strip"
                :class="[
                  `trip-color-${strip.colorIdx}`,
                  strip.isStart ? 'strip-left' : '',
                  strip.isEnd   ? 'strip-right' : '',
                ]"
                :style="{ gridColumn: `${strip.startCol} / ${strip.endCol + 1}` }"
                :title="strip.title"
              >
                <span v-if="strip.isStart" class="strip-title">{{ strip.title }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 로딩 / 빈 상태 -->
      <div v-if="loading" class="cal-loading">일정을 불러오는 중...</div>
      <div v-else-if="!loading && trips.length === 0" class="cal-empty">
        등록된 여행 일정이 없어요.
        <RouterLink to="/discover" class="cal-empty-link">일정 만들러 가기 →</RouterLink>
      </div>
    </div>

    <!-- ── 날짜 상세 패널 ── -->
    <transition name="panel-slide">
      <div v-if="selectedDate" class="cal-panel">
        <div class="panel-header">
          <div class="panel-date-label">
            {{ selectedDate.year }}년 {{ selectedDate.month + 1 }}월 {{ selectedDate.date }}일
          </div>
          <button class="panel-close" @click="selectedDate = null" aria-label="닫기">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M18 6 6 18M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div class="panel-body">
          <div v-if="loadingPanel" class="panel-loading">불러오는 중...</div>
          <div v-else-if="selectedDayTrips.length === 0" class="panel-empty">
            이 날의 여행 일정이 없어요.
          </div>
          <template v-else>
            <div
              v-for="tripDay in selectedDayTrips"
              :key="tripDay.id"
              class="panel-trip"
              :class="`trip-color-${tripDay.colorIdx}`"
            >
              <div class="panel-trip-header">
                <div class="panel-trip-dot"></div>
                <div class="panel-trip-info">
                  <div class="panel-trip-title">{{ tripDay.title }}</div>
                  <div class="panel-trip-dates">
                    {{ tripDay.startDate }} ~ {{ tripDay.endDate }}
                  </div>
                </div>
              </div>

              <div v-if="tripDay.blocks.length === 0" class="panel-trip-empty">
                확정된 방문지가 없어요.
              </div>
              <div v-else class="panel-blocks">
                <div v-for="(block, bi) in tripDay.blocks" :key="bi" class="panel-block">
                  <span class="panel-block-time">{{ block.startTime }}</span>
                  <span class="panel-block-name">{{ block.attractionName }}</span>
                  <span class="panel-block-dur">{{ block.durationMinutes }}분</span>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { tripApi } from '@/api/trip'

defineProps({
  // TripsView 달력 토글에서 재사용 시 페이지 타이틀을 숨긴다.
  embedded: { type: Boolean, default: false },
})

const today = new Date()

const currentYear  = ref(today.getFullYear())
const currentMonth = ref(today.getMonth())
const showPicker   = ref(false)
const pickerYear   = ref(today.getFullYear())

const trips          = ref([])
const loading        = ref(false)
const selectedDate   = ref(null)
const selectedDayTrips = ref([])
const loadingPanel   = ref(false)
const blockCache     = ref({}) // tripId → summaryData

// ── 날짜 유틸 ──────────────────────────────────────────────────

function parseDate(str) {
  const [y, m, d] = str.split('-').map(Number)
  return new Date(y, m - 1, d)
}

function dayToDate(day) {
  return new Date(day.year, day.month, day.date)
}

function isToday(day) {
  return (
    day.year  === today.getFullYear() &&
    day.month === today.getMonth() &&
    day.date  === today.getDate()
  )
}

function isSameDay(a, b) {
  return a.year === b.year && a.month === b.month && a.date === b.date
}

// ── 여행 목록 파생 ──────────────────────────────────────────────

const tripColorMap = computed(() => {
  const map = {}
  trips.value.forEach((t, i) => { map[t.id] = i % 6 })
  return map
})

const tripsInMonth = computed(() => {
  const start = new Date(currentYear.value, currentMonth.value, 1)
  const end   = new Date(currentYear.value, currentMonth.value + 1, 0)
  return trips.value.filter(t => {
    const ts = parseDate(t.startDate)
    const te = parseDate(t.endDate)
    return ts <= end && te >= start
  })
})

// ── 주(週) 계산 ────────────────────────────────────────────────

const weeks = computed(() => {
  const y = currentYear.value
  const m = currentMonth.value
  const firstDow      = new Date(y, m, 1).getDay()
  const lastDateOfMon = new Date(y, m + 1, 0).getDate()
  const cells = []

  // 이전 달 날짜
  const prevLastDate = new Date(y, m, 0).getDate()
  for (let i = firstDow - 1; i >= 0; i--) {
    const pm = m === 0 ? 11 : m - 1
    const py = m === 0 ? y - 1 : y
    cells.push({ year: py, month: pm, date: prevLastDate - i, otherMonth: true })
  }

  // 이번 달 날짜
  for (let d = 1; d <= lastDateOfMon; d++) {
    cells.push({ year: y, month: m, date: d, otherMonth: false })
  }

  // 다음 달 날짜
  const nm = m === 11 ? 0  : m + 1
  const ny = m === 11 ? y + 1 : y
  let nd = 1
  while (cells.length % 7 !== 0) {
    cells.push({ year: ny, month: nm, date: nd++, otherMonth: true })
  }

  const result = []
  for (let i = 0; i < cells.length; i += 7) result.push(cells.slice(i, i + 7))
  return result
})

// ── 스트립 레인 계산 ────────────────────────────────────────────

function computeStripsForWeek(week) {
  const weekDates = week.map(d => dayToDate(d))
  const weekStart = weekDates[0]
  const weekEnd   = weekDates[6]

  const overlapping = trips.value.filter(t => {
    const ts = parseDate(t.startDate)
    const te = parseDate(t.endDate)
    return ts <= weekEnd && te >= weekStart
  })
  if (!overlapping.length) return []

  // 각 여행의 스트립 범위 계산
  const tripInfo = {}
  for (const t of overlapping) {
    const ts = parseDate(t.startDate)
    const te = parseDate(t.endDate)
    let startCol = null, endCol = null
    for (let i = 0; i < 7; i++) {
      const d = weekDates[i]
      if (d >= ts && d <= te) {
        if (startCol === null) startCol = i + 1
        endCol = i + 1
      }
    }
    if (startCol === null) continue
    const isStart = weekDates[startCol - 1].getTime() === ts.getTime()
    const isEnd   = weekDates[endCol - 1].getTime()   === te.getTime()
    tripInfo[t.id] = { startCol, endCol, isStart, isEnd }
  }

  // 레인 배정 (겹치지 않는 여행끼리 같은 레인)
  const lanes     = []
  const tripLanes = {}
  for (const t of overlapping) {
    const info = tripInfo[t.id]
    if (!info) continue
    const { startCol, endCol } = info
    let li = 0
    while (true) {
      if (!lanes[li]) { lanes[li] = []; break }
      const conflict = lanes[li].some(r => !(r.endCol < startCol || r.startCol > endCol))
      if (!conflict) break
      li++
    }
    if (!lanes[li]) lanes[li] = []
    lanes[li].push({ startCol, endCol })
    tripLanes[t.id] = li
  }

  // 레인별 스트립 데이터
  const maxLane = Math.max(...Object.values(tripLanes))
  const result  = []
  for (let l = 0; l <= maxLane; l++) {
    const lane = overlapping
      .filter(t => tripLanes[t.id] === l && tripInfo[t.id])
      .map(t => ({
        tripId:   t.id,
        title:    t.title,
        colorIdx: tripColorMap.value[t.id],
        ...tripInfo[t.id],
      }))
    result.push(lane)
  }
  return result
}

const weeksWithStrips = computed(() =>
  weeks.value.map(week => ({
    days:   week,
    strips: computeStripsForWeek(week),
  }))
)

// ── 날짜 선택 & 상세 패널 ───────────────────────────────────────

async function selectDay(day) {
  selectedDate.value = day
  const dayDate = dayToDate(day)

  const matching = trips.value.filter(t => {
    const ts = parseDate(t.startDate)
    const te = parseDate(t.endDate)
    return ts <= dayDate && te >= dayDate
  })

  loadingPanel.value  = true
  selectedDayTrips.value = []

  for (const trip of matching) {
    let summary = blockCache.value[trip.id]
    if (!summary) {
      try {
        summary = await tripApi.getBlocksSummary(trip.id)
        blockCache.value[trip.id] = summary
      } catch {
        summary = { days: [] }
      }
    }

    const dateKey    = `${day.year}-${String(day.month + 1).padStart(2, '0')}-${String(day.date).padStart(2, '0')}`
    const daySummary = summary.days?.find(d => d.date === dateKey)

    selectedDayTrips.value.push({
      id:        trip.id,
      title:     trip.title,
      startDate: trip.startDate,
      endDate:   trip.endDate,
      colorIdx:  tripColorMap.value[trip.id],
      blocks:    daySummary?.blocks ?? [],
    })
  }

  loadingPanel.value = false
}

// ── 네비게이션 ──────────────────────────────────────────────────

function prevMonth() {
  if (currentMonth.value === 0) { currentMonth.value = 11; currentYear.value-- }
  else currentMonth.value--
  pickerYear.value = currentYear.value
  selectedDate.value = null
}

function nextMonth() {
  if (currentMonth.value === 11) { currentMonth.value = 0; currentYear.value++ }
  else currentMonth.value++
  pickerYear.value = currentYear.value
  selectedDate.value = null
}

function goToMonth(year, month) {
  currentYear.value  = year
  currentMonth.value = month
  showPicker.value   = false
  selectedDate.value = null
}

function togglePicker() {
  pickerYear.value = currentYear.value
  showPicker.value = !showPicker.value
}

function closePicker() {
  showPicker.value = false
}

// ── 초기 로드 ───────────────────────────────────────────────────

onMounted(async () => {
  loading.value = true
  try {
    trips.value = await tripApi.list()
  } catch {
    trips.value = []
  } finally {
    loading.value = false
  }
})
</script>
