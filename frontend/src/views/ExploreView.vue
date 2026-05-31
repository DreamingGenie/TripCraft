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
          <span class="filter-header-summary">
            <template v-if="!filterOpen && hasActiveFilters">{{ [...selectedRegions, ...selectedSigunguNames, ...selectedCats].join(' · ') }}</template>
          </span>
          <button class="filter-clear-inline"
                  :style="{ visibility: hasActiveFilters ? 'visible' : 'hidden' }"
                  @click.stop="clearFilters">초기화</button>
          <span class="filter-chevron" :class="{ open: filterOpen }">▾</span>
        </div>

        <div class="filter-body" :class="{ collapsed: !filterOpen }">
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
                  <span class="group-count">{{ sg.total }}</span>
                </button>
                <Transition name="tree-slide">
                  <div v-if="!sg.sg || !collapsedSearchSigungus[`${rg.region}__${sg.sg}`]"
                       class="group-cards-wrap">
                    <div v-for="cg in toRenderGroups(sg)" :key="cg.cat ?? '__all__'" class="cat-section-wrap">
                      <button v-if="cg.cat" class="cat-section-header"
                              @click="toggleSearchCat(`${rg.region ?? ''}__${sg.sg ?? ''}__${cg.cat}`)">
                        <span class="group-chevron" :class="{ open: !collapsedSearchCats[`${rg.region ?? ''}__${sg.sg ?? ''}__${cg.cat}`] }">▶</span>
                        {{ cg.cat }}
                        <span class="group-count">{{ cg.total }}</span>
                      </button>
                      <Transition name="tree-slide">
                        <div v-if="!cg.cat || !collapsedSearchCats[`${rg.region ?? ''}__${sg.sg ?? ''}__${cg.cat}`]"
                             class="cards-grid"
                             v-observe="() => loadGroup(rg.region, sg.sg, cg.cat)">
                          <!-- 로드 전: 플레이스홀더로 높이 확보 -->
                          <div v-if="!getGroup(rg.region, sg.sg, cg.cat).loading && !getGroup(rg.region, sg.sg, cg.cat).loaded"
                               class="group-placeholder"></div>
                          <!-- 로딩 중: 스켈레톤 -->
                          <template v-else-if="getGroup(rg.region, sg.sg, cg.cat).loading">
                            <div class="skeleton-card"></div>
                            <div class="skeleton-card"></div>
                          </template>
                          <!-- 로드 완료 -->
                          <template v-else>
                            <div v-for="a in getGroup(rg.region, sg.sg, cg.cat).items" :key="a.id"
                                 class="attr-card" :class="{ candidate: addedIds.has(a.id), selected: selectedAttraction?.id === a.id }"
                                 draggable="true"
                                 @click="selectAttraction(a)"
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
                          </template>
                        </div>
                      </Transition>
                    </div>
                  </div>
                </Transition>
              </div>
            </div>
          </Transition>
        </div>

        <div v-if="loading" style="padding:20px;text-align:center;color:var(--gray-muted);font-size:12px">목록 로딩 중...</div>
      </div>

      <!-- 상세 패널 (절대 위치 오버레이) -->
      <Transition name="detail-slide">
        <div v-if="selectedAttraction" class="detail-panel">
          <div class="detail-nav">
            <button class="detail-back" @click="closeDetail()">← 목록</button>
            <button class="card-add detail-add-btn"
                    :class="{ added: addedIds.has(selectedAttraction.id) }"
                    @click.stop="addedIds.has(selectedAttraction.id) ? removeByAttraction(selectedAttraction.id) : addToTrip(selectedAttraction)">
              {{ addedIds.has(selectedAttraction.id) ? '× 제거' : '+ 추가' }}
            </button>
          </div>
          <div class="detail-scroll">
            <img v-if="selectedAttraction.firstImage" :src="selectedAttraction.firstImage" class="detail-img" />
            <div v-else class="detail-img-empty" :style="{ background: colorFor(selectedAttraction.contentTypeId) }">
              <span>{{ emojiFor(selectedAttraction.contentTypeId) }}</span>
            </div>
            <div class="detail-body">
              <div class="detail-cat-row">
                <span class="detail-cat-badge" :style="{ background: colorFor(selectedAttraction.contentTypeId) }">{{ selectedAttraction.category }}</span>
                <span class="detail-region">{{ selectedAttraction.sigunguName || selectedAttraction.region }}</span>
              </div>
              <h2 class="detail-title">{{ selectedAttraction.title }}</h2>
              <div v-if="selectedAttraction.address" class="detail-info-row">
                <span>📍</span>
                <span>{{ selectedAttraction.address }}{{ selectedAttractionDetail?.addr2 ? ' ' + selectedAttractionDetail.addr2 : '' }}</span>
              </div>
              <div v-if="selectedAttractionDetail?.tel && selectedAttractionDetail.tel.trim()" class="detail-info-row">
                <span>📞</span><span>{{ selectedAttractionDetail.tel }}</span>
              </div>
              <p v-if="detailLoading" class="detail-loading">상세 정보 로딩 중...</p>
              <p v-if="selectedAttractionDetail?.overview && selectedAttractionDetail.overview.trim()" class="detail-overview">
                {{ selectedAttractionDetail.overview }}
              </p>
            </div>
          </div>
        </div>
      </Transition>
    </div>

    <!-- 우측 하단 트레이: 내 일정 -->
    <div class="trip-tray" :class="{ dragging: isDraggingCard }"
         @dragover.prevent
         @drop="onDropToActiveTray">
      <button class="tray-header" @click="trayOpen = !trayOpen">
        <span class="tray-icon">📋</span>
        <div class="tray-header-info">
          <span class="tray-title">내 일정</span>
          <span v-if="currentTrip" class="tray-trip-name">{{ currentTrip.title }}</span>
        </div>
        <span class="tray-count">{{ activeTripCandidates.length }}</span>
        <span class="tray-toggle-chev" :class="{ open: trayOpen }">▼</span>
      </button>

      <Transition name="tray-slide">
        <div v-show="trayOpen" class="tray-body">

          <!-- 일정 선택 모드 -->
          <template v-if="trayMode === 'select' || !currentTrip">
            <div v-for="s in trips" :key="s.id"
                 class="trip-select-item"
                 :class="{ active: activeTrip === s.id }"
                 @click="activeTrip = s.id; trayMode = 'candidates'">
              <div class="trip-select-name">{{ s.title }}</div>
              <div class="trip-select-meta">{{ s.startDate }} ~ {{ s.endDate }} · 👥 {{ s.memberCount }}명 · {{ s.candidateCount }}개</div>
            </div>
            <div v-if="!tripsLoading && !trips.length" class="tray-empty">
              <p>등록된 일정이 없습니다</p>
              <button @click="openScheduleModal()">+ 새 일정 만들기</button>
            </div>
            <div v-else-if="trips.length" class="tray-new" @click="openScheduleModal()">+ 새 일정 만들기</div>
            <div v-if="tripsLoading" style="padding:12px;color:var(--gray-muted);font-size:11px;text-align:center">로딩 중...</div>
          </template>

          <!-- 후보 목록 모드 -->
          <template v-else>
            <div class="tray-trip-bar">
              <span class="tray-trip-dates">{{ currentTrip.startDate }} ~ {{ currentTrip.endDate }}</span>
              <span class="tray-trip-members">👥 {{ currentTrip.memberCount }}명</span>
              <button class="tray-switch-btn" @click="trayMode = 'select'">일정 변경</button>
            </div>

            <div v-if="isDraggingCard" class="tray-drag-hint">여기에 놓으면 추가돼요</div>

            <div v-if="!activeTripCandidates.length" class="tray-cand-empty">
              추가된 장소가 없어요<br>
              <span>왼쪽 카드를 드래그하거나 + 버튼으로 추가하세요</span>
            </div>
            <template v-else>
              <div v-for="rg in groupedCandidates" :key="rg.region">
                <button class="tray-group-header"
                        @click="collapsedCandRegions[rg.region] = !collapsedCandRegions[rg.region]">
                  <span class="group-chevron" :class="{ open: !collapsedCandRegions[rg.region] }">▶</span>
                  {{ rg.region }}
                  <span class="tray-group-count">{{ rg.total }}</span>
                </button>
                <div v-if="!collapsedCandRegions[rg.region]" class="tray-cat-wrap">
                  <div v-for="cg in rg.catGroups" :key="cg.cat">
                    <button class="tray-cat-header"
                            @click="collapsedCandCats[`${rg.region}__${cg.cat}`] = !collapsedCandCats[`${rg.region}__${cg.cat}`]">
                      <span class="group-chevron" :class="{ open: !collapsedCandCats[`${rg.region}__${cg.cat}`] }">▶</span>
                      {{ cg.cat }}
                      <span class="tray-group-count">{{ cg.items.length }}</span>
                    </button>
                    <div v-if="!collapsedCandCats[`${rg.region}__${cg.cat}`]" class="tray-cand-list">
                      <div v-for="c in cg.items" :key="c.id" class="cand-item">
                        <div class="cand-item-body">
                          <div class="cand-item-name">{{ c.attractionName }}</div>
                          <div v-if="c.sigunguName" class="cand-item-meta">{{ c.sigunguName }}</div>
                        </div>
                        <button class="cand-remove" @click="removeByAttraction(c.attractionId)">×</button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </template>

            <div class="tray-new" @click="openScheduleModal()">+ 새 일정 만들기</div>
          </template>

        </div>
      </Transition>
    </div>

  </section>
  </main>
