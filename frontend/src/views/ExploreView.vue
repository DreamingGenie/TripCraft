<template>
  <main id="main">
  <section id="screen-explore">

    <!-- 왼쪽: 내 일정 사이드바 -->
    <aside class="schedule-list-sidebar">
      <div class="schedule-list-header">
        <span class="schedule-list-title">내 일정</span>
        <RouterLink to="/schedule" class="btn-goto-schedule">관리 →</RouterLink>
      </div>
      <div class="schedule-cards">
        <div v-for="s in trips" :key="s.id"
             class="schedule-card" :class="{ active: activeTrip === s.id, 'drop-over': s.dropOver }"
             @click="activeTrip = s.id"
             @dragover.prevent="s.dropOver = true"
             @dragleave="s.dropOver = false"
             @drop="onDropToTrip($event, s)">
          <div class="schedule-card-top">
            <span class="schedule-card-name">{{ s.title }}</span>
            <button class="schedule-select-btn">{{ activeTrip === s.id ? '✓ 선택됨' : '선택' }}</button>
          </div>
          <div class="schedule-card-meta">
            <span>👥 {{ s.memberCount }}명</span>
            <span class="schedule-dates">{{ s.startDate }} ~ {{ s.endDate }}</span>
          </div>

          <!-- 선택된 일정: 지역 > 카테고리 > 장소 트리 -->
          <template v-if="activeTrip === s.id && candidateRegionGroups.length">
            <div class="cand-tree">
              <div v-for="rg in candidateRegionGroups" :key="rg.region" class="cand-city-group">
                <button class="cand-city-header" @click.stop="toggleRegion(rg.region)">
                  <span class="cand-chevron" :class="{ open: !collapsedRegions[rg.region] }">▶</span>
                  <span class="cand-city-name">{{ rg.region }}</span>
                  <span class="cand-city-count">{{ rg.total }}</span>
                </button>
                <Transition name="tree-slide">
                  <div v-if="!collapsedRegions[rg.region]" class="cand-city-body">
                    <div v-for="cg in rg.catGroups" :key="cg.cat" class="cand-cat-group">
                      <button class="cand-cat-header" @click.stop="toggleCatGroup(rg.region, cg.cat)">
                        <span class="cand-chevron sm" :class="{ open: !collapsedCatGroups[`${rg.region}__${cg.cat}`] }">▶</span>
                        <span class="cand-cat-name">{{ cg.cat }}</span>
                        <span class="cand-cat-count">({{ cg.items.length }})</span>
                      </button>
                      <Transition name="tree-slide">
                        <div v-if="!collapsedCatGroups[`${rg.region}__${cg.cat}`]" class="cand-cat-body">
                          <div v-for="c in cg.items" :key="c.id"
                               class="cand-item-row"
                               draggable="true"
                               @dragstart="onCandListDragStart($event, c)"
                               @dragend="draggingCand = null">
                            <span class="cand-drag-dot">⠿</span>
                            <span class="cand-item-name">{{ c.attractionName }}</span>
                            <button class="cand-item-del" @click.stop="removeFromTrip(c.attractionId)">×</button>
                          </div>
                        </div>
                      </Transition>
                    </div>
                  </div>
                </Transition>
              </div>
            </div>
          </template>
          <div v-else class="schedule-regions">
            <span style="font-size:11px;color:var(--gray-muted)">{{ s.candidateCount }}개 장소</span>
          </div>
        </div>

        <div v-if="!tripsLoading && !trips.length" class="schedule-empty">
          <p>일정이 없습니다</p>
          <button @click="openScheduleModal()">+ 새 일정 만들기</button>
        </div>
        <div v-if="tripsLoading" style="padding:12px;color:var(--gray-muted);font-size:12px">로딩 중...</div>

      </div>
    </aside>

    <!-- 중앙: 장소 목록 -->
    <div class="attr-list"
         @dragover.prevent
         @drop="onDropToAttrList">
      <div class="search-bar">
        <span class="search-icon">🔍</span>
        <input type="text" v-model="searchQuery" placeholder="장소, 도시 검색"
               @keydown.enter="applyFilters" />
      </div>

      <div class="filter-bar">
        <button class="filter-toggle-btn" :class="{ open: filterOpen, 'has-selection': selectedRegion }"
                @click="filterOpen = !filterOpen">
          지역
          <span v-if="selectedRegion" class="filter-count-badge">1</span>
          <span class="filter-arrow">▾</span>
        </button>
        <button class="filter-toggle-btn" :class="{ open: filterOpen, 'has-selection': selectedCat }"
                @click="filterOpen = !filterOpen">
          카테고리
          <span v-if="selectedCat" class="filter-count-badge">1</span>
          <span class="filter-arrow">▾</span>
        </button>
        <button v-if="selectedRegion || selectedCat"
                class="filter-clear-btn" @click="clearFilters">초기화</button>
      </div>

      <div v-if="filterOpen" class="filter-panel open">
        <div class="filter-panel-section">
          <span class="filter-panel-label">지역</span>
          <div class="chip-group">
            <button v-for="r in regions" :key="r"
                    class="chip" :class="{ sel: selectedRegion === r }"
                    @click="selectedRegion = selectedRegion === r ? '' : r">{{ r }}</button>
          </div>
        </div>
        <div class="filter-panel-section">
          <span class="filter-panel-label">카테고리</span>
          <div class="chip-group">
            <button v-for="c in categories" :key="c"
                    class="chip" :class="{ sel: selectedCat === c }"
                    @click="selectedCat = selectedCat === c ? '' : c">{{ c }}</button>
          </div>
        </div>
        <div class="filter-panel-actions">
          <button class="btn-apply-filter" @click="applyFilters(); filterOpen = false">필터 적용</button>
        </div>
      </div>

      <p class="result-count">{{ total }}개의 장소</p>

      <!-- 스크롤 영역 -->
      <div ref="scrollEl" class="cards-scroll">
        <div v-for="group in groupedAttractions" :key="group.label">
          <div v-if="group.label" class="group-section-header">{{ group.label }}</div>
          <div class="cards-grid">
            <div v-for="a in group.items" :key="a.id"
                 class="attr-card" :class="{ candidate: addedIds.has(a.id) }"
                 draggable="true"
                 @dragstart="onCardDragStart($event, a)">
              <div v-if="addedIds.has(a.id)" class="candidate-badge">✓</div>
              <div class="card-img" :style="{ background: colorFor(a.contentTypeId) }">
                <img v-if="a.firstImage" :src="a.firstImage" style="width:100%;height:100%;object-fit:cover" />
                <span v-else>{{ emojiFor(a.contentTypeId) }}</span>
              </div>
              <div class="card-info">
                <div class="card-name">{{ a.title }}</div>
                <p class="card-cat">{{ a.category }} · {{ a.region }}</p>
                <button class="card-add" :class="{ added: addedIds.has(a.id) }"
                        @click="addedIds.has(a.id) ? removeByAttraction(a.id) : addToTrip(a)">
                  {{ addedIds.has(a.id) ? '× 제거' : '+ 일정에 추가' }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 무한 스크롤 센티넬 -->
        <div ref="sentinel" style="height:1px"></div>
        <div v-if="loading" style="padding:16px;text-align:center;color:var(--gray-muted);font-size:12px">로딩 중...</div>
        <div v-else-if="!hasMore && attractions.length" style="padding:12px;text-align:center;color:var(--gray-muted);font-size:11px">모든 장소를 불러왔어요</div>
      </div>
    </div>

    <!-- 오른쪽: 지도 -->
    <div class="map-area">
      <div ref="mapEl" class="naver-map"></div>
    </div>

  </section>
  </main>
</template>

<script setup>
import { ref, computed, reactive, inject, watch, onMounted, onUnmounted } from 'vue'
import { useToastStore } from '@/stores/toast'
import { searchAttractions } from '@/api/attraction'
import { tripApi } from '@/api/trip'

const toast = useToastStore()
const openScheduleModal = inject('openScheduleModal')

const trips = ref([])
const tripsLoading = ref(false)
const activeTrip = ref(null)
const collapsedRegions = reactive({})
const collapsedCatGroups = reactive({})
function toggleRegion(region) { collapsedRegions[region] = !collapsedRegions[region] }
function toggleCatGroup(region, cat) {
  const key = `${region}__${cat}`
  collapsedCatGroups[key] = !collapsedCatGroups[key]
}
const addedIds = ref(new Set())
const activeTripCandidates = ref([])
const candidateIdMap = ref(new Map()) // attractionId → candidateId (DB PK)

const attractions = ref([])
const total = ref(0)
const page = ref(0)
const loading = ref(false)
const hasMore = computed(() => attractions.value.length < total.value)

const searchQuery = ref('')
const filterOpen = ref(false)
const selectedRegion = ref('')
const selectedCat = ref('')

const scrollEl = ref(null)
const sentinel = ref(null)
let observer = null

const regions = ['서울', '경기', '강원', '충청', '경상', '전라', '제주']
const categories = ['관광지', '음식점', '숙박', '문화시설', '레포츠']
const PAGE_SIZE = 20

// 후보군 지역 > 카테고리 2단계 그룹
const candidateRegionGroups = computed(() => {
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
    total: Object.values(catMap).reduce((s, arr) => s + arr.length, 0),
    catGroups: Object.entries(catMap).map(([cat, items]) => ({ cat, items })),
  }))
})

