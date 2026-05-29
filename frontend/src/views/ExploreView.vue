<template>
  <main id="main">
  <section id="screen-explore">

    <!-- 지도: 전체 배경 -->
    <div class="map-area">
      <div ref="mapEl" class="naver-map"></div>
    </div>

    <!-- 왼쪽 float 패널: 검색 + 필터 + 목록 -->
    <div class="attr-list"
         @dragover.prevent
         @drop="onDropToAttrList">

      <!-- 검색창 -->
      <div class="search-bar">
        <span class="search-icon">🔍</span>
        <input type="text" v-model="searchQuery" placeholder="장소, 도시 검색"
               @keydown.enter="applyFilters" />
      </div>

      <!-- 필터 칩 -->
      <div class="filter-inline">
        <div class="filter-header" @click="filterOpen = !filterOpen">
          <span class="filter-header-label">필터</span>
          <button class="filter-clear-inline"
                  :style="{ visibility: (selectedRegions.length || selectedSigungus.length || selectedCats.length) ? 'visible' : 'hidden' }"
                  @click.stop="clearFilters">초기화</button>
          <span class="filter-chevron" :class="{ open: filterOpen }">▾</span>
        </div>

        <Transition name="tree-slide">
          <div v-show="filterOpen" class="filter-body">
            <div class="filter-row">
              <span class="filter-row-label">지역</span>
              <div class="filter-chips-wrap">
                <button v-for="r in regions" :key="r"
                        class="chip" :class="{ sel: selectedRegions.includes(r) }"
                        @click="selectRegion(r)">{{ r }}</button>
              </div>
            </div>

            <div v-if="currentSigunguList.length" class="filter-row">
              <span class="filter-row-label">시군</span>
              <div class="filter-chips-wrap">
                <button v-for="sg in currentSigunguList" :key="`${sg.sidoCode}:${sg.code}`"
                        class="chip chip-sm" :class="{ sel: selectedSigungus.includes(`${sg.sidoCode}:${sg.code}`) }"
                        @click="toggleSigungu(sg.sidoCode, sg.code)">{{ sg.name }}</button>
              </div>
            </div>

            <div class="filter-row">
              <span class="filter-row-label">분류</span>
              <div class="filter-chips-wrap">
                <button v-for="c in categories" :key="c"
                        class="chip" :class="{ sel: selectedCats.includes(c) }"
                        @click="toggleCatFilter(c)">{{ c }}</button>
              </div>
            </div>
          </div>
        </Transition>
      </div>

      <p class="result-count"><strong>{{ total }}</strong>개의 장소</p>

      <!-- 카드 스크롤 (1열) -->
      <div ref="scrollEl" class="cards-scroll">
        <div v-for="rg in searchResultGroups" :key="rg.region ?? '__flat__'">
          <button v-if="rg.region" class="group-section-header"
                  @click="toggleSearchRegion(rg.region)">
            <span class="group-chevron" :class="{ open: !collapsedSearchRegions[rg.region] }">▶</span>
            {{ rg.region }}
            <span class="group-count">{{ rg.total }}</span>
          </button>
          <Transition name="tree-slide">
            <div v-if="!rg.region || !collapsedSearchRegions[rg.region]"
                 :class="{ 'sg-level-indent': !!rg.region }">
              <div v-for="sg in rg.sgGroups" :key="sg.sg ?? '__nosg__'">
                <button v-if="sg.sg" class="sg-section-header"
                        @click="toggleSearchSigungu(`${rg.region}__${sg.sg}`)">
                  <span class="group-chevron" :class="{ open: !collapsedSearchSigungus[`${rg.region}__${sg.sg}`] }">▶</span>
                  {{ sg.sg }}
                  <span class="group-count">{{ sg.total ?? sg.items.length }}</span>
                </button>
                <Transition name="tree-slide">
                  <div v-if="!sg.sg || !collapsedSearchSigungus[`${rg.region}__${sg.sg}`]"
                       class="group-cards-wrap">
                    <div v-for="cg in toRenderGroups(sg)" :key="cg.cat ?? '__all__'" class="cat-section-wrap">
                      <button v-if="cg.cat" class="cat-section-header"
                              @click="toggleSearchCat(`${rg.region ?? ''}__${sg.sg ?? ''}__${cg.cat}`)">
                        <span class="group-chevron" :class="{ open: !collapsedSearchCats[`${rg.region ?? ''}__${sg.sg ?? ''}__${cg.cat}`] }">▶</span>
                        {{ cg.cat }}
                        <span class="group-count">{{ cg.total ?? cg.items.length }}</span>
                      </button>
                      <Transition name="tree-slide">
                        <div v-if="!cg.cat || !collapsedSearchCats[`${rg.region ?? ''}__${sg.sg ?? ''}__${cg.cat}`]"
                             class="cards-grid">
                          <div v-for="a in cg.items" :key="a.id"
                               class="attr-card" :class="{ candidate: addedIds.has(a.id) }"
                               draggable="true"
                               @dragstart="onCardDragStart($event, a)"
                               @dragend="onCardDragEnd">
                            <div v-if="addedIds.has(a.id)" class="candidate-badge">✓</div>
                            <div class="card-img" :style="{ background: colorFor(a.contentTypeId) }">
                              <img v-if="a.firstImage" :src="a.firstImage" />
                              <span v-else>{{ emojiFor(a.contentTypeId) }}</span>
                            </div>
                            <div class="card-info">
                              <div class="card-name">{{ a.title }}</div>
                              <p class="card-cat">
                                <template v-if="cg.cat">{{ a.sigunguName || a.region }}</template>
                                <template v-else>{{ a.category }} · {{ a.sigunguName || a.region }}</template>
                              </p>
                              <button class="card-add" :class="{ added: addedIds.has(a.id) }"
                                      @click.stop="addedIds.has(a.id) ? removeByAttraction(a.id) : addToTrip(a)">
                                {{ addedIds.has(a.id) ? '× 제거' : '+ 일정에 추가' }}
                              </button>
                            </div>
                          </div>
                        </div>
                      </Transition>
                    </div>
                  </div>
                </Transition>
              </div>
            </div>
          </Transition>
        </div>

        <div v-if="loading" style="padding:20px;text-align:center;color:var(--gray-muted);font-size:12px">로딩 중...</div>
        <div v-else-if="!hasMore && attractions.length" style="padding:14px;text-align:center;color:var(--gray-muted);font-size:11px">모든 장소를 불러왔어요</div>
      </div>
    </div>

    <!-- 우측 하단 트레이: 내 일정 -->
    <div class="trip-tray" :class="{ dragging: isDraggingCard }">
      <button class="tray-header" @click="trayOpen = !trayOpen">
        <span class="tray-icon">📋</span>
        <span class="tray-title">내 일정</span>
        <span class="tray-count">{{ trips.length }}</span>
        <span class="tray-toggle">
          <span>{{ trayOpen ? '접기' : '펼치기' }}</span>
          <span class="tray-toggle-chev" :class="{ open: trayOpen }">▼</span>
        </span>
      </button>

      <Transition name="tray-slide">
        <div v-show="trayOpen" class="tray-body">
          <div v-if="isDraggingCard && trips.length" class="tray-drag-hint">
            아래 일정 위에 놓으세요
          </div>

          <div v-for="s in trips" :key="s.id"
               class="trip-card"
               :class="{ active: activeTrip === s.id, 'drop-over': s.dropOver }"
               @click="activeTrip = s.id"
               @dragover.prevent="s.dropOver = true"
               @dragleave="s.dropOver = false"
               @drop="onDropToTrip($event, s)">
            <div class="trip-card-top">
              <span class="trip-card-name">{{ s.title }}</span>
              <span v-if="activeTrip === s.id" class="trip-card-tick">✓</span>
            </div>
            <div class="trip-card-meta">
              <span>{{ s.startDate }} ~ {{ s.endDate }}</span>
              <span class="dot">·</span>
              <span>👥 {{ s.memberCount }}명</span>
              <span class="trip-card-count">{{ s.candidateCount }}개</span>
            </div>
          </div>

          <div v-if="!tripsLoading && !trips.length" class="tray-empty">
            <p>등록된 일정이 없습니다</p>
            <button @click="openScheduleModal()">+ 새 일정 만들기</button>
          </div>
          <div v-else-if="trips.length" class="tray-new">
            <button @click="openScheduleModal()" style="background:none;border:none;color:inherit;cursor:pointer;width:100%;">
              + 새 일정 만들기
            </button>
          </div>
          <div v-if="tripsLoading" style="padding:12px;color:var(--gray-muted);font-size:11px;text-align:center">로딩 중...</div>
        </div>
      </Transition>
    </div>

  </section>
  </main>
