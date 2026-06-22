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

      <template v-if="trips.length || collaboratingTrips.length">
        <div class="toolbar-select-wrap">
          <select class="toolbar-select" v-model="activeTripId" @change="loadTrip">
            <optgroup v-if="trips.length" label="내 일정">
              <option v-for="t in trips" :key="t.id" :value="t.id">{{ t.title }}</option>
            </optgroup>
            <optgroup v-if="collaboratingTrips.length" label="초대받은 일정">
              <option v-for="t in collaboratingTrips" :key="t.id" :value="t.id">{{ t.title }}</option>
            </optgroup>
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

      <button class="btn-map-view" @click="openMapPanel()">🗺 지도로 보기</button>
      <button v-if="activeTrip" class="btn-share-trip" @click="shareToComm">📢 공유하기</button>
      <button v-if="activeTrip" class="btn-collab" :class="{ active: collabPanelOpen }"
              @click="collabPanelOpen = !collabPanelOpen">
        <span class="collab-status-dot" :class="{ connected: collab.connected }" :title="collab.connected ? '실시간 연결됨' : '연결 끊김'"></span>
        👥 협업자
      </button>
      <button class="btn-new-trip" @click="openScheduleModal()">+ 새 일정</button>
    </div>

    <!-- 협업자 패널 (툴바 아래 슬라이드) -->
    <Transition name="collab-slide">
      <div v-if="collabPanelOpen && activeTrip" class="collab-panel-overlay">
        <CollaboratorPanel
          :trip-id="activeTrip.id"
          :is-owner="activeTripIsOwner"
          :owner-label="activeTripOwnerLabel"
          @close="collabPanelOpen = false"
        />
      </div>
    </Transition>

    <!-- BODY: 사이드바 + 시간표 + 지도 패널 -->
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

        <div class="timetable-wrapper" ref="wrapperEl" @scroll="openPillKey = null">
          <div class="timetable-header">
            <div class="th-gutter"></div>
            <div v-for="d in days" :key="d.label" class="th-day">
              <span class="th-badge">{{ d.label }}</span>
              <span class="th-date">{{ d.date }}</span>
            </div>
          </div>

          <div class="timetable-body" @click="openPillKey = null">
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
                     :class="{
                       'transit-pill-none': pill.transportMode === 'NONE',
                       'transit-pill-open': openPillKey === pillKey(pill)
                     }"
                     :style="{ top: pill.top + 'px', height: Math.max(pill.durationMinutes || 24, 24) + 'px' }"
                     @click.stop="togglePillDropdown(pill, $event)">
                  <span class="transit-pill-text">
                    <template v-if="pill.transportMode === 'NONE'">경로 없음</template>
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

      <!-- 지도 패널 -->
      <Transition name="map-panel-slide" @after-enter="onMapPanelEntered">
        <div v-if="showMapPanel"
             class="map-panel"
             :class="{ 'map-panel--fullscreen': mapFullscreen }"
             :style="mapFullscreen ? {} : { width: mapPanelWidth + 'px' }">
          <div v-if="!mapFullscreen" class="map-resize-handle" @mousedown.prevent="onPanelResizeStart"></div>
          <div class="map-panel-header">
            <div class="map-day-tabs">
              <button v-for="d in days" :key="d.label"
                      class="map-day-tab" :class="{ active: mapDay === d }"
                      @click="selectMapDay(d)">{{ d.label }}</button>
            </div>
            <button class="map-panel-btn" :title="mapFullscreen ? '화면 복원' : '전체화면'"
                    @click="toggleMapFullscreen">{{ mapFullscreen ? '⊡' : '⊞' }}</button>
            <button class="map-panel-close" @click="closeMapPanel">✕</button>
          </div>
          <div ref="mapEl" class="map-el"></div>
        </div>
      </Transition>
    </div>

  </section>

  <!-- 이동수단 모달 -->
  <Teleport to="body">
    <div v-if="openPillKey && currentPillData"
         class="transit-modal-overlay"
         @click.self="openPillKey = null; currentPillData = null">
      <div class="transit-modal" @click.stop>
        <div class="transit-modal-header">
          <span class="transit-modal-title">이동 수단 선택</span>
          <button class="transit-modal-close" @click="openPillKey = null; currentPillData = null">✕</button>
        </div>

        <div class="transit-mode-tabs">
          <button v-for="opt in modeOptions" :key="opt.mode"
                  class="transit-mode-tab"
                  :class="{ active: selectedModalMode === opt.mode }"
                  @click="onModeTabClick(opt.mode)">
            <span class="transit-tab-label">{{ opt.label }}</span>
            <span v-if="pillLoadingModes[`${openPillKey}-${opt.mode}`]" class="transit-tab-spinner"></span>
            <span v-else-if="pillResults[openPillKey]?.[opt.mode]?.durationMinutes > 0"
                  class="transit-tab-time">
              {{ pillResults[openPillKey][opt.mode].durationMinutes }}분
            </span>
          </button>
        </div>

        <div class="transit-modal-body">
          <!-- 대중교통: 경로 탭 + step 표시 -->
          <template v-if="selectedModalMode === 'PUBLIC_TRANSIT'">
            <div v-if="pillPublicTransitPaths[openPillKey] === undefined" class="transit-body-hint">
              경로 불러오는 중...
            </div>
            <template v-else>
              <div v-if="!pillPublicTransitPaths[openPillKey].length" class="transit-body-hint">
                이용 가능한 경로가 없어요
              </div>
              <template v-else>
                <div v-if="pillPublicTransitPaths[openPillKey].length > 1" class="transit-route-tabs">
                  <button v-for="(path, idx) in pillPublicTransitPaths[openPillKey]" :key="idx"
                          class="transit-route-tab"
                          :class="{ active: selectedPublicPathIndex === idx }"
                          @click="selectedPublicPathIndex = idx">
                    <span class="transit-route-tab-label">{{ path.label }}</span>
                    <span class="transit-route-tab-time">{{ path.minutes }}분</span>
                  </button>
                </div>
                <div class="transit-steps">
                  <template v-for="(step, si) in currentPublicPath?.steps" :key="si">
                    <div class="transit-step" :class="step.stepClass">
                      <span class="transit-step-icon">{{ step.icon }}</span>
                      <div class="transit-step-body">
                        <div class="transit-step-main">
                          <span class="transit-step-title">{{ step.title }}</span>
                          <span v-if="step.route" class="transit-step-route">{{ step.route }}</span>
                          <span v-if="step.detail" class="transit-step-detail">{{ step.detail }}</span>
                        </div>
                        <span class="transit-step-time">{{ step.time }}분</span>
                      </div>
                    </div>
                    <div v-if="si < (currentPublicPath?.steps?.length ?? 0) - 1" class="transit-connector"></div>
                  </template>
                </div>
              </template>
            </template>
          </template>

          <!-- 자동차: 세로 카드 리스트 -->
          <template v-else-if="selectedModalMode === 'DRIVING'">
            <div class="transit-route-tabs">
              <button v-for="(label, idx) in DRIVING_OPTION_LABELS" :key="idx"
                      class="transit-route-tab"
                      :class="{ active: selectedDrivingOptionIndex === idx }"
                      @click="onDrivingOptionTabClick(idx)">
                <span class="transit-route-tab-label">{{ label }}</span>
                <span v-if="pillLoadingModes[`${openPillKey}-driving-${idx}`]" class="transit-tab-spinner"></span>
                <span v-else-if="pillDrivingOptions[openPillKey]?.[idx]?.durationMinutes"
                      class="transit-route-tab-time">
                  {{ pillDrivingOptions[openPillKey][idx].durationMinutes }}분
                </span>
              </button>
            </div>
            <div v-if="pillLoadingModes[`${openPillKey}-driving-${selectedDrivingOptionIndex}`]"
                 class="transit-body-hint">경로 계산 중...</div>
            <div v-else-if="currentDrivingOption" class="transit-steps">
              <!-- 요약 첫 줄 -->
              <div class="transit-step transit-step--drive-summary">
                <span class="transit-step-icon">🚗</span>
                <div class="transit-step-body">
                  <div class="transit-step-main">
                    <span class="transit-step-title">{{ currentDrivingOption.roadSummary || '경로' }}</span>
                  </div>
                </div>
              </div>
              <!-- 전체 구간 -->
              <template v-for="(seg, si) in currentDrivingSegments" :key="si">
                <div class="transit-connector"></div>
                <div class="transit-step transit-step--walk">
                  <span class="transit-step-icon">🛣</span>
                  <div class="transit-step-body">
                    <div class="transit-step-main">
                      <span class="transit-step-title">{{ seg.name }}</span>
                      <span class="transit-step-route">
                        {{ (seg.distanceM / 1000).toFixed(1) }}km · {{ Math.ceil(seg.timeSec / 60) }}분
                        <template v-if="seg.speedKmh > 0"> · {{ seg.speedKmh }}km/h</template>
                      </span>
                      <span v-if="ROAD_TYPE_LABEL[seg.roadType]" class="transit-step-detail">{{ ROAD_TYPE_LABEL[seg.roadType] }}</span>
                    </div>
                  </div>
                </div>
              </template>
            </div>
            <div v-else-if="pillDrivingOptions[openPillKey]?.[selectedDrivingOptionIndex] === null"
                 class="transit-body-hint">경로를 찾을 수 없어요</div>
          </template>

          <!-- 도보: 단일 결과 + 구간 스텝 -->
          <template v-else-if="selectedModalMode === 'WALKING'">
            <div v-if="pillLoadingModes[`${openPillKey}-WALKING`]" class="transit-body-hint">
              경로 계산 중...
            </div>
            <template v-else-if="currentWalkingResult?.durationMinutes">
              <div class="transit-steps">
                <div class="transit-step transit-step--drive-summary">
                  <span class="transit-step-icon">🚶</span>
                  <div class="transit-step-body">
                    <div class="transit-step-main">
                      <span class="transit-step-title">{{ currentWalkingResult.roadSummary || '도보 경로' }}</span>
                    </div>
                  </div>
                </div>
                <template v-for="(seg, si) in currentWalkingSegments" :key="si">
                  <div class="transit-connector"></div>
                  <div class="transit-step transit-step--walk">
                    <span class="transit-step-icon">🚶</span>
                    <div class="transit-step-body">
                      <div class="transit-step-main">
                        <span class="transit-step-title">{{ seg.name }}</span>
                        <span class="transit-step-route">
                          {{ (seg.distanceM / 1000).toFixed(1) }}km · {{ Math.ceil(seg.timeSec / 60) }}분
                        </span>
                      </div>
                    </div>
                  </div>
                </template>
              </div>
            </template>
            <div v-else class="transit-body-hint">경로를 찾을 수 없어요</div>
          </template>
        </div>

        <!-- 하단 푸터: 요약 + 저장 -->
        <div v-if="footerVisible" class="transit-footer">
          <div class="transit-summary">
            <div class="transit-summary-item">
              <span class="transit-summary-label">총 소요</span>
              <span class="transit-summary-value">{{ footerMinutes }}분</span>
            </div>
            <div v-if="footerFare > 0" class="transit-summary-item">
              <span class="transit-summary-label">{{ footerFareLabel }}</span>
              <span class="transit-summary-value">{{ footerFare.toLocaleString() }}원</span>
            </div>
            <div v-if="footerTollFare > 0" class="transit-summary-item">
              <span class="transit-summary-label">통행료</span>
              <span class="transit-summary-value">{{ footerTollFare.toLocaleString() }}원</span>
            </div>
            <div v-if="footerDist" class="transit-summary-item">
              <span class="transit-summary-label">거리</span>
              <span class="transit-summary-value">{{ footerDist }}</span>
            </div>
            <div v-if="footerTransfers > 0" class="transit-summary-item">
              <span class="transit-summary-label">환승</span>
              <span class="transit-summary-value">{{ footerTransfers }}회</span>
            </div>
          </div>
          <button class="transit-save-btn" @click="onTransitSave">이 경로로 저장</button>
        </div>
      </div>
    </div>
  </Teleport>

  </main>