// 장소 목록 그룹화
// 지역 필터 선택 시 → 카테고리별 / 그 외 → 지역별
const groupedAttractions = computed(() => {
  if (!attractions.value.length) return []
  if (selectedRegion.value && selectedCat.value) {
    return [{ label: null, items: attractions.value }]
  }
  const key = selectedRegion.value ? 'category' : 'region'
  const groups = {}
  for (const a of attractions.value) {
    const label = a[key] || '기타'
    if (!groups[label]) groups[label] = []
    groups[label].push(a)
  }
  return Object.entries(groups).map(([label, items]) => ({ label, items }))
})

const COLORS = { 12: '#C8C5F5', 14: '#9BD4C0', 28: '#AFE8C0', 32: '#AFC9E8', 38: '#F5D0A9', 39: '#F5C0D2' }
const EMOJIS = { 12: '🏯', 14: '🎨', 28: '🏄', 32: '🏨', 38: '🛍️', 39: '🍜' }
function colorFor(typeId) { return COLORS[typeId] || '#e0e0e0' }
function emojiFor(typeId) { return EMOJIS[typeId] || '📍' }

const CAT_EMOJI = { '관광지': '🏯', '음식점': '🍜', '숙박': '🏨', '문화시설': '🎨', '레포츠': '🏄', '쇼핑': '🛍️' }
function catEmoji(cat) { return CAT_EMOJI[cat] || '📍' }

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
  // resize 완료 이벤트 수신 후 updateMarkers (후보군 마커 + fitMap)
  // resize 전에 fitBounds를 호출하면 SDK가 아직 컨테이너 크기를 모르므로 무효
  setTimeout(() => {
    naver.maps.Event.once(naverMap, 'resize', () => updateMarkers())
    naver.maps.Event.trigger(naverMap, 'resize')
  }, 100)
}