</template>

<script setup>
import { ref, computed, reactive, inject, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useToastStore } from '@/stores/toast'
import { searchAttractions, fetchRegions, fetchAttractionDetail } from '@/api/attraction'
import { tripApi } from '@/api/trip'

const toast = useToastStore()
const openScheduleModal = inject('openScheduleModal')

// ── UI state (트레이 토글 / 드래그 감지) ──
const trayOpen = ref(false)
const trayMode = ref('candidates') // 'candidates' | 'select'
const isDraggingCard = ref(false)
const currentTrip = computed(() => trips.value.find(t => t.id === activeTrip.value))

const trips = ref([])
const tripsLoading = ref(false)
const activeTrip = ref(null)
const addedIds = ref(new Set())
const activeTripCandidates = ref([])
const candidateIdMap = ref(new Map())

const total = ref(0)
const loading = ref(false)
let loadSeq = 0

const selectedAttraction = ref(null)
const selectedAttractionDetail = ref(null)
const detailLoading = ref(false)

// 그룹별 아이템 캐시: "region__sg__cat" → { items, loading, loaded }
const groupItems = reactive({})

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

const hasActiveFilters = computed(() =>
  selectedRegions.value.length > 0 || selectedSigungus.value.length > 0 || selectedCats.value.length > 0
)

