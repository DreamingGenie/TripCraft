<template>
  <main :id="embedded ? null : 'main'" :class="embedded ? 'board-embedded' : ''">
  <section id="screen-schedule">

    <!-- TOOLBAR (standalone /schedule 전용) -->
    <div v-if="!embedded" class="schedule-toolbar">
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

      <div v-if="otherParticipants.length" class="toolbar-collab-avatars">
        <div v-for="p in otherParticipants" :key="p.memberId"
             class="toolbar-avatar-wrap"
             :title="p.nickname"
             :style="{ '--avatar-border': collab.colorMap[p.memberId] }">
          <img v-if="collaboratorImageMap[p.memberId]"
               :src="collaboratorImageMap[p.memberId]" class="toolbar-avatar-img" />
          <span v-else class="toolbar-avatar-initial">{{ p.nickname?.charAt(0)?.toUpperCase() }}</span>
        </div>
      </div>

      <button class="btn-map-view" @click="openMapPanel()">🗺 지도로 보기</button>
      <button v-if="activeTrip" class="btn-share-trip" @click="shareToComm">📢 공유하기</button>
      <button v-if="activeTrip" ref="collabBtnRef" class="btn-collab" :class="{ active: collabPanelOpen }"
              @click="collabPanelOpen = !collabPanelOpen">
        <span class="collab-status-dot" :class="{ connected: collab.connected }"></span>
        👥 협업자
      </button>
      <button class="btn-new-trip" @click="openScheduleModal()">+ 새 일정</button>
    </div>

    <!-- 협업자 패널 (툴바 아래 슬라이드) -->
    <Transition name="collab-slide">
      <div v-if="collabPanelOpen && activeTrip" ref="collabPanelRef" class="collab-panel-overlay">
        <CollaboratorPanel
          :trip-id="activeTrip.id"
          :is-owner="activeTripIsOwner"
          :owner-label="activeTripOwnerLabel"
          :participants="collab.participants"
          :color-map="collab.colorMap"
          @close="collabPanelOpen = false"
        />
      </div>
    </Transition>

    <!-- BODY: 사이드바 + 시간표 + 지도 패널 -->
    <div class="schedule-body" @mousemove="onPointerMove">
      <!-- 후보군 사이드바 -->
      <aside class="candidate-sidebar"
             :class="{ collapsed: !sidebarOpen, 'drop-delete-zone': sidebarDropActive }"
             @dragover.prevent="onSidebarDragOver"
             @dragleave="sidebarDragOver = false"
             @drop="onDropSidebar">
        <div v-if="sidebarDragOver" class="sidebar-delete-hint">여기에 놓으면 삭제</div>

        <template v-if="!sidebarDragOver">
          <div class="cand-sidebar-header">
            <span class="cand-sidebar-title">{{ embedded ? '보관함' : '후보군' }}</span>
            <span v-if="candidates.length" class="cand-sidebar-count">{{ candidates.length }}</span>
            <!-- embedded(organize) 전용: 보관함 접기 -->
            <button v-if="embedded" class="cand-collapse-btn" @click="sidebarOpen = false" title="보관함 접기" aria-label="보관함 접기">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M10 3 5 8l5 5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </button>
          </div>

          <!-- embedded(organize): 플랫 리스트 + 분류색 (§5-5). 같은 장소 여러 번 배치 가능 -->
          <div v-if="embedded" class="cand-sidebar-body cand-flat-body">
            <div v-if="!activeTrip" class="cand-empty">
              {{ tripsLoading ? '로딩 중...' : '일정을 선택하세요' }}
            </div>
            <template v-else>
              <div v-if="!candidates.length" class="cand-flat-empty">
                <p>보관함이 비어 있어요.</p>
                <span>탐색 모드에서 가고 싶은 곳을 담아오세요.</span>
              </div>
              <div v-for="c in candidates" :key="c.id"
                   class="cand-flat-card" :class="{ 'cand-flat-card--placed': blockCount(c) > 0 }"
                   :style="{ '--cat-ink': catInk(c.category) }"
                   :draggable="!readOnly"
                   @dragstart="onCandDragStart($event, c)"
                   @dragend="onDragEnd">
                <span class="cand-flat-bar"></span>
                <div class="cand-flat-info">
                  <div class="cand-flat-name">{{ c.attractionName }}</div>
                  <div class="cand-flat-meta">
                    <span class="cand-flat-cat">{{ c.category || '장소' }}</span>
                    <span v-if="c.sigunguName" class="cand-flat-dot">·</span>
                    <span v-if="c.sigunguName">{{ c.sigunguName }}</span>
                  </div>
                </div>
                <!-- 배치 여부는 카드 음영으로 표시(배치됨 카운트는 과해서 제거) -->
                <button class="cand-flat-add" :title="blockCount(c) > 0 ? '또 배치하기' : '일정에 배치'"
                        @click.stop="placeCandidateQuick(c)">
                  <svg width="13" height="13" viewBox="0 0 14 14" fill="none">
                    <path d="M7 2.5v9M2.5 7h9" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                  </svg>
                  배치
                </button>
              </div>
            </template>
          </div>

          <!-- standalone(/schedule): 기존 3단 트리 유지 -->
          <div v-else class="cand-sidebar-body">
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
                                       :draggable="!readOnly"
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
                                   :draggable="!readOnly"
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
            <button v-if="embedded && !readOnly" class="btn-add-from-explore" @click="$emit('explore')">
              + 탐색에서 더 담기
            </button>
            <button v-if="embedded && !readOnly" class="btn-add-place" @click="addPlaceOpen = true">
              + 내 장소 · 직접 추가
            </button>
            <RouterLink v-else to="/explore" class="btn-add-from-explore">
              + 관광지 탐색에서 추가하기
            </RouterLink>
          </div>
        </template>
      </aside>

      <!-- 시간표 -->
      <div class="timetable-main">
        <!-- 접힌 보관함 펼치기 (embedded 전용) -->
        <button v-if="embedded && !sidebarOpen" class="cand-expand-btn" @click="sidebarOpen = true" title="보관함 펼치기">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M6 3l5 5-5 5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
          보관함
        </button>
        <!-- standalone: 드래그 힌트 -->
        <div v-if="!embedded" class="hint-bar">✋ 왼쪽 후보군 카드를 원하는 날짜·시간대로 드래그해서 놓으세요</div>

        <div class="timetable-wrapper" ref="wrapperEl" @scroll="e => { openPillKey = null; timetableScrollTop = e.currentTarget.scrollTop }">
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
                     :class="{
                       'event-dragging': ev.dragging,
                       'event-processing': isProcessing && ev.id === processingEvId,
                       'event-block--cat': embedded,
                       'grabbed-by-other': collab.isGrabbedByOther(ev.id, myMemberId),
                     }"
                     :draggable="!readOnly"
                     :style="{ top: ev.top + 'px', height: ev.height + 'px', '--cat-ink': catInk(ev.category), '--grabber-color': grabberColor(ev.id) }"
                     @dragstart="onEventDragStart($event, ev, d)"
                     @dragend="onDragEnd">
                  <span class="event-name">{{ ev.name }}</span>
                  <span class="event-time">{{ ev.timeLabel }}</span>
                  <span v-if="isProcessing && ev.id === processingEvId" class="event-spinner"></span>
                  <button v-if="!readOnly" class="event-del" :title="embedded ? '보관함으로 빼기' : '삭제'"
                          @click.stop="removeEvent(d, ev)">✕</button>
                  <div v-if="!readOnly" class="resize-handle" @mousedown.stop="onResizeStart($event, ev)"></div>
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
                    :aria-label="mapFullscreen ? '지도 화면 복원' : '지도 전체화면'"
                    @click="toggleMapFullscreen">{{ mapFullscreen ? '⊡' : '⊞' }}</button>
            <button class="map-panel-close" @click="closeMapPanel" aria-label="지도 패널 닫기">✕</button>
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

  <!-- 협업자 커서 + 유령 블록 오버레이 -->
  <Teleport to="body">
    <template v-for="p in otherParticipants" :key="p.memberId">
      <div v-if="p.interaction !== 'grab' && p.zone && p.zone !== 'other'"
           class="collab-cursor"
           :style="{ ...cursorStyle(p), '--cursor-color': collab.colorMap[p.memberId] }">
        <svg class="cursor-icon" viewBox="0 0 16 20" width="16" height="20" xmlns="http://www.w3.org/2000/svg">
          <path d="M0 0 L0 16 L4 12 L7 18 L9 17 L6 11 L11 11 Z"
                fill="var(--cursor-color)" stroke="white" stroke-width="1.2"/>
        </svg>
        <div class="cursor-avatar" :style="{ borderColor: collab.colorMap[p.memberId] }">
          <img v-if="collaboratorImageMap[p.memberId]" :src="collaboratorImageMap[p.memberId]" />
          <span v-else>{{ p.nickname?.charAt(0)?.toUpperCase() }}</span>
        </div>
      </div>
      <div v-if="p.interaction === 'grab' && p.targetBlockId && p.zone === 'timetable'"
           class="collab-ghost-block"
           :data-color="ghostBlockColor(p.targetBlockId)"
           :style="{ ...ghostStyle(p), '--cursor-color': collab.colorMap[p.memberId] }">
        <span class="ghost-label">{{ p.nickname }}</span>
        <span class="ghost-name">{{ ghostBlockName(p.targetBlockId) }}</span>
      </div>
      <div v-if="p.interaction === 'grab' && p.zone === 'timetable' && p.dayIndex >= 0"
           class="collab-drop-preview"
           :style="dropPreviewStyle(p)">
        {{ ghostBlockName(p.targetBlockId) }}
      </div>
    </template>
  </Teleport>

  <!-- 장소 추가(내 장소·직접 추가) -->
  <AddPlaceModal v-if="addPlaceOpen && activeTripId" :trip-id="activeTripId"
                 @close="addPlaceOpen = false" @added="onPlaceAdded" />

  </main>