const MAX_ZOOM = 12

function fitMap() {
  if (!naverMap) return
  if (markers.length === 0) {
    // 후보군 없음 → 한국 전체 표시
    naverMap.fitBounds(new naver.maps.LatLngBounds(
      new naver.maps.LatLng(33.0, 124.5),
      new naver.maps.LatLng(38.9, 130.0)
    ), { top: 20, right: 20, bottom: 20, left: 20 })
    return
  }
  // 후보군 있음 → 마커 범위에 맞춤 (zoom 상한 12)
  const bounds = new naver.maps.LatLngBounds()
  markers.forEach(m => bounds.extend(m.getPosition()))
  naverMap.fitBounds(bounds, { top: 60, right: 40, bottom: 60, left: 40 })
  setTimeout(() => { if (naverMap.getZoom() > MAX_ZOOM) naverMap.setZoom(MAX_ZOOM) }, 150)
}

function clearMarkers() {
  markers.forEach(m => m.setMap(null))
  markers = []
  markerIdSet = new Set()
  infoWindow?.close()
}

// 후보군 마커: activeTripCandidates 기준
function updateMarkers() {
  if (!naverMap) return
  clearMarkers()
  console.log('[map] updateMarkers 호출, 후보군:', activeTripCandidates.value.length, '개',
    activeTripCandidates.value.map(c => `${c.attractionName}(lat:${c.latitude},lng:${c.longitude})`))
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

// 활성 일정 변경 시 후보군 로드
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

// ── 후보 제거 ──
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
  e.dataTransfer.effectAllowed = 'copy'
  e.dataTransfer.setData('type', 'attraction')
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

async function loadAttractions(append = false) {
  if (loading.value) return
  loading.value = true
  try {
    const data = await searchAttractions({
      keyword: searchQuery.value || undefined,
      region: selectedRegion.value || undefined,
      category: selectedCat.value || undefined,
      page: page.value,
      size: PAGE_SIZE,
    })
    if (append) {
      attractions.value = [...attractions.value, ...data.items]
    } else {
      attractions.value = data.items
    }
    total.value = data.total
  } catch {
    toast.show('관광지 로드 실패')
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loading.value || !hasMore.value) return
  page.value++
  await loadAttractions(true)
}

function applyFilters() {
  page.value = 0
  loadAttractions(false)
}

function clearFilters() {
  selectedRegion.value = ''
  selectedCat.value = ''
  searchQuery.value = ''
  page.value = 0
  loadAttractions(false)
  toast.show('필터가 초기화됐어요')
}

async function addToTrip(attraction) {
  if (addedIds.value.has(attraction.id)) return
  if (!activeTrip.value) {
    toast.show('먼저 일정을 선택해주세요.')
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

  // 무한 스크롤 IntersectionObserver
  observer = new IntersectionObserver(entries => {
    if (entries[0].isIntersecting) loadMore()
  }, { root: scrollEl.value, rootMargin: '200px' })
  watch(sentinel, el => {
    observer.disconnect()
    if (el) observer.observe(el)
  }, { immediate: true })

  try {
    await loadNaverScript()
    initMap()
  } catch (e) {
    console.error('Naver Maps 로드 실패:', e)
    toast.show('지도를 불러오지 못했어요')
  }
})

onUnmounted(() => observer?.disconnect())
</script>

<style scoped>
/* flex 체인의 height 전파 불안정 문제: 명시적 높이로 직접 고정 */
.schedule-list-sidebar,
.attr-list {
  height: calc(100vh - 48px);
}
</style>