</template>

<script setup>
import { ref, computed, reactive, onMounted, onUnmounted, nextTick, inject, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import { useCollabStore } from '@/stores/collab'
import { tripApi } from '@/api/trip'
import CollaboratorPanel from '@/components/CollaboratorPanel.vue'
import { getTransitByMode, getTransitDetail, selectTransitPath, getDrivingOption, applyDrivingOption, getLaneSegments, getWalkingCoords } from '@/api/transit'

const toast = useToastStore()
const auth = useAuthStore()
const collab = useCollabStore()
const router = useRouter()
const _openScheduleModal = inject('openScheduleModal')
function openScheduleModal() {
  _openScheduleModal((newTrip) => {
    trips.value = [...trips.value, newTrip]
    activeTripId.value = newTrip.id
    loadTrip()
  })
}
const wrapperEl = ref(null)
const mapEl = ref(null)

const HOUR_PX = 60
const SNAP = 30

// ── UI state ──
const sidebarOpen = ref(true)

const trips = ref([])
const collaboratingTrips = ref([])
const tripsLoading = ref(false)
const activeTripId = ref(null)
const collabPanelOpen = ref(false)

const activeTripIsOwner = computed(() =>
  trips.value.some(t => t.id === activeTripId.value)
)
const activeTripOwnerLabel = computed(() => activeTrip.value?.ownerNickname ?? '소유자')
const activeTrip = ref(null)
const candidates = ref([])
const days = ref([])

// ── 이동수단 모달 ──
const openPillKey = ref(null)
const currentPillData = ref(null)
const selectedModalMode = ref('PUBLIC_TRANSIT')
const selectedPublicPathIndex = ref(0)
const selectedDrivingOptionIndex = ref(0)
const pillResults = reactive({})
const pillLoadingModes = reactive({})
const pillPublicTransitPaths = reactive({})
const pillDrivingOptions = reactive({})

const DRIVING_OPTION_LABELS = ['추천', '최단시간', '무료도로', '최소거리']

const modeOptions = [
  { mode: 'PUBLIC_TRANSIT', icon: '🚌', label: '대중교통' },
  { mode: 'DRIVING', icon: '🚗', label: '자동차' },
  { mode: 'WALKING', icon: '🚶', label: '도보' },
]

const currentPublicPath = computed(() =>
  pillPublicTransitPaths[openPillKey.value]?.[selectedPublicPathIndex.value]
)

const currentDrivingOption = computed(() =>
  pillDrivingOptions[openPillKey.value]?.[selectedDrivingOptionIndex.value]
)

const ROAD_TYPE_LABEL = { 1: '고속도로', 2: '자동차전용', 3: '국도', 4: '지방도' }

const currentDrivingSegments = computed(() => {
  const json = currentDrivingOption.value?.routeSegmentsJson
  if (!json) return []
  try { return JSON.parse(json) } catch { return [] }
})

const currentWalkingResult = computed(() =>
  pillResults[openPillKey.value]?.['WALKING']
)

const currentWalkingSegments = computed(() => {
  const json = currentWalkingResult.value?.routeSegmentsJson
  if (!json) return []
  try { return JSON.parse(json) } catch { return [] }
})

const footerVisible = computed(() => {
  if (!openPillKey.value) return false
  if (selectedModalMode.value === 'PUBLIC_TRANSIT') return !!currentPublicPath.value
  if (selectedModalMode.value === 'DRIVING') return !!currentDrivingOption.value?.durationMinutes
  return !!pillResults[openPillKey.value]?.[selectedModalMode.value]?.durationMinutes
})

const footerMinutes = computed(() => {
  if (selectedModalMode.value === 'PUBLIC_TRANSIT') return currentPublicPath.value?.minutes ?? 0
  if (selectedModalMode.value === 'DRIVING') return currentDrivingOption.value?.durationMinutes ?? 0
  return pillResults[openPillKey.value]?.[selectedModalMode.value]?.durationMinutes ?? 0
})

const footerFare = computed(() => {
  if (selectedModalMode.value === 'PUBLIC_TRANSIT') return currentPublicPath.value?.fare ?? 0
  if (selectedModalMode.value === 'DRIVING') return currentDrivingOption.value?.taxiFare ?? 0
  return 0
})

const footerTollFare = computed(() => {
  if (selectedModalMode.value !== 'DRIVING') return 0
  return currentDrivingOption.value?.tollFare ?? 0
})

const footerFareLabel = computed(() =>
  selectedModalMode.value === 'DRIVING' ? '택시요금' : '요금'
)

const footerDist = computed(() => {
  if (selectedModalMode.value === 'PUBLIC_TRANSIT') return ''
  if (selectedModalMode.value === 'DRIVING') {
    const m = currentDrivingOption.value?.totalDistanceM
    return m ? formatDistM(m) : ''
  }
  const m = pillResults[openPillKey.value]?.[selectedModalMode.value]?.totalDistanceM
  return m ? formatDistM(m) : ''
})

const footerTransfers = computed(() => {
  if (selectedModalMode.value !== 'PUBLIC_TRANSIT') return 0
  return currentPublicPath.value?.transferCount ?? 0
})

function onTransitSave() {
  if (selectedModalMode.value === 'PUBLIC_TRANSIT') {
    selectPublicTransitPath(currentPillData.value, selectedPublicPathIndex.value)
  } else if (selectedModalMode.value === 'DRIVING' && currentDrivingOption.value?.durationMinutes) {
    selectDrivingOption(currentPillData.value, selectedDrivingOptionIndex.value)
  } else {
    selectPillMode(currentPillData.value, selectedModalMode.value)
  }
}

// ── 지도 패널 ──
const showMapPanel = ref(false)
const mapFullscreen = ref(false)
const mapPanelWidth = ref(400)
const mapDay = ref(null)
let naverMapInstance = null
let routePolylines = []
let routeMarkers = []
let routeMarkerGroups = new Map()  // routeKey → { expand, collapse, polyline, style }
let pinnedRouteKey = null
let polylineClicked = false        // 폴리라인 click이 지도 click으로 전파되는 것을 막기 위한 플래그
let drawGeneration = 0             // drawDayRoute 호출마다 증가 — 이전 async 작업 취소용
let panelResizing = false
let panelResizeStartX = 0
let panelResizeStartWidth = 0

let dragState = null
const dragPreview = ref(null)
const isProcessing = ref(false)
const processingEvId = ref(null)
const sidebarDragOver = ref(false)
const sidebarDropActive = computed(() => sidebarDragOver.value && dragState?.type === 'event')

const TRANSPORT_DISPLAY = {
  BUS: '버스', SUBWAY: '지하철', RAIL: 'KTX/기차', EXPRESSBUS: '고속버스',
  INTERCITYBUS: '시외버스', WALK: '도보', CAR: '자동차', AIRPLANE: '항공', FERRY: '해운', NONE: '-',
  DRIVING: '자동차', WALKING: '도보', PUBLIC_TRANSIT: '대중교통',
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
    transitOptionIndex: b.transitOptionIndex ?? null,
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
        transitOptionIndex: curr.transitOptionIndex,
        fromAttractionId: prevCand?.attractionId,
        toAttractionId: currCand?.attractionId,
        departureHour: Math.min(Math.floor(pillTop / 60), 23),
        toBlockId: curr.id,
        toBlockDate: curr.tripDate,
        toBlockStartTime: topToTime(curr.top) + ':00',
        toBlockDuration: curr.height,
        toBlockOrder: curr.displayOrder ?? 1,
      })
    }
  }
  return pills
}