const selectedSigunguNames = computed(() =>
  selectedSigungus.value.map(key => {
    const [sidoCode, code] = key.split(':')
    for (const r of regionsData.value) {
      const sg = (r.sigunguList ?? []).find(s => String(s.sidoCode) === sidoCode && String(s.code) === code)
      if (sg) return sg.name
    }
    return key
  })
)

function selectRegion(r) {
  selectedRegions.value = selectedRegions.value.includes(r) ? [] : [r]
  selectedSigungus.value = []
  loadAttractions()
}
function toggleSigungu(sidoCode, code) {
  const val = `${sidoCode}:${code}`
  const idx = selectedSigungus.value.indexOf(val)
  if (idx === -1) selectedSigungus.value = [...selectedSigungus.value, val]
  else selectedSigungus.value = selectedSigungus.value.filter(c => c !== val)
  loadAttractions()
}
function toggleCatFilter(c) {
  const idx = selectedCats.value.indexOf(c)
  if (idx === -1) selectedCats.value = [...selectedCats.value, c]
  else selectedCats.value = selectedCats.value.filter(x => x !== c)
  loadAttractions()
}

const scrollEl = ref(null)
const filterOpen = ref(true)
let lastScrollTop = 0
let accScrollDown = 0
let groupObserver = null
const groupObserverMap = new WeakMap()
const observedElements = new Set()

const vObserve = {
  mounted(el, binding) {
    groupObserverMap.set(el, binding.value)
    observedElements.add(el)
    groupObserver?.observe(el)
  },
  beforeUpdate(el, binding) {
    groupObserverMap.set(el, binding.value)
  },
  unmounted(el) {
    groupObserver?.unobserve(el)
    groupObserverMap.delete(el)
    observedElements.delete(el)
  }
}

function reobserveAll() {
  if (!groupObserver) return
  for (const el of observedElements) {
    groupObserver.unobserve(el)
    groupObserver.observe(el)
  }
}

function setupGroupObserver() {
  groupObserver?.disconnect()
  if (!scrollEl.value) return
  groupObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          groupObserverMap.get(entry.target)?.()
        }
      }
    },
    { root: scrollEl.value, threshold: 0 }
  )
}

function getSigunguKey(regionName, sgName) {
  if (!sgName || sgName === '기타') return null
  const region = regionsData.value.find(r => r.sido === regionName)
  const sg = region?.sigunguList?.find(s => s.name === sgName)
  return sg ? `${sg.sidoCode}:${sg.code}` : null
}

function getGroup(region, sg, cat) {
  const key = `${region ?? ''}__${sg ?? ''}__${cat ?? ''}`
  return groupItems[key] ?? { items: [], loading: false, loaded: false }
}