</template>

<script setup>
import { ref, computed, reactive, inject, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useToastStore } from '@/stores/toast'
import { searchAttractions, fetchRegions } from '@/api/attraction'
import { tripApi } from '@/api/trip'

const toast = useToastStore()
const openScheduleModal = inject('openScheduleModal')

// ── UI state (트레이 토글 / 드래그 감지) ──
const trayOpen = ref(false)
const isDraggingCard = ref(false)

const trips = ref([])
const tripsLoading = ref(false)
const activeTrip = ref(null)
const addedIds = ref(new Set())
const activeTripCandidates = ref([])
const candidateIdMap = ref(new Map())

const attractions = ref([])
const total = ref(0)
const page = ref(0)
const loading = ref(false)
const hasMore = computed(() => attractions.value.length < total.value)
let loadSeq = 0

const searchQuery = ref('')
const selectedRegions = ref([])
const selectedSigungus = ref([])
const selectedCats = ref([])
const statsData = ref([])

const regionsData = ref([])
const currentSigunguList = computed(() => {
  if (!selectedRegions.value.length) return []
  return regionsData.value
    .filter(r => selectedRegions.value.includes(r.sido))
    .flatMap(r => r.sigunguList ?? [])
})

function selectRegion(r) {
  selectedRegions.value = selectedRegions.value.includes(r) ? [] : [r]
  selectedSigungus.value = []
}
function toggleSigungu(sidoCode, code) {
  const val = `${sidoCode}:${code}`
  const idx = selectedSigungus.value.indexOf(val)
  if (idx === -1) selectedSigungus.value = [...selectedSigungus.value, val]
  else selectedSigungus.value = selectedSigungus.value.filter(c => c !== val)
}
function toggleCatFilter(c) {
  const idx = selectedCats.value.indexOf(c)
  if (idx === -1) selectedCats.value = [...selectedCats.value, c]
  else selectedCats.value = selectedCats.value.filter(x => x !== c)
}