function pillKey(pill) {
  return `${pill.fromAttractionId}-${pill.toAttractionId}-${pill.departureHour}`
}

function togglePillDropdown(pill, event) {
  event.stopPropagation()
  const key = pillKey(pill)
  if (openPillKey.value === key) {
    openPillKey.value = null
    currentPillData.value = null
    return
  }
  openPillKey.value = key
  currentPillData.value = pill
  const cur = pill.transportMode
  selectedModalMode.value = (cur === 'DRIVING' || cur === 'WALKING') ? cur : 'PUBLIC_TRANSIT'
  selectedPublicPathIndex.value = pill.transitOptionIndex ?? 0
  selectedDrivingOptionIndex.value = pill.transitOptionIndex ?? 0
  if (!pill.fromAttractionId || !pill.toAttractionId) return
  loadTabData(pill, selectedModalMode.value)
}

function loadTabData(pill, mode) {
  if (mode === 'PUBLIC_TRANSIT') {
    fetchPillMode(pill, 'PUBLIC_TRANSIT')
    fetchPublicTransitPaths(pill)
  } else if (mode === 'DRIVING') {
    fetchPillMode(pill, 'DRIVING')
    fetchDrivingOption(pill, 0)
    const savedIdx = pill.transitOptionIndex ?? 0
    if (savedIdx > 0) fetchDrivingOption(pill, savedIdx)
  } else if (mode === 'WALKING') {
    fetchPillMode(pill, 'WALKING')
  }
}

function onModeTabClick(mode) {
  selectedModalMode.value = mode
  selectedPublicPathIndex.value = 0
  selectedDrivingOptionIndex.value = 0
  if (currentPillData.value) loadTabData(currentPillData.value, mode)
}

function onDrivingOptionTabClick(idx) {
  selectedDrivingOptionIndex.value = idx
  if (currentPillData.value) fetchDrivingOption(currentPillData.value, idx)
}

