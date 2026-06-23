<template>
  <div class="trips-embed">
    <div class="trips-shell">
      <!-- 헤더: 카드/달력 토글 · 새 여행 (제목은 마이페이지 탭이 담당) -->
      <div class="trips-header">
        <div class="trips-toggle" role="tablist" aria-label="보기 전환">
          <button
            class="trips-toggle-btn"
            :class="{ active: view === 'cards' }"
            role="tab" :aria-selected="view === 'cards'"
            @click="view = 'cards'"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <rect x="3" y="3" width="7" height="7" rx="1" />
              <rect x="14" y="3" width="7" height="7" rx="1" />
              <rect x="3" y="14" width="7" height="7" rx="1" />
              <rect x="14" y="14" width="7" height="7" rx="1" />
            </svg>
            카드
          </button>
          <button
            class="trips-toggle-btn"
            :class="{ active: view === 'calendar' }"
            role="tab" :aria-selected="view === 'calendar'"
            @click="view = 'calendar'"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <rect x="3" y="4" width="18" height="17" rx="2" />
              <path d="M3 9h18M8 2v4M16 2v4" />
            </svg>
            달력
          </button>
        </div>

        <span class="trips-header-spacer"></span>

        <button class="trips-new-btn" @click="openScheduleModal()">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M12 5v14M5 12h14" />
          </svg>
          새 여행
        </button>
      </div>

      <!-- ── 카드 뷰 ── -->
      <template v-if="view === 'cards'">
        <!-- 로딩 스켈레톤 -->
        <div v-if="loading" class="trips-grid">
          <div v-for="n in 6" :key="n" class="trip-card-skeleton">
            <div class="skeleton-line w-70"></div>
            <div class="skeleton-line w-40"></div>
            <div class="skeleton-line w-90"></div>
          </div>
        </div>

        <!-- 빈 상태 -->
        <div v-else-if="trips.length === 0" class="trips-empty-wrap">
          <EmptyState
            title="첫 여행을 시작해보세요"
            message="가고 싶은 곳을 담고 일정을 짜면 여기에 모입니다."
          >
            <button class="trips-new-btn" @click="openScheduleModal()">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                   stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M12 5v14M5 12h14" />
              </svg>
              새 여행 시작
            </button>
          </EmptyState>
        </div>

        <!-- 카드 그리드 -->
        <div v-else class="trips-grid">
          <article
            v-for="(trip, i) in trips"
            :key="trip.id"
            class="trip-card"
            :style="{ '--card-accent': accentInk(i) }"
          >
            <h2 class="trip-card-title">{{ trip.title }}</h2>

            <div class="trip-card-dates">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                   stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <rect x="3" y="4" width="18" height="17" rx="2" />
                <path d="M3 9h18M8 2v4M16 2v4" />
              </svg>
              {{ trip.startDate }} ~ {{ trip.endDate }}
            </div>

            <div class="trip-card-meta">
              <span v-if="trip.memberCount" class="trip-meta-chip">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                     stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
                  <circle cx="9" cy="7" r="4" />
                  <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
                </svg>
                {{ trip.memberCount }}명
              </span>
              <span class="trip-meta-chip">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                     stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M21 10c0 7-9 12-9 12s-9-5-9-12a9 9 0 0 1 18 0Z" />
                  <circle cx="12" cy="10" r="3" />
                </svg>
                담은 곳 {{ trip.candidateCount }}
              </span>
            </div>

            <div class="trip-card-actions">
              <button class="trip-open-btn" @click="openTrip(trip)">열기</button>
              <button
                class="trip-delete-btn"
                :disabled="deletingId === trip.id"
                aria-label="삭제"
                @click="deleteTrip(trip)"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                     stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m2 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
                  <path d="M10 11v6M14 11v6" />
                </svg>
              </button>
            </div>
          </article>

          <!-- 새 여행 추가 카드 -->
          <button class="trip-card-add" @click="openScheduleModal()">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M12 5v14M5 12h14" />
            </svg>
            <span>새 여행 시작</span>
          </button>
        </div>
      </template>

      <!-- ── 달력 뷰 (CalendarBoard 재사용) ── -->
      <div v-else class="trips-calendar-host">
        <CalendarBoard embedded />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, inject, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { tripApi } from '@/api/trip'
import { useActiveTripStore } from '@/stores/activeTrip'
import { useToastStore } from '@/stores/toast'
import CalendarBoard from '@/components/CalendarBoard.vue'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
const activeTrip = useActiveTripStore()
const toast = useToastStore()
const openScheduleModal = inject('openScheduleModal', () => {})

const view = ref('cards')
const trips = ref([])
const loading = ref(false)
const deletingId = ref(null)

// 분류 ink 6색을 카드 좌측 액센트로 순환 사용 (§6 분류 6색 = 전 화면 공통 신호)
const ACCENT_INK = [
  'var(--cat-sights-ink)',
  'var(--cat-culture-ink)',
  'var(--cat-food-ink)',
  'var(--cat-stay-ink)',
  'var(--cat-shop-ink)',
  'var(--cat-leisure-ink)',
]
function accentInk(i) {
  return ACCENT_INK[i % ACCENT_INK.length]
}

function openTrip(trip) {
  activeTrip.set(trip.id)
  router.push(`/plan/${trip.id}`)
}

async function deleteTrip(trip) {
  if (!confirm(`'${trip.title}'을(를) 삭제할까요? 되돌릴 수 없어요.`)) return
  deletingId.value = trip.id
  try {
    await tripApi.delete(trip.id)
    trips.value = trips.value.filter(t => t.id !== trip.id)
    if (activeTrip.id === trip.id) activeTrip.set(null)
    toast.show('여행을 삭제했어요')
  } catch (e) {
    // 백엔드가 공유중 삭제를 막으면 그 메시지를 그대로 노출
    toast.show(e.message || '삭제에 실패했어요')
  } finally {
    deletingId.value = null
  }
}

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