const scrollEl = ref(null)
const filterOpen = ref(true)

const regions = ['서울', '경기', '강원', '충북', '충남', '경북', '경남', '전북', '전남', '제주']
const categories = ['관광지', '음식점', '숙박', '문화시설', '레포츠']
const PAGE_SIZE = 20

function toRenderGroups(sg) {
  return sg.catGroups || [{ cat: null, items: sg.items }]
}

const collapsedSearchRegions = reactive({})
const collapsedSearchSigungus = reactive({})
const collapsedSearchCats = reactive({})
function toggleSearchRegion(region) { collapsedSearchRegions[region] = !collapsedSearchRegions[region] }
function toggleSearchSigungu(key) { collapsedSearchSigungus[key] = !collapsedSearchSigungus[key] }
function toggleSearchCat(key) { collapsedSearchCats[key] = !collapsedSearchCats[key] }

const searchResultGroups = computed(() => {
  if (!statsData.value.length && !attractions.value.length) return []

  const hasRegion = selectedRegions.value.length > 0
  const hasSigungu = selectedSigungus.value.length > 0
  const hasCat = selectedCats.value.length > 0

  const cards = attractions.value
  const stats = statsData.value

  if (hasRegion && hasSigungu && hasCat) {
    return [{ region: null, total: cards.length,
              sgGroups: [{ sg: null, total: cards.length, items: cards }] }]
  }

  if (hasRegion && hasSigungu) {
    const catTotals = {}
    for (const s of stats) catTotals[s.category] = (catTotals[s.category] || 0) + s.count
    const grandTotal = Object.values(catTotals).reduce((a, b) => a + b, 0)
    const catGroups = Object.entries(catTotals).map(([cat, total]) => ({
      cat, total, items: cards.filter(a => (a.category || '기타') === cat)
    }))
    return [{ region: null, total: grandTotal,
              sgGroups: [{ sg: null, total: grandTotal, items: cards, catGroups }] }]
  }

  if (hasRegion) {
    const sgMap = {}
    for (const s of stats) {
      const sg = s.sigunguName || '기타'
      if (!sgMap[sg]) sgMap[sg] = { total: 0, catMap: {} }
      sgMap[sg].total += s.count
      sgMap[sg].catMap[s.category] = (sgMap[sg].catMap[s.category] || 0) + s.count
    }
    const grandTotal = Object.values(sgMap).reduce((s, v) => s + v.total, 0)
    return [{ region: null, total: grandTotal,
              sgGroups: Object.entries(sgMap).map(([sg, { total: sgTotal, catMap }]) => ({
                sg, total: sgTotal,
                items: cards.filter(a => (a.sigunguName || '기타') === sg),
                catGroups: Object.entries(catMap).map(([cat, catTotal]) => ({
                  cat, total: catTotal,
                  items: cards.filter(a => (a.sigunguName || '기타') === sg && (a.category || '기타') === cat)
                }))
              })) }]
  }

  const regionMap = {}
  for (const s of stats) {
    const region = s.region || '기타'
    const sg = s.sigunguName || '기타'
    if (!regionMap[region]) regionMap[region] = { total: 0, sgMap: {} }
    regionMap[region].total += s.count
    if (!regionMap[region].sgMap[sg]) regionMap[region].sgMap[sg] = { total: 0, catMap: {} }
    regionMap[region].sgMap[sg].total += s.count
    regionMap[region].sgMap[sg].catMap[s.category] = (regionMap[region].sgMap[sg].catMap[s.category] || 0) + s.count
  }
  return Object.entries(regionMap).map(([region, { total, sgMap }]) => ({
    region, total,
    sgGroups: Object.entries(sgMap).map(([sg, { total: sgTotal, catMap }]) => ({
      sg, total: sgTotal,
      items: cards.filter(a => (a.region || '기타') === region && (a.sigunguName || '기타') === sg),
      catGroups: Object.entries(catMap).map(([cat, catTotal]) => ({
        cat, total: catTotal,
        items: cards.filter(a => (a.region || '기타') === region && (a.sigunguName || '기타') === sg && (a.category || '기타') === cat)
      }))
    }))
  }))
})