async function fetchPillMode(pill, mode) {
  const key = pillKey(pill)
  const loadKey = `${key}-${mode}`
  if (pillLoadingModes[loadKey] || pillResults[key]?.[mode] !== undefined) return
  pillLoadingModes[loadKey] = true
  try {
    const result = await getTransitByMode(pill.fromAttractionId, pill.toAttractionId, mode, pill.departureHour)
    if (!pillResults[key]) pillResults[key] = {}
    pillResults[key][mode] = result
  } catch {
    if (!pillResults[key]) pillResults[key] = {}
    pillResults[key][mode] = null
  } finally {
    pillLoadingModes[loadKey] = false
  }
}

async function selectPillMode(pill, mode) {
  if (!pill?.toBlockId) return
  const key = pillKey(pill)
  if (pillResults[key]?.[mode] === undefined && !pillLoadingModes[`${key}-${mode}`]) {
    await fetchPillMode(pill, mode)
  }
  const r = pillResults[key]?.[mode]
  try {
    await tripApi.updateBlock(activeTripId.value, pill.toBlockId, {
      tripDate: pill.toBlockDate,
      startTime: pill.toBlockStartTime,
      durationMinutes: pill.toBlockDuration,
      displayOrder: pill.toBlockOrder,
      transitMode: mode,
      transitDurationMinutes: r?.durationMinutes ?? null,
      taxiFare: r?.taxiFare ?? null,
    })
    delete pillResults[key]
    openPillKey.value = null
    currentPillData.value = null
    await loadTrip()
    toast.show('이동 수단이 변경됐어요')
  } catch (err) {
    toast.show(err.message || '변경 실패')
  }
}

function formatDistM(m) {
  if (!m) return ''
  return m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${m}m`
}

const PATH_TYPE_LABEL = { 1: '지하철', 2: '버스', 3: '버스+지하철', 11: '열차', 12: '고속/시외버스', 13: '항공', 20: '복합' }

function parseStep(sub) {
  const lane = sub.lane?.[0] || {}
  const icons = { 1: '🚇', 2: '🚌', 3: '🚶', 4: '🚅', 5: '🚌', 6: '🚌', 7: '✈️' }
  const classes = { 1: 'transit-step--subway', 2: 'transit-step--bus', 3: 'transit-step--walk', 4: 'transit-step--rail', 5: 'transit-step--bus', 6: 'transit-step--bus' }
  const icon = icons[sub.trafficType] || '🚌'
  const stepClass = classes[sub.trafficType] || ''
  let title = '', route = '', detail = ''
  if (sub.trafficType === 1) {
    title = `${sub.startName} → ${sub.endName}`
    route = lane.name || `${lane.subwayCode}호선`
    detail = sub.stationCount ? `${sub.stationCount}정거장` : ''
  } else if (sub.trafficType === 2) {
    title = `${sub.startName} → ${sub.endName}`
    route = lane.busNo ? `${lane.busNo}번` : '버스'
    detail = sub.stationCount ? `${sub.stationCount}정거장` : ''
  } else if (sub.trafficType === 3) {
    title = '도보'
    detail = sub.distance ? `${sub.distance}m` : ''
  } else if (sub.trafficType === 4) {
    title = `${sub.startName} → ${sub.endName}`
    route = lane.name || 'KTX'
    detail = sub.stationCount ? `${sub.stationCount}정거장` : ''
  } else if (sub.trafficType === 5 || sub.trafficType === 6) {
    title = `${sub.startName} → ${sub.endName}`
    route = lane.busNo ? `${lane.busNo}번` : '고속/시외버스'
  }
  return { icon, stepClass, title, route, detail, time: sub.sectionTime }
}

function parseTransitPaths(detail) {
  const paths = []
  for (const path of detail?.intercityPaths || []) {
    const pathType = path.pathType || 0
    const info = path.info || {}
    const interTime = info.totalTime || 0
    const localFrom = path.localFrom?.minutes || 0
    const localTo = path.localTo?.minutes || 0
    const totalMinutes = pathType < 11 ? interTime : localFrom + interTime + localTo
    const fare = info.totalPayment || info.payment || 0
    const totalWalkM = info.totalWalk || 0
    const transferCount = Math.max(0, (info.transitCount ?? 0) - 1) || ((info.busTransitCount ?? 0) + (info.subwayTransitCount ?? 0))
    const label = PATH_TYPE_LABEL[pathType] || '기타'
    const steps = (path.subPath || []).map(parseStep)
    const segments = []
    for (const sub of path.subPath || []) {
      if (sub.trafficType === 3) continue
      const lane = sub.lane?.[0] || {}
      const stops = sub.stationCount || 0
      if (sub.trafficType === 2) {
        const busNo = lane.busNo || lane.busNoGov || ''
        segments.push({ name: busNo ? `${busNo}번` : '버스', stops })
      } else if (sub.trafficType === 1) {
        segments.push({ name: lane.name || `${lane.subwayCode}호선`, stops })
      } else if (sub.trafficType === 4) {
        segments.push({ name: lane.name || 'KTX', stops })
      } else if (sub.trafficType === 5 || sub.trafficType === 6) {
        segments.push({ name: lane.busNo ? `${lane.busNo}번` : '고속버스', stops })
      }
    }
    if (totalMinutes > 0) {
      paths.push({ minutes: totalMinutes, fare, segments, totalWalkM, label, steps, transferCount })
    }
  }
  return paths
}

async function fetchPublicTransitPaths(pill) {
  const key = pillKey(pill)
  if (pillPublicTransitPaths[key] !== undefined) return
  if (!pill.fromAttractionId || !pill.toAttractionId) return
  // fetchPillMode는 이미 병렬로 실행 중일 수 있어 즉시 리턴됨 → 직접 await로 캐시 완료 보장
  try { await getTransitByMode(pill.fromAttractionId, pill.toAttractionId, 'PUBLIC_TRANSIT', pill.departureHour) } catch {}
  try {
    const detail = await getTransitDetail(pill.fromAttractionId, pill.toAttractionId, pill.departureHour)
    pillPublicTransitPaths[key] = parseTransitPaths(detail)
  } catch {
    pillPublicTransitPaths[key] = []
  }
}

async function selectPublicTransitPath(pill, pathIndex) {
  if (!pill?.fromAttractionId || !pill?.toAttractionId) return
  try {
    await selectTransitPath(pill.fromAttractionId, pill.toAttractionId, pill.departureHour, pathIndex)
    const key = pillKey(pill)
    delete pillResults[key]
    openPillKey.value = null
    currentPillData.value = null
    await loadTrip()
    toast.show('이동 수단이 변경됐어요')
  } catch (err) {
    toast.show(err.message || '변경 실패')
  }
}

async function fetchDrivingOption(pill, optionIndex) {
  const key = pillKey(pill)
  if (!pill.fromAttractionId || !pill.toAttractionId) return
  if (!pillDrivingOptions[key]) pillDrivingOptions[key] = new Array(4).fill(undefined)
  if (pillDrivingOptions[key][optionIndex] !== undefined) return
  const loadKey = `${key}-driving-${optionIndex}`
  if (pillLoadingModes[loadKey]) return
  pillLoadingModes[loadKey] = true
  try {
    const result = await getDrivingOption(pill.fromAttractionId, pill.toAttractionId, pill.departureHour, optionIndex)
    pillDrivingOptions[key][optionIndex] = result
  } catch {
    pillDrivingOptions[key][optionIndex] = null
  } finally {
    pillLoadingModes[loadKey] = false
  }
}

async function selectDrivingOption(pill, optionIndex) {
  if (!pill?.toBlockId) return
  const key = pillKey(pill)
  const opt = pillDrivingOptions[key]?.[optionIndex]
  try {
    await Promise.all([
      tripApi.updateBlock(activeTripId.value, pill.toBlockId, {
        tripDate: pill.toBlockDate,
        startTime: pill.toBlockStartTime,
        durationMinutes: pill.toBlockDuration,
        displayOrder: pill.toBlockOrder,
        transitMode: 'DRIVING',
        transitDurationMinutes: opt?.durationMinutes ?? null,
        taxiFare: opt?.taxiFare ?? null,
        transitOptionIndex: optionIndex,
      }),
      applyDrivingOption(pill.fromAttractionId, pill.toAttractionId, pill.departureHour, optionIndex),
    ])
    delete pillResults[key]
    openPillKey.value = null
    currentPillData.value = null
    await loadTrip()
    toast.show('이동 수단이 변경됐어요')
  } catch (err) {
    toast.show(err.message || '변경 실패')
  }
}

// ── 지도 ──
function loadNaverMapScript() {
  return new Promise((resolve, reject) => {
    if (window.naver?.maps) { resolve(); return }
    const existing = document.getElementById('naver-map-sdk')
    if (existing) { existing.addEventListener('load', resolve); return }
    const script = document.createElement('script')
    script.id = 'naver-map-sdk'
    script.src = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${import.meta.env.VITE_NAVER_MAP_CLIENT_ID}`
    script.onload = resolve
    script.onerror = reject
    document.head.appendChild(script)
  })
}