</template>

<script setup>
import { ref, computed, reactive, watch, onMounted, onUnmounted, nextTick, inject } from 'vue'
import { useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { useActiveTripStore } from '@/stores/activeTrip'
import { useAuthStore } from '@/stores/auth'
import { useCollabStore } from '@/stores/collab'
import { tripApi } from '@/api/trip'
import { getTransitByMode, getTransitDetail, selectTransitPath, getDrivingOption, applyDrivingOption, getLaneSegments, getWalkingCoords } from '@/api/transit'
import CollaboratorPanel from '@/components/CollaboratorPanel.vue'
import AddPlaceModal from '@/components/AddPlaceModal.vue'
import { useCollabCursor } from '@/composables/useCollabCursor'

// embedded=true 이면 PlanView organize 모드 안에서 동작(자체 툴바·트레이 select 숨김,
// 현재 일정은 tripId prop 으로 제어). false 이면 /schedule 단독 화면.
const props = defineProps({
  embedded: { type: Boolean, default: false },
  tripId: { type: [Number, null], default: null },
  // 공유 링크 read-only: shareToken 으로 비로그인 조회, readOnly 면 편집·collab 비활성
  readOnly: { type: Boolean, default: false },
  shareToken: { type: [String, null], default: null },
})
const emit = defineEmits(['explore', 'open-map', 'loaded'])

const toast = useToastStore()
const activeTripStore = useActiveTripStore()
const auth = useAuthStore()
const collab = useCollabStore()
const router = useRouter()
const openScheduleModal = inject('openScheduleModal', () => {})
const wrapperEl = ref(null)
const mapEl = ref(null)

const HOUR_PX = 60
const SNAP = 30

// ── UI state ──
const sidebarOpen = ref(true)
const addPlaceOpen = ref(false)
function onPlaceAdded() { loadTrip() }

// ── 분류 6색 ink (전 화면 공통 신호) ──
const CAT_INK = {
  '관광지': 'var(--cat-sights-ink)', '문화시설': 'var(--cat-culture-ink)',
  '레포츠': 'var(--cat-leisure-ink)', '숙박': 'var(--cat-stay-ink)',
  '쇼핑': 'var(--cat-shop-ink)', '음식점': 'var(--cat-food-ink)',
}
function catInk(cat) { return CAT_INK[cat] || 'var(--purple-900)' }

// 보관함: 한 후보가 격자에 배치된 블록 개수 (같은 장소 여러 번 배치 허용 → "배치됨 ×N")
function blockCount(c) { return c.blocks?.length ?? 0 }

const trips = ref([])
const collaboratingTrips = ref([])
const tripsLoading = ref(false)
const activeTripId = ref(null)
const activeTrip = ref(null)
const candidates = ref([])
const days = ref([])

const collabPanelOpen = ref(false)
const collabPanelRef = ref(null)
const collabBtnRef = ref(null)
const timetableScrollTop = ref(0)
const collaboratorImageMap = ref({})

const myMemberId = computed(() => auth.user?.id)
const otherParticipants = computed(() =>
  collab.participants.filter(p => p.memberId !== myMemberId.value)
)

// 협업 커서 좌표 송수신 (zone 기반 의미 좌표). ghostBlockHeight는 함수 선언이라 호이스팅됨.
const { buildPointerPayload, cursorStyle, ghostStyle, dropPreviewStyle } = useCollabCursor({
  wrapperEl, mapEl, timetableScrollTop, ghostBlockHeight, HOUR_PX, SNAP,
})
const activeTripIsOwner = computed(() => activeTrip.value?.myRole === 'OWNER')
const activeTripOwnerLabel = computed(() => activeTrip.value?.ownerNickname ?? '소유자')

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
    category: cand?.category || '',
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

// ── §5 클릭 배치 (드래그 못 해도 100% 가능) ──
// 첫 Day 의 마지막 블록 종료 + 이동 여유(30분) 뒤에 자동으로 꽂는다(수정 가능).
async function placeCandidateQuick(candidate) {
  if (!days.value.length) { toast.show('먼저 일정 날짜를 설정하세요'); return }
  // 빈 시간이 있는 첫 Day 선택(Day1부터). 차면 다음 Day로 넘어가 23시 겹침 방지.
  let day = null, top = 0
  for (const d of days.value) {
    const bottom = d.events.reduce((max, ev) => Math.max(max, ev.top + ev.height), 9 * 60)
    const t = Math.round(bottom / SNAP) * SNAP
    if (t + SNAP <= 24 * 60) { day = d; top = t; break }
  }
  if (!day) { toast.show('빈 시간이 없어요. 블록을 옮기거나 줄여보세요'); return }
  dragState = { type: 'candidate', data: candidate }
  await dropCandidate(day, top, topToTime(top))
  dragState = null
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
  activeTripStore.set(activeTripId.value)
  try {
    // 공유 토큰이 있으면 비로그인 조회 엔드포인트로 로드
    const trip = props.shareToken
      ? await tripApi.getShared(props.shareToken)
      : await tripApi.get(activeTripId.value)
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

async function loadCollaboratorImages() {
  if (!activeTripId.value) return
  try {
    const list = await tripApi.getCollaborators(activeTripId.value)
    const map = {}
    list.forEach(c => { map[c.memberId] = c.profileImageUrl ?? null })
    collaboratorImageMap.value = map
  } catch {}
}

// ── 드래그 앤 드롭 ──
function onCandDragStart(e, candidate) {
  if (props.readOnly) { e.preventDefault(); return }
  if (isProcessing.value) { e.preventDefault(); return }
  dragState = { type: 'candidate', data: candidate, grabOffsetY: 0, grabRatioX: 0, grabOffsetMin: 0 }
  candidate.dragging = true
  e.dataTransfer.effectAllowed = 'move'
}

function onEventDragStart(e, ev, day) {
  if (props.readOnly) { e.preventDefault(); return }
  if (isProcessing.value || resizeState) { e.preventDefault(); return }
  const blockRect = e.currentTarget.getBoundingClientRect()
  const grabOffsetY = e.clientY - blockRect.top
  const grabOffsetX = e.offsetX
  // 블록 폭·높이 대비 정규화 — 수신측 창 폭이 달라도 잡은 지점 비율을 유지
  const grabRatioX = blockRect.width > 0 ? grabOffsetX / blockRect.width : 0
  const grabOffsetMin = grabOffsetY / (HOUR_PX / 60)
  dragState = { type: 'event', data: ev, fromDay: day, grabOffsetY, grabOffsetX, grabRatioX, grabOffsetMin }
  ev.dragging = true
  e.dataTransfer.effectAllowed = 'move'
  const el = e.currentTarget
  const clone = el.cloneNode(true)
  const cs = getComputedStyle(el)
  clone.classList.remove('event-dragging')
  clone.style.position = 'fixed'
  clone.style.top = '-9999px'
  clone.style.left = '-9999px'
  clone.style.opacity = '1'
  clone.style.pointerEvents = 'none'
  clone.style.width = el.offsetWidth + 'px'
  clone.style.height = el.offsetHeight + 'px'
  clone.style.transform = 'none'
  clone.style.backgroundColor = cs.backgroundColor
  clone.style.borderLeftColor = cs.borderLeftColor
  document.body.appendChild(clone)
  e.dataTransfer.setDragImage(clone, grabOffsetX, grabOffsetY)
  requestAnimationFrame(() => document.body.removeChild(clone))
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
  if (activeTripId.value) {
    // grab 해제 — zone:'other'로 보내 ghost/잠금을 즉시 내림
    collab.sendPointer(activeTripId.value, {
      zone: 'other', interaction: '', targetBlockId: null,
      nickname: auth.user?.nickname ?? '',
    })
  }
}

function onDragOver(e, day) {
  if (!dragState) return
  day.dragOver = true
  const relY = e.clientY - e.currentTarget.getBoundingClientRect().top - (dragState.grabOffsetY ?? 0)
  const height = dragState.type === 'event' ? dragState.data.height : 60
  dragPreview.value = { top: Math.round(Math.max(0, relY) / SNAP) * SNAP, height }
  if (dragState.type === 'event' && activeTripId.value) {
    collab.sendPointer(activeTripId.value, buildPointerPayload(e, {
      interaction: 'grab', dragState, nickname: auth.user?.nickname ?? '',
    }))
  }
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

function onSidebarDragOver(e) {
  sidebarDragOver.value = dragState?.type === 'event'
  if (dragState?.type === 'event' && activeTripId.value) {
    // 사이드바는 'other' zone — grab 상태만 전파(잠금 유지), ghost는 timetable 복귀 시 다시 표시
    collab.sendPointer(activeTripId.value, buildPointerPayload(e, {
      interaction: 'grab', dragState, nickname: auth.user?.nickname ?? '',
    }))
  }
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

// ── 협업자 커서 전송 ──
let pointerThrottle = null
function onPointerMove(e) {
  if (!activeTripId.value) return
  if (pointerThrottle) return
  pointerThrottle = setTimeout(() => { pointerThrottle = null }, 50)
  collab.sendPointer(activeTripId.value, buildPointerPayload(e, {
    interaction: '', nickname: auth.user?.nickname ?? '',
  }))
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible' && activeTripId.value != null) {
    loadTrip()
  }
}

// ── 유령 블록 헬퍼 ──
function ghostBlockHeight(blockId) {
  for (const d of days.value) {
    const ev = d.events.find(e => e.id === blockId)
    if (ev) return ev.height
  }
  return 60
}
function ghostBlockName(blockId) {
  for (const d of days.value) {
    const ev = d.events.find(e => e.id === blockId)
    if (ev) return ev.name
  }
  return ''
}
function ghostBlockColor(blockId) {
  for (const d of days.value) {
    const ev = d.events.find(e => e.id === blockId)
    if (ev) return ev.color ?? 'blue'
  }
  return 'blue'
}
function grabberColor(blockId) {
  const grabberId = collab.grabMap[blockId]
  return grabberId ? collab.colorMap[grabberId] : undefined
}

// ── 실시간 협업 이벤트 핸들러 ──
function handleTripEvent(event) {
  const myId = auth.user?.id
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

let prevGrabbers = new Set()

function connectCollab(tripId) {
  prevGrabbers = new Set()
  const observer = !auth.user?.id   // 비로그인 = 관전 모드(구독만 수신, 전송 X)
  if (!observer) loadCollaboratorImages()  // 협업자 명단(인증 필요)은 로그인 시에만 조회
  collab.setHandlers({
    tripEvent: handleTripEvent,
    presence: (list) => {
      collab.assignColors(list, myMemberId.value)
      const currentGrabbers = new Set(
        list
          .filter(p => p.interaction === 'grab' && p.memberId !== myMemberId.value)
          .map(p => p.memberId)
      )
      const dropped = [...prevGrabbers].some(id => !currentGrabbers.has(id))
      if (dropped) loadTrip()
      prevGrabbers = currentGrabbers
    },
    reconnect: loadTrip,
  })
  collab.connect(tripId, { observer })
}

function onDocumentClick(e) {
  openPillKey.value = null
  currentPillData.value = null
  if (collabPanelOpen.value) {
    const overlay = collabPanelRef.value
    if (overlay && !overlay.contains(e.target) && !collabBtnRef.value?.contains(e.target)) {
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

// 일정 전환 시 WebSocket 재연결 (standalone 모드)
watch(activeTripId, (newId, oldId) => {
  if (props.embedded) return
  collabPanelOpen.value = false
  if (oldId != null) collab.disconnect()
  if (newId != null) connectCollab(newId)
})

// embedded 모드: 현재 일정은 PlanView 의 tripId prop 으로 제어
watch(() => props.tripId, async (id, oldId) => {
  if (!props.embedded) return
  if (id == null) { activeTripId.value = null; activeTrip.value = null; candidates.value = []; days.value = []; return }
  if (id === activeTripId.value) return
  if (oldId != null) collab.disconnect()
  activeTripId.value = id
  await loadTrip()
  // 로그인=실시간 협업, 비로그인 공유링크=관전(observer) 연결. 둘 다 실시간 편집상태 수신
  if (auth.user?.id || props.shareToken) connectCollab(id)
})

// PlanView 헤더의 "지도" 토글이 보드 지도 패널을 제어할 수 있도록 노출
defineExpose({ openMapPanel, closeMapPanel, toggleMap: () => (showMapPanel.value ? closeMapPanel() : openMapPanel()) })

onMounted(async () => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('visibilitychange', onVisibilityChange)

  if (props.embedded) {
    // organize 모드: tripId prop 으로 일정 로드 (자체 trips 목록 불필요)
    if (props.tripId != null) {
      activeTripId.value = props.tripId
      await loadTrip()
      // 로그인=실시간 협업, 비로그인 공유링크=관전(observer) 연결. 둘 다 실시간 편집상태 수신
      if (auth.user?.id || props.shareToken) connectCollab(props.tripId)
    }
    if (wrapperEl.value) wrapperEl.value.scrollTop = 8 * HOUR_PX
    return
  }

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
      const preferred = activeTripStore.id
      const allTrips = [...trips.value, ...collaboratingTrips.value]
      const exists = preferred != null && allTrips.some(t => t.id === preferred)
      activeTripId.value = exists ? preferred : first.id
      activeTripStore.set(activeTripId.value)
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
/* ── 협업자 버튼 ── */
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
  width: 7px; height: 7px; border-radius: 50%;
  background: #d1d5db; flex-shrink: 0;
  transition: background 0.3s;
}
.collab-status-dot.connected { background: #22c55e; }

.collab-panel-overlay {
  position: absolute;
  top: 48px; right: 16px;
  z-index: 200; width: 320px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0,0,0,.12);
}

.collab-slide-enter-active,
.collab-slide-leave-active { transition: opacity 0.2s, transform 0.2s; }
.collab-slide-enter-from,
.collab-slide-leave-to { opacity: 0; transform: translateY(-8px); }

/* ── 툴바 협업자 아바타 ── */
.toolbar-collab-avatars { display: flex; align-items: center; margin-right: 8px; }
.toolbar-avatar-wrap {
  width: 28px; height: 28px; border-radius: 50%; overflow: hidden;
  border: 2px solid var(--avatar-border, #ccc);
  display: flex; align-items: center; justify-content: center;
  background: #e5e7eb; margin-left: -6px; cursor: default;
}
.toolbar-avatar-wrap:first-child { margin-left: 0; }
.toolbar-avatar-img { width: 100%; height: 100%; object-fit: cover; }
.toolbar-avatar-initial { font-size: 11px; font-weight: 700; color: #fff; }

/* ── 협업자 커서 오버레이 ── */
.collab-cursor {
  position: fixed; pointer-events: none; z-index: 9999;
  display: flex; align-items: flex-start; gap: 4px;
}
.cursor-icon { flex-shrink: 0; }
.cursor-avatar {
  width: 24px; height: 24px; border-radius: 50%; overflow: hidden;
  border: 2px solid;
  display: flex; align-items: center; justify-content: center;
  background: #e5e7eb; margin-left: 2px; margin-top: 12px; flex-shrink: 0;
}
.cursor-avatar img { width: 100%; height: 100%; object-fit: cover; }
.cursor-avatar span { font-size: 11px; font-weight: 700; color: #fff; }

/* ── 드래그 중 유령 블록 ── */
.collab-ghost-block {
  position: fixed; pointer-events: none; z-index: 9998;
  border: none; border-left: 4px solid; border-radius: 10px;
  padding: 6px 10px; overflow: hidden;
  opacity: 0.5; box-shadow: 0 2px 8px rgba(0,0,0,.15);
}
.collab-ghost-block[data-color="purple"] { background: var(--purple-50); border-color: var(--purple-900); }
.collab-ghost-block[data-color="pink"]   { background: var(--pink-50);   border-color: var(--pink-600); }
.collab-ghost-block[data-color="teal"]   { background: var(--teal-50);   border-color: var(--teal-600); }
.collab-ghost-block[data-color="blue"]   { background: var(--blue-50);   border-color: var(--blue-600); }
.collab-ghost-block[data-color="amber"]  { background: var(--amber-50);  border-color: var(--amber-600); }

.collab-drop-preview {
  position: fixed; pointer-events: none; z-index: 9997;
  border-radius: 10px; border: 2px dashed var(--purple-900);
  background: rgba(83, 74, 183, 0.12);
  animation: collab-drop-pulse 1.2s ease-in-out infinite;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; color: var(--purple-900); font-weight: 600;
  letter-spacing: -0.01em; overflow: hidden;
}
/* schedule.css 의 drop-pulse 에 의존하지 않도록 컴포넌트 내부에 자체 정의 (scoped 로 이름 격리) */
@keyframes collab-drop-pulse {
  0%, 100% { background: rgba(83, 74, 183, 0.12); }
  50% { background: rgba(83, 74, 183, 0.20); }
}

.ghost-label {
  display: block; font-size: 9px;
  color: var(--cursor-color); font-weight: 700; margin-bottom: 2px;
}
.ghost-name {
  display: block; font-size: 12px; color: #1a1a2e; font-weight: 500;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

/* ── 다른 협업자가 잡고 있는 블록 ── */
.event-block.grabbed-by-other {
  opacity: 0.55; pointer-events: none;
}
</style>