async function loadGroup(region, sg, cat) {
  const key = `${region ?? ''}__${sg ?? ''}__${cat ?? ''}`
  if (groupItems[key]?.loading || groupItems[key]?.loaded) return
  const seq = loadSeq
  groupItems[key] = { items: [], loading: true, loaded: false }
  try {
    const sigunguKey = getSigunguKey(region, sg)
    const data = await searchAttractions({
      keyword: searchQuery.value || undefined,
      region: region && region !== '기타' ? region : undefined,
      sigungu: sigunguKey ? [sigunguKey] : undefined,
      category: cat && cat !== '기타' ? cat : undefined,
      page: 0,
      size: 200,
    })
    if (seq !== loadSeq) { delete groupItems[key]; return }
    groupItems[key].items = data.items
    groupItems[key].loaded = true
  } catch {
    if (seq === loadSeq) delete groupItems[key]
  } finally {
    if (groupItems[key] && seq === loadSeq) groupItems[key].loading = false
  }
}

const regions = ['서울', '경기', '강원', '충북', '충남', '경북', '경남', '전북', '전남', '제주']
const categories = ['관광지', '음식점', '숙박', '문화시설', '레포츠']
const PAGE_SIZE = 20

function toRenderGroups(sg) {
  return sg.catGroups ?? []
}

const collapsedSearchRegions = reactive({})
const collapsedSearchSigungus = reactive({})
const collapsedSearchCats = reactive({})

const collapsedCandRegions = reactive({})
const collapsedCandCats = reactive({})

const groupedCandidates = computed(() => {
  const regionMap = {}
  for (const c of activeTripCandidates.value) {
    const region = c.cityName || '기타'
    const cat = c.category || '기타'
    if (!regionMap[region]) regionMap[region] = {}
    if (!regionMap[region][cat]) regionMap[region][cat] = []
    regionMap[region][cat].push(c)
  }
  return Object.entries(regionMap).map(([region, catMap]) => ({
    region,
    total: Object.values(catMap).reduce((s, v) => s + v.length, 0),
    catGroups: Object.entries(catMap).map(([cat, items]) => ({ cat, items }))
  }))
})
function toggleSearchRegion(region) { collapsedSearchRegions[region] = !collapsedSearchRegions[region] }
function toggleSearchSigungu(key) { collapsedSearchSigungus[key] = !collapsedSearchSigungus[key] }
function toggleSearchCat(key) { collapsedSearchCats[key] = !collapsedSearchCats[key] }