function shareToComm() {
  if (!activeTrip.value) return
  router.push({ path: '/community', query: { shareTrip: activeTrip.value.id } })
}

async function openMapPanel() {
  if (!activeTrip.value) { toast.show('일정을 먼저 선택하세요'); return }
  showMapPanel.value = true
  mapDay.value = days.value[0] || null
  try {
    await loadNaverMapScript()
  } catch {
    toast.show('지도 초기화에 실패했어요')
  }
}

function closeMapPanel() {
  showMapPanel.value = false
  mapFullscreen.value = false
}

function toggleMapFullscreen() {
  mapFullscreen.value = !mapFullscreen.value
  nextTick(() => {
    if (naverMapInstance) {
      naver.maps.Event.trigger(naverMapInstance, 'resize')
    }
  })
}

function onPanelResizeStart(e) {
  panelResizing = true
  panelResizeStartX = e.clientX
  panelResizeStartWidth = mapPanelWidth.value
  document.addEventListener('mousemove', onPanelResizeMove)
  document.addEventListener('mouseup', onPanelResizeEnd)
}

function onPanelResizeMove(e) {
  if (!panelResizing) return
  const delta = panelResizeStartX - e.clientX
  mapPanelWidth.value = Math.max(260, Math.min(1400, panelResizeStartWidth + delta))
}

function onPanelResizeEnd() {
  if (!panelResizing) return
  panelResizing = false
  document.removeEventListener('mousemove', onPanelResizeMove)
  document.removeEventListener('mouseup', onPanelResizeEnd)
  nextTick(() => {
    if (naverMapInstance) naver.maps.Event.trigger(naverMapInstance, 'resize')
  })
}

async function onMapPanelEntered() {
  try {
    await initNaverMap()
  } catch {
    toast.show('지도 초기화에 실패했어요')
  }
}

async function initNaverMap() {
  if (!mapEl.value) return
  const { naver } = window
  mapEl.value.innerHTML = ''
  naverMapInstance = new naver.maps.Map(mapEl.value, {
    zoom: 12,
    center: new naver.maps.LatLng(37.5665, 126.9780),
    mapDataControl: false,
  })
  naver.maps.Event.addListener(naverMapInstance, 'click', () => {
    if (polylineClicked) { polylineClicked = false; return }
    if (pinnedRouteKey) unpinRoute(pinnedRouteKey)
  })
  await drawDayRoute()
}

async function selectMapDay(day) {
  mapDay.value = day
  await drawDayRoute()
}

const ROUTE_STYLES = {
  DRIVING:       { color: '#e06343', weight: 5, opacity: 0.85, strokeStyle: 'solid' },
  WALKING:       { color: '#16a34a', weight: 3, opacity: 0.8,  strokeStyle: 'shortdot' },
  BUS:           { color: '#2563eb', weight: 5, opacity: 0.85, strokeStyle: 'solid' },
  SUBWAY:        { color: '#7c3aed', weight: 5, opacity: 0.85, strokeStyle: 'solid' },
  RAIL:          { color: '#dc2626', weight: 5, opacity: 0.85, strokeStyle: 'solid' },
  EXPRESSBUS:    { color: '#d97706', weight: 5, opacity: 0.85, strokeStyle: 'solid' },
  INTERCITYBUS:  { color: '#d97706', weight: 5, opacity: 0.85, strokeStyle: 'solid' },
  PUBLIC_TRANSIT:{ color: '#534ab7', weight: 5, opacity: 0.85, strokeStyle: 'solid' },
}

function getRouteStyle(transitMode) {
  const first = transitMode?.split(',')[0]
  return ROUTE_STYLES[first] || ROUTE_STYLES.PUBLIC_TRANSIT
}