const COLORS = { 12: '#C8C5F5', 14: '#9BD4C0', 28: '#AFE8C0', 32: '#AFC9E8', 38: '#F5D0A9', 39: '#F5C0D2' }
const EMOJIS = { 12: '🏯', 14: '🎨', 28: '🏄', 32: '🏨', 38: '🛍️', 39: '🍜' }
function colorFor(typeId) { return COLORS[typeId] || '#e0e0e0' }
function emojiFor(typeId) { return EMOJIS[typeId] || '📍' }

// ── Naver Maps ──
const mapEl = ref(null)
let naverMap = null
let markers = []
let markerIdSet = new Set()
let infoWindow = null

function loadNaverScript() {
  return new Promise((resolve, reject) => {
    if (window.naver?.maps) { resolve(); return }
    const clientId = import.meta.env.VITE_NAVER_MAP_CLIENT_ID
    const script = document.createElement('script')
    script.src = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${clientId}`
    script.onload = resolve
    script.onerror = reject
    document.head.appendChild(script)
  })
}

function initMap() {
  naverMap = new naver.maps.Map(mapEl.value, {
    center: new naver.maps.LatLng(36.5, 127.7),
    zoom: 8,
  })
  infoWindow = new naver.maps.InfoWindow({ zIndex: 1 })
  setTimeout(() => {
    naver.maps.Event.once(naverMap, 'resize', () => updateMarkers())
    naver.maps.Event.trigger(naverMap, 'resize')
  }, 100)
}

const MAX_ZOOM = 12

function fitMap() {
  if (!naverMap) return
  if (markers.length === 0) {
    naverMap.fitBounds(new naver.maps.LatLngBounds(
      new naver.maps.LatLng(33.0, 124.5),
      new naver.maps.LatLng(38.9, 130.0)
    ), { top: 20, right: 20, bottom: 20, left: 20 })
    return
  }
  const bounds = new naver.maps.LatLngBounds()
  markers.forEach(m => bounds.extend(m.getPosition()))
  naverMap.fitBounds(bounds, { top: 80, right: 360 + 32, bottom: 200, left: 60 })
  setTimeout(() => { if (naverMap.getZoom() > MAX_ZOOM) naverMap.setZoom(MAX_ZOOM) }, 150)
}

function clearMarkers() {
  markers.forEach(m => m.setMap(null))
  markers = []
  markerIdSet = new Set()
  infoWindow?.close()
}

function updateMarkers() {
  if (!naverMap) return
  clearMarkers()
  activeTripCandidates.value.forEach(c => {
    if (!c.latitude || !c.longitude) return
    const position = new naver.maps.LatLng(c.latitude, c.longitude)
    const marker = new naver.maps.Marker({ map: naverMap, position, title: c.attractionName })
    naver.maps.Event.addListener(marker, 'click', () => {
      infoWindow.setContent(`<div style="padding:6px 10px;font-size:12px;white-space:nowrap">${c.attractionName}</div>`)
      infoWindow.open(naverMap, marker)
    })
    markers.push(marker)
    markerIdSet.add(c.attractionId)
  })
  fitMap()
}

watch(activeTripCandidates, updateMarkers)

watch(activeTrip, async (id) => {
  if (!id) {
    activeTripCandidates.value = []
    addedIds.value = new Set()
    candidateIdMap.value = new Map()
    return
  }
  try {
    const detail = await tripApi.get(id)
    activeTripCandidates.value = detail.candidates || []
    addedIds.value = new Set(detail.candidates.map(c => c.attractionId))
    candidateIdMap.value = new Map(detail.candidates.map(c => [c.attractionId, c.id]))
  } catch {
    activeTripCandidates.value = []
  }
})

function _applyRemove(attractionId, candidateId, tripId) {
  activeTripCandidates.value = activeTripCandidates.value.filter(c => c.id !== candidateId)
  addedIds.value = new Set([...addedIds.value].filter(id => id !== attractionId))
  candidateIdMap.value.delete(attractionId)
  const t = trips.value.find(t => t.id === tripId)
  if (t && t.candidateCount > 0) t.candidateCount--
}

async function removeFromTrip(attractionId) {
  const tripId = activeTrip.value
  if (!tripId) return
  const candidateId = candidateIdMap.value.get(attractionId)
  if (candidateId == null) return
  try {
    await tripApi.removeCandidate(tripId, candidateId)
    _applyRemove(attractionId, candidateId, tripId)
    toast.show('후보에서 제거됐어요')
  } catch (e) {
    if (e.status === 409) {
      const candidate = activeTripCandidates.value.find(c => c.id === candidateId)
      const blocks = candidate?.blocks || []
      if (blocks.length > 0 && window.confirm(
        `타임라인에 ${blocks.length}개 일정 블록이 배치돼 있어요.\n블록도 함께 삭제할까요?`
      )) {
        try {
          for (const block of blocks) {
            await tripApi.removeBlock(tripId, block.id)
          }
          await tripApi.removeCandidate(tripId, candidateId)
          _applyRemove(attractionId, candidateId, tripId)
          toast.show('일정 블록과 함께 후보에서 제거됐어요')
        } catch {
          toast.show('삭제 중 오류가 발생했어요')
        }
      }
    } else if (e.status === 401) {
      toast.show('로그인이 필요합니다.')
    } else {
      toast.show(`제거 실패 (${e.status ?? '?'}: ${e.message})`)
    }
  }
}

async function removeByAttraction(attractionId) {
  await removeFromTrip(attractionId)
}

// ── 드래그 ──
let draggedAttraction = null
const draggingCand = ref(null)

function onCandListDragStart(e, c) {
  draggingCand.value = c
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('type', 'candidate')
}

function onDropToAttrList(e) {
  const type = e.dataTransfer.getData('type')
  if (type === 'candidate' && draggingCand.value) {
    removeFromTrip(draggingCand.value.attractionId)
    draggingCand.value = null
  }
}

function onCardDragStart(e, attraction) {
  draggedAttraction = attraction
  isDraggingCard.value = true
  trayOpen.value = true
  e.dataTransfer.effectAllowed = 'copy'
  e.dataTransfer.setData('type', 'attraction')
}

function onCardDragEnd() {
  isDraggingCard.value = false
  draggedAttraction = null
}

async function onDropToTrip(e, trip) {
  trip.dropOver = false
  const type = e.dataTransfer.getData('type')
  if (type === 'candidate' || !draggedAttraction) return
  const prev = activeTrip.value
  activeTrip.value = trip.id
  await addToTrip(draggedAttraction)
  if (prev !== trip.id) activeTrip.value = prev
  draggedAttraction = null
  isDraggingCard.value = false
}

async function loadTrips() {
  tripsLoading.value = true
  try {
    trips.value = (await tripApi.list()).map(t => ({ ...t, dropOver: false }))
    if (trips.value.length) activeTrip.value = trips.value[0].id
  } catch {
    // 비로그인
  } finally {
    tripsLoading.value = false
  }
}

async function loadRegions() {
  try {
    regionsData.value = await fetchRegions()
  } catch {
    // 실패 시 빈 목록 유지
  }
}

async function loadAttractions(append = false) {
  if (append && loading.value) return

  if (!append) loadSeq++
  const mySeq = loadSeq
  loading.value = true

  try {
    const data = await searchAttractions({
      keyword: searchQuery.value || undefined,
      region: selectedRegions.value.length ? selectedRegions.value.join(',') : undefined,
      sigungu: selectedSigungus.value.length ? selectedSigungus.value : undefined,
      category: selectedCats.value.length ? selectedCats.value.join(',') : undefined,
      page: page.value,
      size: PAGE_SIZE,
    })
    if (mySeq !== loadSeq) return
    if (append) {
      attractions.value = [...attractions.value, ...data.items]
    } else {
      attractions.value = data.items
      statsData.value = data.groupStats || []
      backgroundLoadAll(mySeq)
    }
    total.value = data.total
  } catch {
    if (mySeq === loadSeq) toast.show('관광지 로드 실패')
  } finally {
    if (mySeq === loadSeq) loading.value = false
  }
}

function checkScroll() {
  const el = scrollEl.value
  if (!el || loading.value || !hasMore.value) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 300) loadMore()
}

async function loadMore() {
  if (loading.value || !hasMore.value) return
  page.value++
  await loadAttractions(true)
  await nextTick()
  checkScroll()
}

async function backgroundLoadAll(seq) {
  await new Promise(r => setTimeout(r, 200))
  if (total.value > 20) return
  while (seq === loadSeq && hasMore.value) {
    if (!loading.value) await loadMore()
    await new Promise(r => setTimeout(r, 80))
  }
}

function applyFilters() {
  page.value = 0
  loadAttractions(false)
}

function clearFilters() {
  selectedRegions.value = []
  selectedSigungus.value = []
  selectedCats.value = []
  searchQuery.value = ''
  page.value = 0
  loadAttractions(false)
  toast.show('필터가 초기화됐어요')
}

async function addToTrip(attraction) {
  if (addedIds.value.has(attraction.id)) return
  if (!activeTrip.value) {
    toast.show('먼저 일정을 선택해주세요.')
    trayOpen.value = true
    return
  }
  try {
    const candidateId = await tripApi.addCandidate(activeTrip.value, attraction.id)
    addedIds.value = new Set([...addedIds.value, attraction.id])
    candidateIdMap.value.set(attraction.id, candidateId)
    activeTripCandidates.value = [
      ...activeTripCandidates.value,
      { id: candidateId, attractionId: attraction.id, attractionName: attraction.title,
        category: attraction.category, cityName: attraction.region,
        sigunguCode: attraction.sigunguCode, sigunguName: attraction.sigunguName,
        latitude: attraction.latitude, longitude: attraction.longitude }
    ]
    toast.show(`"${attraction.title}" 후보군에 추가됐어요`)
    const t = trips.value.find(t => t.id === activeTrip.value)
    if (t) t.candidateCount++
  } catch (e) {
    toast.show(e.status === 401 ? '로그인이 필요합니다.' : (e.message || '추가 실패'))
  }
}

onMounted(async () => {
  loadTrips()
  loadAttractions(false)
  loadRegions()

  scrollEl.value?.addEventListener('scroll', checkScroll)
  await nextTick()
  checkScroll()

  try {
    await loadNaverScript()
    initMap()
  } catch (e) {
    console.error('Naver Maps 로드 실패:', e)
    toast.show('지도를 불러오지 못했어요')
  }
})

onUnmounted(() => scrollEl.value?.removeEventListener('scroll', checkScroll))
</script>