const searchResultGroups = computed(() => {
  if (!statsData.value.length) return []

  const hasRegion = selectedRegions.value.length > 0
  const stats = statsData.value
  const selectedRegion = selectedRegions.value[0] ?? null

  if (hasRegion) {
    const sgMap = {}
    for (const s of stats) {
      const sg = s.sigunguName || '기타'
      if (!sgMap[sg]) sgMap[sg] = { total: 0, catMap: {} }
      sgMap[sg].total += s.count
      sgMap[sg].catMap[s.category] = (sgMap[sg].catMap[s.category] || 0) + s.count
    }
    const grandTotal = Object.values(sgMap).reduce((s, v) => s + v.total, 0)
    return [{ region: selectedRegion, total: grandTotal,
              sgGroups: Object.entries(sgMap).map(([sg, { total: sgTotal, catMap }]) => ({
                sg, total: sgTotal,
                catGroups: Object.entries(catMap).map(([cat, catTotal]) => ({ cat, total: catTotal }))
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
  return Object.entries(regionMap).map(([region, { total: rTotal, sgMap }]) => ({
    region, total: rTotal,
    sgGroups: Object.entries(sgMap).map(([sg, { total: sgTotal, catMap }]) => ({
      sg, total: sgTotal,
      catGroups: Object.entries(catMap).map(([cat, catTotal]) => ({ cat, total: catTotal }))
    }))
  }))
})

const COLORS = { 12: '#C8C5F5', 14: '#9BD4C0', 28: '#AFE8C0', 32: '#AFC9E8', 38: '#F5D0A9', 39: '#F5C0D2' }
const EMOJIS = { 12: '🏯', 14: '🎨', 28: '🏄', 32: '🏨', 38: '🛍️', 39: '🍜' }
function colorFor(typeId) { return COLORS[typeId] || '#e0e0e0' }
function emojiFor(typeId) { return EMOJIS[typeId] || '📍' }

const CAT_COLORS = {
  '관광지': '#8B85E0', '문화시설': '#48B89A', '레포츠': '#55B36E',
  '숙박': '#6B9FD4', '쇼핑': '#D4844A', '음식점': '#D46070'
}
function catColor(cat) { return CAT_COLORS[cat] || '#9E9E9E' }

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

let selectedMarker = null

function clearSelectedMarker() {
  selectedMarker?.setMap(null)
  selectedMarker = null
}

function updateSelectedMarker(a) {
  clearSelectedMarker()
  if (!naverMap || !a?.latitude || !a?.longitude) return
  const color = catColor(a.category)
  selectedMarker = new naver.maps.Marker({
    map: naverMap,
    position: new naver.maps.LatLng(Number(a.latitude), Number(a.longitude)),
    icon: {
      content: `<div style="width:32px;height:32px;background:${color};border:3px solid white;border-radius:50%;box-shadow:0 3px 10px rgba(0,0,0,.4);box-sizing:border-box;outline:3px solid ${color}"></div>`,
      anchor: new naver.maps.Point(16, 16)
    },
    zIndex: 100
  })
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
    const color = catColor(c.category)
    const marker = new naver.maps.Marker({
      map: naverMap, position, title: c.attractionName,
      icon: {
        content: `<div style="width:22px;height:22px;background:${color};border:2px solid white;border-radius:50%;box-shadow:0 2px 5px rgba(0,0,0,.3);cursor:pointer;box-sizing:border-box"></div>`,
        anchor: new naver.maps.Point(11, 11)
      }
    })
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

async function onDropToActiveTray(e) {
  const type = e.dataTransfer.getData('type')
  if (type === 'candidate' || !draggedAttraction) return
  await addToTrip(draggedAttraction)
  draggedAttraction = null
  isDraggingCard.value = false
}

async function loadTrips() {
  tripsLoading.value = true
  try {
    trips.value = await tripApi.list()
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

async function loadAttractions() {
  loadSeq++
  const mySeq = loadSeq
  loading.value = true
  for (const key in groupItems) delete groupItems[key]

  try {
    const data = await searchAttractions({
      keyword: searchQuery.value || undefined,
      region: selectedRegions.value.length ? selectedRegions.value.join(',') : undefined,
      sigungu: selectedSigungus.value.length ? selectedSigungus.value : undefined,
      category: selectedCats.value.length ? selectedCats.value.join(',') : undefined,
      page: 0,
      size: 1,
    })
    if (mySeq !== loadSeq) return
    statsData.value = data.groupStats || []
    total.value = data.total
    await nextTick()
    reobserveAll()
  } catch {
    if (mySeq === loadSeq) toast.show('관광지 로드 실패')
  } finally {
    if (mySeq === loadSeq) loading.value = false
  }
}

function checkScroll() {
  const el = scrollEl.value
  if (!el) return
  const st = el.scrollTop
  const delta = st - lastScrollTop

  if (delta > 0 && filterOpen.value) {
    accScrollDown += delta
    if (accScrollDown > 60) {
      filterOpen.value = false
      accScrollDown = 0
    }
  } else if (delta < 0) {
    accScrollDown = 0
  }

  lastScrollTop = st
}

function applyFilters() {
  loadAttractions()
}

function clearFilters() {
  selectedRegions.value = []
  selectedSigungus.value = []
  selectedCats.value = []
  searchQuery.value = ''
  loadAttractions()
  toast.show('필터가 초기화됐어요')
}

async function selectAttraction(a) {
  if (selectedAttraction.value?.id === a.id) { closeDetail(); return }
  selectedAttraction.value = a
  selectedAttractionDetail.value = null

  if (naverMap && a.latitude && a.longitude) {
    naverMap.panTo(new naver.maps.LatLng(Number(a.latitude), Number(a.longitude)))
    if (naverMap.getZoom() < 13) naverMap.setZoom(13)
  }
  updateSelectedMarker(a)

  detailLoading.value = true
  try {
    selectedAttractionDetail.value = await fetchAttractionDetail(a.id)
  } catch { /* 기본 정보로 대체 */ } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  selectedAttraction.value = null
  selectedAttractionDetail.value = null
  clearSelectedMarker()
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
  setupGroupObserver()
  scrollEl.value?.addEventListener('scroll', checkScroll)

  loadTrips()
  loadRegions()
  loadAttractions()

  try {
    await loadNaverScript()
    initMap()
  } catch (e) {
    console.error('Naver Maps 로드 실패:', e)
    toast.show('지도를 불러오지 못했어요')
  }
})

onUnmounted(() => {
  scrollEl.value?.removeEventListener('scroll', checkScroll)
  groupObserver?.disconnect()
})
</script>