async function drawDayRoute() {
  if (!naverMapInstance || !mapDay.value) return
  const gen = ++drawGeneration          // 이 호출의 고유 세대 번호
  const { naver } = window
  routePolylines.forEach(p => p.setMap(null))
  routeMarkers.forEach(m => m.setMap(null))
  routePolylines = []
  routeMarkers = []
  routeMarkerGroups = new Map()
  pinnedRouteKey = null

  const day = mapDay.value
  const sorted = [...day.events].sort((a, b) => a.top - b.top)
  if (!sorted.length) return

  const bounds = new naver.maps.LatLngBounds()
  let hasCoords = false

  for (let i = 0; i < sorted.length; i++) {
    const ev = sorted[i]
    const cand = candidates.value.find(c => c.id === ev.candidateId)
    const lat = cand?.latitude ? parseFloat(cand.latitude) : 0
    const lng = cand?.longitude ? parseFloat(cand.longitude) : 0
    if (!lat || !lng) continue
    const pos = new naver.maps.LatLng(lat, lng)
    bounds.extend(pos)
    hasCoords = true
    const marker = new naver.maps.Marker({
      position: pos,
      map: naverMapInstance,
      title: ev.name,
      icon: {
        content: `<div class="map-route-marker">${i + 1}. ${ev.name}</div>`,
        anchor: new naver.maps.Point(0, 20),
      },
    })
    routeMarkers.push(marker)
  }

  for (let i = 1; i < sorted.length; i++) {
    const prev = sorted[i - 1]
    const curr = sorted[i]
    if (!curr.transitMode || curr.transitMode === 'NONE') continue
    const prevCand = candidates.value.find(c => c.id === prev.candidateId)
    const currCand = candidates.value.find(c => c.id === curr.candidateId)
    const pillTop = prev.top + prev.height
    const hour = Math.min(Math.floor(pillTop / 60), 23)
    const key = `${prevCand?.attractionId}-${currCand?.attractionId}-${hour}`

    // BUS·SUBWAY 등 ODsay 결과 모드는 PUBLIC_TRANSIT 요청 모드로 매핑
    const PUBLIC_TRANSIT_MODES = new Set(['BUS','SUBWAY','RAIL','EXPRESSBUS','INTERCITYBUS','BUS,SUBWAY'])
    const requestMode = PUBLIC_TRANSIT_MODES.has(curr.transitMode) ? 'PUBLIC_TRANSIT' : curr.transitMode
    let result = pillResults[key]?.[requestMode]
    if (result === undefined && prevCand?.attractionId && currCand?.attractionId) {
      try {
        result = await getTransitByMode(prevCand.attractionId, currCand.attractionId, requestMode, hour)
        if (gen !== drawGeneration) return   // 새 draw가 시작됐으면 중단
        if (!pillResults[key]) pillResults[key] = {}
        pillResults[key][requestMode] = result
      } catch { result = null }
    }

    if (gen !== drawGeneration) return

    const style = getRouteStyle(curr.transitMode)

    if (requestMode === 'PUBLIC_TRANSIT' && prevCand?.attractionId && currCand?.attractionId) {
      // PUBLIC_TRANSIT: lane별 구간 폴리라인 (구간별 hover 강조)
      drawPublicTransitPolylines(prevCand.attractionId, currCand.attractionId, hour, key, gen, style, result)
      const legOrigin = { lat: parseFloat(prevCand.latitude), lng: parseFloat(prevCand.longitude) }
      const legDest = { lat: parseFloat(currCand.latitude), lng: parseFloat(currCand.longitude) }
      drawTransferMarkers(prevCand.attractionId, currCand.attractionId, hour, key, gen, legOrigin, legDest)
    } else if (result?.routeCoords) {
      try {
        const coords = JSON.parse(result.routeCoords)
        const path = coords.map(([lng, lat]) => new naver.maps.LatLng(lat, lng))
        const polyline = new naver.maps.Polyline({
          path, clickable: true,
          strokeColor: style.color, strokeWeight: style.weight,
          strokeOpacity: style.opacity, strokeStyle: style.strokeStyle,
          map: naverMapInstance,
        })
        addPolylineHover(polyline, style, key)
        routePolylines.push(polyline)
      } catch {}
    } else {
      const prevLat = prevCand?.latitude ? parseFloat(prevCand.latitude) : 0
      const prevLng = prevCand?.longitude ? parseFloat(prevCand.longitude) : 0
      const currLat = currCand?.latitude ? parseFloat(currCand.latitude) : 0
      const currLng = currCand?.longitude ? parseFloat(currCand.longitude) : 0
      if (prevLat && prevLng && currLat && currLng) {
        const fallbackStyle = { ...style, weight: 3, opacity: 0.4 }
        const polyline = new naver.maps.Polyline({
          path: [new naver.maps.LatLng(prevLat, prevLng), new naver.maps.LatLng(currLat, currLng)],
          clickable: true,
          strokeColor: fallbackStyle.color, strokeWeight: 3, strokeOpacity: 0.4,
          strokeStyle: 'shortdot', map: naverMapInstance,
        })
        addPolylineHover(polyline, fallbackStyle, key)
        routePolylines.push(polyline)
      }
    }
  }

  if (hasCoords) naverMapInstance.fitBounds(bounds, { top: 50, right: 30, bottom: 30, left: 30 })
}

// lane.class → 색상 매핑 (ODsay: 1=지하철, 2=버스)
const LANE_CLASS_COLOR = { 1: '#7c3aed', 2: '#2563eb' }

async function drawPublicTransitPolylines(fromAttrId, toAttrId, hour, routeKey, gen, fallbackStyle, fallbackResult) {
  const { naver } = window
  try {
    const segments = await getLaneSegments(fromAttrId, toAttrId, hour)
    if (gen !== drawGeneration) return
    if (Array.isArray(segments) && segments.length > 0) {
      segments.forEach((seg, idx) => {
        if (!Array.isArray(seg.p) || seg.p.length < 2) return
        const laneKey = `${routeKey}-${idx}`
        const color = LANE_CLASS_COLOR[seg.c] || fallbackStyle.color
        const segStyle = { color, weight: fallbackStyle.weight, opacity: fallbackStyle.opacity, strokeStyle: 'solid' }
        const path = seg.p.map(([x, y]) => new naver.maps.LatLng(y, x))
        const polyline = new naver.maps.Polyline({
          path, clickable: true,
          strokeColor: color, strokeWeight: segStyle.weight,
          strokeOpacity: segStyle.opacity, strokeStyle: 'solid',
          map: naverMapInstance,
        })
        addPolylineHoverSegment(polyline, segStyle, laneKey)
        routePolylines.push(polyline)
      })
      return
    }
  } catch {}

  // getLaneSegments 실패 시 기존 단일 폴리라인 fallback
  if (gen !== drawGeneration) return
  if (fallbackResult?.routeCoords) {
    try {
      const coords = JSON.parse(fallbackResult.routeCoords)
      const path = coords.map(([lng, lat]) => new naver.maps.LatLng(lat, lng))
      const polyline = new naver.maps.Polyline({
        path, clickable: true,
        strokeColor: fallbackStyle.color, strokeWeight: fallbackStyle.weight,
        strokeOpacity: fallbackStyle.opacity, strokeStyle: fallbackStyle.strokeStyle,
        map: naverMapInstance,
      })
      addPolylineHover(polyline, fallbackStyle, routeKey)
      routePolylines.push(polyline)
    } catch {}
  }
}

function unpinRoute(key) {
  const group = routeMarkerGroups.get(key)
  group?.collapse?.()
  if (group?.style) {
    const targets = group.polylines ?? (group.polyline ? [group.polyline] : [])
    targets.forEach(p => p.setOptions({ strokeWeight: group.style.weight, strokeOpacity: group.style.opacity }))
  }
  pinnedRouteKey = null
}

// 구간별 폴리라인용 hover — 해당 폴리라인만 강조, 마커는 routeKey로 expand/collapse
function addPolylineHoverSegment(polyline, style, routeKey) {
  const { naver } = window
  const highlight = () => polyline.setOptions({ strokeWeight: style.weight + 3, strokeOpacity: 1.0 })
  const restore   = () => polyline.setOptions({ strokeWeight: style.weight,     strokeOpacity: style.opacity })

  naver.maps.Event.addListener(polyline, 'mouseover', () => {
    if (pinnedRouteKey !== routeKey) highlight()
  })
  naver.maps.Event.addListener(polyline, 'mouseout', () => {
    if (pinnedRouteKey !== routeKey) restore()
  })
  naver.maps.Event.addListener(polyline, 'click', () => {
    polylineClicked = true
    if (pinnedRouteKey === routeKey) {
      unpinRoute(routeKey)
    } else {
      if (pinnedRouteKey) unpinRoute(pinnedRouteKey)
      pinnedRouteKey = routeKey
      highlight(); routeMarkerGroups.get(routeKey)?.expand()
    }
  })
  // 그룹에 polylines 배열 추가 (pin 해제 시 모든 lane 복원용)
  const existing = routeMarkerGroups.get(routeKey) || {}
  const polylines = existing.polylines ? [...existing.polylines, polyline] : [polyline]
  routeMarkerGroups.set(routeKey, { ...existing, polylines, style })
}

