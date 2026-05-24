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
        <div v-for="s in schedules" :key="s.id"
             class="schedule-card" :class="{ active: activeScheduleId === s.id }"
             @click="activeScheduleId = s.id">
          <div class="schedule-card-top">
            <span class="schedule-card-name">{{ s.name }}</span>
            <button class="schedule-select-btn">{{ activeScheduleId === s.id ? '✓ 선택됨' : '선택' }}</button>
          </div>
          <div class="schedule-card-meta">
            <span>👥 {{ s.people }}명</span>
            <span class="schedule-dates">{{ s.dates }}</span>
          </div>
          <div class="schedule-regions">
            <div v-for="r in s.regions" :key="r.name" class="schedule-region">
              <span class="region-name">📍 {{ r.name }}</span>
              <div class="region-places">
                <span v-for="p in r.places.slice(0, 2)" :key="p" class="place-chip">{{ p }}</span>
                <span v-if="r.places.length > 2" class="place-chip more">+{{ r.places.length - 2 }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="!schedules.length" class="schedule-empty">
          <p>일정이 없습니다</p>
          <button @click="openScheduleModal()">+ 새 일정 만들기</button>
        </div>
      </div>
    </aside>

    <!-- 중앙: 장소 목록 -->
    <div class="attr-list">
      <div class="search-bar">
        <span class="search-icon">🔍</span>
        <input type="text" v-model="searchQuery" placeholder="장소, 도시 검색" />
      </div>

      <div class="filter-bar">
        <button class="filter-toggle-btn" :class="{ open: filterOpen, 'has-selection': selectedRegions.length }"
                @click="filterOpen = !filterOpen">
          지역
          <span v-if="selectedRegions.length" class="filter-count-badge">{{ selectedRegions.length }}</span>
          <span class="filter-arrow">▾</span>
        </button>
        <button class="filter-toggle-btn" :class="{ open: filterOpen, 'has-selection': selectedCats.length }"
                @click="filterOpen = !filterOpen">
          카테고리
          <span v-if="selectedCats.length" class="filter-count-badge">{{ selectedCats.length }}</span>
          <span class="filter-arrow">▾</span>
        </button>
        <button v-if="selectedRegions.length || selectedCats.length"
                class="filter-clear-btn" @click="clearFilters">초기화</button>
      </div>

      <div v-if="filterOpen" class="filter-panel open">
        <div class="filter-panel-section">
          <span class="filter-panel-label">지역</span>
          <div class="chip-group">
            <button v-for="r in regions" :key="r"
                    class="chip" :class="{ sel: selectedRegions.includes(r) }"
                    @click="toggleFilter(selectedRegions, r)">{{ r }}</button>
          </div>
        </div>
        <div class="filter-panel-section">
          <span class="filter-panel-label">카테고리</span>
          <div class="chip-group">
            <button v-for="c in categories" :key="c"
                    class="chip" :class="{ sel: selectedCats.includes(c) }"
                    @click="toggleFilter(selectedCats, c)">{{ c }}</button>
          </div>
        </div>
        <div class="filter-panel-actions">
          <button class="btn-apply-filter" @click="filterOpen = false">필터 적용</button>
        </div>
      </div>

      <p class="result-count">{{ filteredAttractions.length }}개의 장소</p>

      <div class="cards-grid">
        <div v-for="a in filteredAttractions" :key="a.id"
             class="attr-card" :class="{ candidate: a.added }">
          <div v-if="a.added" class="candidate-badge">✓</div>
          <div class="card-img" :style="{ background: a.color }">{{ a.emoji }}</div>
          <div class="card-info">
            <div class="card-name">{{ a.name }} <span :class="a.favorited ? 'star' : ''" style="color:var(--gray-border)">{{ a.favorited ? '★' : '☆' }}</span></div>
            <p class="card-cat">{{ a.category }} · {{ a.region }}</p>
            <button class="card-add" :class="{ added: a.added }" @click="!a.added && addToSchedule(a)">
              {{ a.added ? '✓ 추가됨' : '+ 일정에 추가' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 오른쪽: 지도 (placeholder) -->
    <div class="map-area">
      <div class="map-label">대한민국</div>
      <svg class="map-grid" viewBox="0 0 100 100" preserveAspectRatio="none">
        <line v-for="n in 7" :key="'h'+n" :x1="0" :y1="n*12.5" :x2="100" :y2="n*12.5" stroke="#888780" stroke-width="0.3"/>
        <line v-for="n in 7" :key="'v'+n" :x1="n*12.5" :y1="0" :x2="n*12.5" :y2="100" stroke="#888780" stroke-width="0.3"/>
      </svg>
      <div v-for="a in filteredAttractions" :key="'m'+a.id"
           class="map-marker" :class="{ cand: a.added }"
           :title="a.name"
           :style="{ background: a.markerColor, left: a.mapX, top: a.mapY }">
      </div>
      <div class="map-zoom">
        <button>+</button><button>−</button>
      </div>
    </div>

  </section>
  </main>
</template>

<script setup>
import { ref, computed, inject } from 'vue'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const openScheduleModal = inject('openScheduleModal')

const activeScheduleId = ref(1)
const searchQuery = ref('')
const filterOpen = ref(false)
const selectedRegions = ref([])
const selectedCats = ref([])

const regions = ['서울', '경기', '강원', '충청', '경상', '전라', '제주']
const categories = ['관광지', '음식점', '숙박', '문화시설', '레포츠']

const schedules = ref([
  { id: 1, name: '제주 여름 여행', people: 2, dates: '07.10 ~ 07.13',
    regions: [
      { name: '서울', places: ['경복궁', '남산서울타워', '인사동'] },
      { name: '경주', places: ['불국사', '석굴암', '첨성대', '동궁과월지'] },
    ]},
  { id: 2, name: '경주 가을 여행', people: 3, dates: '09.20 ~ 09.22',
    regions: [
      { name: '경주', places: ['불국사', '동궁과월지'] },
      { name: '부산', places: ['해운대', '광안리'] },
    ]},
])

const attractions = ref([
  { id: 1, name: '경복궁',       category: '관광지',  region: '서울', color: '#C8C5F5', markerColor: '#534AB7', emoji: '🏯', favorited: true,  added: true,  mapX: '22%', mapY: '24%' },
  { id: 2, name: '광장시장',     category: '음식점',  region: '서울', color: '#F5C0D2', markerColor: '#993556', emoji: '🍜', favorited: false, added: false, mapX: '30%', mapY: '30%' },
  { id: 3, name: '남산서울타워', category: '관광지',  region: '서울', color: '#C8C5F5', markerColor: '#534AB7', emoji: '🗼', favorited: true,  added: true,  mapX: '27%', mapY: '36%' },
  { id: 4, name: '인사동 쌈지길',category: '문화시설',region: '서울', color: '#9BD4C0', markerColor: '#0F6E56', emoji: '🎨', favorited: false, added: false, mapX: '40%', mapY: '20%' },
  { id: 5, name: '명동 칼국수',  category: '음식점',  region: '서울', color: '#F5C0D2', markerColor: '#993556', emoji: '🍲', favorited: false, added: false, mapX: '52%', mapY: '32%' },
  { id: 6, name: '롯데호텔 서울',category: '숙박',    region: '서울', color: '#AFC9E8', markerColor: '#185FA5', emoji: '🏨', favorited: false, added: false, mapX: '62%', mapY: '24%' },
  { id: 7, name: '불국사',       category: '관광지',  region: '경주', color: '#C8C5F5', markerColor: '#534AB7', emoji: '⛩️', favorited: true,  added: true,  mapX: '72%', mapY: '50%' },
  { id: 8, name: '첨성대',       category: '문화시설',region: '경주', color: '#9BD4C0', markerColor: '#0F6E56', emoji: '🌙', favorited: false, added: false, mapX: '78%', mapY: '58%' },
  { id: 9, name: '성산일출봉',   category: '관광지',  region: '제주', color: '#C8C5F5', markerColor: '#534AB7', emoji: '🌋', favorited: true,  added: true,  mapX: '58%', mapY: '78%' },
  { id: 10, name: '흑돼지 거리', category: '음식점',  region: '제주', color: '#F5C0D2', markerColor: '#993556', emoji: '🐷', favorited: false, added: false, mapX: '46%', mapY: '82%' },
])

const filteredAttractions = computed(() => {
  return attractions.value.filter(a => {
    if (searchQuery.value && !a.name.includes(searchQuery.value) && !a.region.includes(searchQuery.value)) return false
    if (selectedRegions.value.length && !selectedRegions.value.includes(a.region)) return false
    if (selectedCats.value.length && !selectedCats.value.includes(a.category)) return false
    return true
  })
})

function toggleFilter(list, value) {
  const idx = list.indexOf(value)
  if (idx === -1) list.push(value)
  else list.splice(idx, 1)
}

function clearFilters() {
  selectedRegions.value = []
  selectedCats.value = []
  toast.show('필터가 초기화됐어요')
}

function addToSchedule(attraction) {
  attraction.added = true
  toast.show(`"${attraction.name}" 후보군에 추가됐어요`)
}
</script>
