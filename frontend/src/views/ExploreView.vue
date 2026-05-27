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
             class="schedule-card" :class="{ active: activeTrip === s.id }"
             @click="activeTrip = s.id">
          <div class="schedule-card-top">
            <span class="schedule-card-name">{{ s.title }}</span>
            <button class="schedule-select-btn">{{ activeTrip === s.id ? '✓ 선택됨' : '선택' }}</button>
          </div>
          <div class="schedule-card-meta">
            <span>👥 {{ s.memberCount }}명</span>
            <span class="schedule-dates">{{ s.startDate }} ~ {{ s.endDate }}</span>
          </div>
          <div class="schedule-regions">
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
    <div class="attr-list">
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

      <div v-if="loading" style="padding:40px;text-align:center;color:var(--gray-muted)">로딩 중...</div>
      <div v-else class="cards-grid">
        <div v-for="a in attractions" :key="a.id"
             class="attr-card" :class="{ candidate: addedIds.has(a.id) }">
          <div v-if="addedIds.has(a.id)" class="candidate-badge">✓</div>
          <div class="card-img" :style="{ background: colorFor(a.contentTypeId) }">
            <img v-if="a.firstImage" :src="a.firstImage" style="width:100%;height:100%;object-fit:cover" />
            <span v-else>{{ emojiFor(a.contentTypeId) }}</span>
          </div>
          <div class="card-info">
            <div class="card-name">{{ a.title }} <span style="color:var(--gray-border)">{{ a.favorited ? '★' : '☆' }}</span></div>
            <p class="card-cat">{{ a.category }} · {{ a.region }}</p>
            <button class="card-add" :class="{ added: addedIds.has(a.id) }"
                    @click="addToTrip(a)">
              {{ addedIds.has(a.id) ? '✓ 추가됨' : '+ 일정에 추가' }}
            </button>
          </div>
        </div>
      </div>

      <div v-if="totalPages > 1" class="pagination" style="margin-top:12px">
        <button class="page-btn" :disabled="page === 0" @click="goPage(page - 1)">‹</button>
        <button v-for="p in totalPages" :key="p"
                class="page-btn" :class="{ active: page === p - 1 }"
                @click="goPage(p - 1)">{{ p }}</button>
        <button class="page-btn" :disabled="page >= totalPages - 1" @click="goPage(page + 1)">›</button>
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
import { ref, computed, inject, watch, onMounted } from 'vue'
import { useToastStore } from '@/stores/toast'
import { searchAttractions } from '@/api/attraction'
import { tripApi } from '@/api/trip'

const toast = useToastStore()
const openScheduleModal = inject('openScheduleModal')

const trips = ref([])
const tripsLoading = ref(false)
const activeTrip = ref(null)
const addedIds = ref(new Set())

const attractions = ref([])
const total = ref(0)
const page = ref(0)
const loading = ref(false)

const searchQuery = ref('')
const filterOpen = ref(false)
const selectedRegion = ref('')
const selectedCat = ref('')

const regions = ['서울', '경기', '강원', '충청', '경상', '전라', '제주']
const categories = ['관광지', '음식점', '숙박', '문화시설', '레포츠']
const PAGE_SIZE = 20

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

const COLORS = { 12: '#C8C5F5', 14: '#9BD4C0', 28: '#AFE8C0', 32: '#AFC9E8', 38: '#F5D0A9', 39: '#F5C0D2' }
const EMOJIS = { 12: '🏯', 14: '🎨', 28: '🏄', 32: '🏨', 38: '🛍️', 39: '🍜' }
function colorFor(typeId) { return COLORS[typeId] || '#e0e0e0' }
function emojiFor(typeId) { return EMOJIS[typeId] || '📍' }

// ── Naver Maps ──
const mapEl = ref(null)
let naverMap = null
let markers = []
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
    center: new naver.maps.LatLng(36.5, 127.5),
    zoom: 7,
  })
  infoWindow = new naver.maps.InfoWindow({ zIndex: 1 })
}

function updateMarkers() {
  if (!naverMap) return
  markers.forEach(m => m.setMap(null))
  markers = []
  infoWindow.close()

  attractions.value.forEach(a => {
    if (!a.latitude || !a.longitude) return
    const position = new naver.maps.LatLng(a.latitude, a.longitude)
    const marker = new naver.maps.Marker({ map: naverMap, position, title: a.title })
    naver.maps.Event.addListener(marker, 'click', () => {
      infoWindow.setContent(`<div style="padding:6px 10px;font-size:12px;white-space:nowrap">${a.title}</div>`)
      infoWindow.open(naverMap, marker)
    })
    markers.push(marker)
  })
}

watch(attractions, updateMarkers)

async function loadTrips() {
  tripsLoading.value = true
  try {
    trips.value = await tripApi.list()
    if (trips.value.length) activeTrip.value = trips.value[0].id
  } catch {
    // 비로그인 상태
  } finally {
    tripsLoading.value = false
  }
}

async function loadAttractions() {
  loading.value = true
  try {
    const data = await searchAttractions({
      keyword: searchQuery.value || undefined,
      region: selectedRegion.value || undefined,
      category: selectedCat.value || undefined,
      page: page.value,
      size: PAGE_SIZE,
    })
    attractions.value = data.items
    total.value = data.total
  } catch {
    toast.show('관광지 로드 실패')
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 0
  loadAttractions()
}

function clearFilters() {
  selectedRegion.value = ''
  selectedCat.value = ''
  searchQuery.value = ''
  page.value = 0
  loadAttractions()
  toast.show('필터가 초기화됐어요')
}

function goPage(p) {
  page.value = p
  loadAttractions()
}

async function addToTrip(attraction) {
  if (addedIds.value.has(attraction.id)) return
  if (!activeTrip.value) {
    toast.show('먼저 일정을 선택해주세요.')
    return
  }
  try {
    await tripApi.addCandidate(activeTrip.value, attraction.id)
    addedIds.value = new Set([...addedIds.value, attraction.id])
    toast.show(`"${attraction.title}" 후보군에 추가됐어요`)
    const t = trips.value.find(t => t.id === activeTrip.value)
    if (t) t.candidateCount++
  } catch (e) {
    toast.show(e.status === 401 ? '로그인이 필요합니다.' : (e.message || '추가 실패'))
  }
}

onMounted(async () => {
  loadTrips()
  loadAttractions()
  try {
    await loadNaverScript()
    initMap()
  } catch (e) {
    console.error('Naver Maps 로드 실패:', e)
    toast.show('지도를 불러오지 못했어요')
  }
})
</script>