function addPolylineHover(polyline, style, routeKey) {
  const { naver } = window
  const highlight = () => polyline.setOptions({ strokeWeight: style.weight + 3, strokeOpacity: 1.0 })
  const restore   = () => polyline.setOptions({ strokeWeight: style.weight,     strokeOpacity: style.opacity })

  naver.maps.Event.addListener(polyline, 'mouseover', () => {
    if (pinnedRouteKey !== routeKey) highlight()
  })
  naver.maps.Event.addListener(polyline, 'mouseout', () => {
    if (pinnedRouteKey !== routeKey) restore()
  })
  naver.maps.Event.addListener(polyline, 'click', () => {
    polylineClicked = true
    if (pinnedRouteKey === routeKey) {
      unpinRoute(routeKey)
    } else {
      if (pinnedRouteKey) unpinRoute(pinnedRouteKey)
      pinnedRouteKey = routeKey
      highlight(); routeMarkerGroups.get(routeKey)?.expand()
    }
  })
  // polyline 참조를 그룹에 저장 (나중에 pin 해제 시 사용)
  const existing = routeMarkerGroups.get(routeKey) || {}
  routeMarkerGroups.set(routeKey, { ...existing, polyline, style })
}

async function drawTransferMarkers(fromAttrId, toAttrId, hour, routeKey, gen, legOrigin, legDest) {
  try {
    const detail = await getTransitDetail(fromAttrId, toAttrId, hour)
    if (gen !== drawGeneration) return   // 새 draw가 시작됐으면 마커 추가 중단
    const subPaths = detail?.intercityPaths?.[0]?.subPath || []
    const { naver } = window
    const TYPE_COLOR = { 1: '#7c3aed', 2: '#2563eb', 4: '#dc2626', 5: '#d97706', 6: '#d97706' }
    const nonWalking = subPaths.filter(s => s.trafficType !== 3)

    nonWalking.forEach((sub, idx) => {
      const laneKey = `${routeKey}-${idx}`
      const color = TYPE_COLOR[sub.trafficType] || '#534ab7'
      const label = getTransitSegmentLabel(sub)
      const detailMarkers = []

      // 기본 라벨 마커 (항상 표시 — 탑승 지점에 버스번호/호선명)
      let labelMarker = null
      if (sub.startX && sub.startY) {
        const labelIcon = {
          content: `<div class="map-lane-label" style="border-color:${color};color:${color}">${label}</div>`,
          anchor: new naver.maps.Point(0, 22),
        }
        labelMarker = new naver.maps.Marker({ position: new naver.maps.LatLng(sub.startY, sub.startX), map: naverMapInstance, icon: labelIcon, zIndex: 8 })
        routeMarkers.push(labelMarker)
      }

      // 탑승 상세 마커 (pin 시에만 표시)
      if (sub.startX && sub.startY) {
        const expandedIcon = {
          content: `<div class="map-stop-label map-stop-label--board" style="border-color:${color}">
                      <span style="color:${color}">▲</span><span>${label} · ${sub.startName || '탑승'}</span>
                    </div>`,
          anchor: new naver.maps.Point(0, 22),
        }
        const m = new naver.maps.Marker({ position: new naver.maps.LatLng(sub.startY, sub.startX), map: null, icon: expandedIcon, zIndex: 9 })
        routeMarkers.push(m)
        detailMarkers.push(m)
      }

      // 하차/환승 상세 마커 (마지막 구간 제외, pin 시에만 표시)
      if (idx < nonWalking.length - 1 && sub.endX && sub.endY) {
        const expandedIcon = {
          content: `<div class="map-stop-label map-stop-label--alight" style="border-color:${color};color:${color}">${label} · ${sub.endName || '하차'}</div>`,
          anchor: new naver.maps.Point(0, 22),
        }
        const m = new naver.maps.Marker({ position: new naver.maps.LatLng(sub.endY, sub.endX), map: null, icon: expandedIcon, zIndex: 10 })
        routeMarkers.push(m)
        detailMarkers.push(m)
      }

      // laneKey별로 expand/collapse 등록
      const existing = routeMarkerGroups.get(laneKey) || {}
      routeMarkerGroups.set(laneKey, {
        ...existing,
        expand: () => {
          labelMarker?.setMap(null)
          detailMarkers.forEach(m => m.setMap(naverMapInstance))
        },
        collapse: () => {
          detailMarkers.forEach(m => m.setMap(null))
          labelMarker?.setMap(naverMapInstance)
        },
      })
    })

    // 도보 구간 폴리라인 (TMAP 실제 경로, 점선)
    // ODsay 도보 subPath는 좌표(startX/Y, endX/Y)가 0인 경우가 많아, 인접 비-도보 구간의
    // 끝/시작점과 여행지 원점(legOrigin/legDest)에서 도보 양 끝점을 유도한다.
    const path0 = detail?.intercityPaths?.[0] || {}
    const firstStation = firstStationCoord(subPaths)
    const lastStation = lastStationCoord(subPaths)
    const localFromSub = Array.isArray(path0.localFrom?.subPath) ? path0.localFrom.subPath : []
    const localToSub = Array.isArray(path0.localTo?.subPath) ? path0.localTo.subPath : []

    // 도시간 경로: 출발·도착 도보는 localFrom/localTo에 분리되어 있으므로
    // 메인 subPath의 양 끝 기준점을 첫/마지막 역으로 잡는다.
    const mainOrigin = localFromSub.length ? (firstStation || legOrigin) : legOrigin
    const mainDest = localToSub.length ? (lastStation || legDest) : legDest

    let walkPairs = deriveWalkingPairs(subPaths, mainOrigin, mainDest)
    if (localFromSub.length) walkPairs = walkPairs.concat(deriveWalkingPairs(localFromSub, legOrigin, firstStation || legOrigin))
    if (localToSub.length) walkPairs = walkPairs.concat(deriveWalkingPairs(localToSub, lastStation || legDest, legDest))

    const walkingStyle = { color: '#16a34a', weight: 5, opacity: 0.9, strokeStyle: 'shortdash' }
    await Promise.all(walkPairs.map(async ([from, to]) => {
      if (!from?.lat || !from?.lng || !to?.lat || !to?.lng) return
      try {
        const coords = await getWalkingCoords(from.lat, from.lng, to.lat, to.lng)
        if (gen !== drawGeneration) return
        if (!Array.isArray(coords) || coords.length < 2) return
        const path = coords.map(([lng, lat]) => new naver.maps.LatLng(lat, lng))
        const polyline = new naver.maps.Polyline({
          path, clickable: false,
          strokeColor: walkingStyle.color, strokeWeight: walkingStyle.weight,
          strokeOpacity: walkingStyle.opacity, strokeStyle: walkingStyle.strokeStyle,
          map: naverMapInstance,
        })
        routePolylines.push(polyline)
      } catch {}
    }))
  } catch {}
}

// 비-도보 구간의 시작/끝 좌표 (0이면 null)
function segStartCoord(s) { return (s.startX && s.startY) ? { lat: s.startY, lng: s.startX } : null }
function segEndCoord(s) { return (s.endX && s.endY) ? { lat: s.endY, lng: s.endX } : null }

function firstStationCoord(subPaths) {
  for (const s of subPaths) if (s.trafficType !== 3) return segStartCoord(s)
  return null
}
function lastStationCoord(subPaths) {
  for (let i = subPaths.length - 1; i >= 0; i--) if (subPaths[i].trafficType !== 3) return segEndCoord(subPaths[i])
  return null
}

// subPath 배열에서 도보 구간의 [출발, 도착] 좌표쌍을 인접 비-도보 구간/원점에서 유도
function deriveWalkingPairs(subPaths, origin, dest) {
  const pairs = []
  for (let i = 0; i < subPaths.length; i++) {
    if (subPaths[i].trafficType !== 3) continue
    let from = null
    for (let j = i - 1; j >= 0; j--) {
      if (subPaths[j].trafficType !== 3) { from = segEndCoord(subPaths[j]); break }
    }
    if (!from) from = origin
    let to = null
    for (let j = i + 1; j < subPaths.length; j++) {
      if (subPaths[j].trafficType !== 3) { to = segStartCoord(subPaths[j]); break }
    }
    if (!to) to = dest
    if (from && to) pairs.push([from, to])
  }
  return pairs
}

function getTransitSegmentLabel(sub) {
  const lane = sub.lane?.[0] || {}
  switch (sub.trafficType) {
    case 1: { // 지하철 — "2호선", "경의중앙선" 등
      const name = lane.name || ''
      // "수도권 2호선" → "2호선", 앞 지역명 제거
      return name.replace(/^수도권\s*/, '').replace(/^[가-힣]+\s(?=[가-힣]+선)/, '') || `${lane.subwayCode}호선`
    }
    case 2: { const no = lane.busNo || lane.busNoGov || ''; return no ? `${no}번` : '버스' }
    case 4: return lane.name || 'KTX'
    case 5:
    case 6: return lane.busNo ? `${lane.busNo}번` : '고속버스'
    default: return '환승'
  }
}

// ── 일정 로드 ──
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
    if (showMapPanel.value && mapDay.value) {
      const updated = days.value.find(d => d.isoDate === mapDay.value.isoDate)
      if (updated) mapDay.value = updated
      if (naverMapInstance) await drawDayRoute()
    }
  } catch (err) {
    if (err?.status === 403 || err?.response?.status === 403) {
      collaboratingTrips.value = collaboratingTrips.value.filter(t => t.id !== activeTripId.value)
      const next = trips.value[0] ?? collaboratingTrips.value[0]
      activeTripId.value = next?.id ?? null
      if (activeTripId.value) await loadTrip()
    } else {
      toast.show('일정 로드 실패')
    }
  }
}

// ── 드래그 앤 드롭 ──
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

function onDocumentClick(e) {
  openPillKey.value = null
  currentPillData.value = null
  // 협업자 패널 바깥 클릭 시 닫기
  if (collabPanelOpen.value) {
    const overlay = document.querySelector('.collab-panel-overlay')
    const collabBtn = document.querySelector('.btn-collab')
    if (overlay && !overlay.contains(e.target) && !collabBtn?.contains(e.target)) {
      collabPanelOpen.value = false
    }
  }
}

onUnmounted(() => {
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', onResizeEnd)
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('mousemove', onPanelResizeMove)
  document.removeEventListener('mouseup', onPanelResizeEnd)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  routePolylines.forEach(p => p.setMap(null))
  routeMarkers.forEach(m => m.setMap(null))
  collab.disconnect()
})

// ── 실시간 협업 이벤트 핸들러 ──────────────────────────────────────────────
function handleTripEvent(event) {
  const myId = auth.user?.id
  // echo 방지: 내가 보낸 이벤트는 서버에서 이미 반영했으므로 무시
  if (event.actorId != null && event.actorId === myId) return

  switch (event.type) {
    case 'BLOCK_ADDED':
    case 'BLOCK_MOVED':
    case 'BLOCK_DELETED':
    case 'CANDIDATE_ADDED':
    case 'CANDIDATE_REMOVED':
      loadTrip()
      break
    case 'TRANSIT_RECALCULATED':
      applyTransitUpdate(event.payload)
      break
  }
}

function applyTransitUpdate(payload) {
  if (!payload?.blocks) return
  const blockMap = {}
  payload.blocks.forEach(b => { blockMap[b.blockId] = b })

  days.value.forEach(day => {
    day.events?.forEach(ev => {
      const update = blockMap[ev.id]
      if (update) {
        ev.transitDurationMinutes = update.transitDurationMinutes
        ev.transitMode = update.transitMode
      }
    })
  })
}

function connectCollab(tripId) {
  collab.setHandlers({ tripEvent: handleTripEvent, presence: null, reconnect: loadTrip })
  collab.connect(tripId)
}

// 일정 전환 시 WebSocket 재연결
watch(activeTripId, (newId, oldId) => {
  collabPanelOpen.value = false
  if (oldId != null) collab.disconnect()
  if (newId != null) connectCollab(newId)
})

function onVisibilityChange() {
  if (document.visibilityState === 'visible' && activeTripId.value != null) {
    loadTrip()
  }
}

onMounted(async () => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('visibilitychange', onVisibilityChange)
  tripsLoading.value = true
  try {
    const [tripsResult, collabResult] = await Promise.allSettled([
      tripApi.list(),
      tripApi.listCollaborating(),
    ])
    trips.value = tripsResult.status === 'fulfilled' ? tripsResult.value : []
    collaboratingTrips.value = collabResult.status === 'fulfilled' ? collabResult.value : []
    const first = trips.value[0] ?? collaboratingTrips.value[0]
    if (first) {
      activeTripId.value = first.id
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

<style scoped>
.btn-collab {
  padding: 6px 14px;
  border: 1px solid var(--gray-border);
  border-radius: var(--radius-full);
  background: none;
  font-size: 12px;
  color: var(--gray-dark);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  transition: color .12s, background .12s, border-color .12s;
  font-family: inherit;
}
.btn-collab:hover {
  color: var(--purple-900);
  background: var(--purple-50);
  border-color: var(--purple-100);
}
.btn-collab.active {
  background: var(--purple-50);
  border-color: var(--purple-900);
  color: var(--purple-900);
  font-weight: 600;
}

.collab-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #d1d5db;
  flex-shrink: 0;
  transition: background 0.3s;
}
.collab-status-dot.connected { background: #22c55e; }

.collab-panel-overlay {
  position: absolute;
  top: 48px; /* 툴바 높이 */
  right: 16px;
  z-index: 200;
  width: 320px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.collab-slide-enter-active,
.collab-slide-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}
.collab-slide-enter-from,
.collab-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
