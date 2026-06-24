<template>
  <main id="main" class="plan-main">

    <!-- 작업실 헤더: 현재 여행 칩 + 모드 토글 + (정리)공유·자동저장·지도토글 -->
    <header class="plan-header">
      <div class="plan-trip-menu" ref="tripMenuRef">
        <button class="plan-trip-chip" :class="{ open: tripMenuOpen }"
                @click="tripMenuOpen = !tripMenuOpen"
                :aria-expanded="tripMenuOpen">
          <span class="plan-trip-icon">
            <svg width="15" height="15" viewBox="0 0 16 16" fill="none">
              <path d="M8 1.5C5.5 1.5 3.5 3.4 3.5 5.9 3.5 9.4 8 14.5 8 14.5s4.5-5.1 4.5-8.6C12.5 3.4 10.5 1.5 8 1.5z" stroke="currentColor" stroke-width="1.4"/>
              <circle cx="8" cy="6" r="1.7" fill="currentColor"/>
            </svg>
          </span>
          <span class="plan-trip-text">
            <span class="plan-trip-label">현재 여행</span>
            <span class="plan-trip-name" :class="{ 'plan-trip-name--empty': !currentTrip }">
              {{ currentTrip ? currentTrip.title : '여행 선택' }}
            </span>
          </span>
          <svg class="plan-trip-caret" :class="{ open: tripMenuOpen }" width="11" height="11" viewBox="0 0 12 12" fill="none">
            <path d="M3 4.5L6 7.5L9 4.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>

        <Transition name="trip-menu-pop">
          <div v-if="tripMenuOpen" class="plan-trip-dropdown">
            <div v-if="tripsLoading" class="plan-trip-dd-loading">로딩 중...</div>
            <template v-else>
              <button
                v-for="t in trips" :key="t.id"
                class="plan-trip-dd-item" :class="{ active: t.id === activeTrip }"
                @click="selectTrip(t.id)">
                <span class="plan-trip-dd-main">
                  <span class="plan-trip-dd-name">{{ t.title }}</span>
                  <span class="plan-trip-dd-dates">{{ t.startDate }} ~ {{ t.endDate }}</span>
                </span>
                <svg v-if="t.id === activeTrip" class="plan-trip-dd-check" width="13" height="13" viewBox="0 0 14 14" fill="none">
                  <path d="M2.5 7.5L5.5 10.5L11.5 3.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </button>
              <template v-if="collaboratingTrips.length">
                <div class="plan-trip-dd-group">초대받은 일정</div>
                <button
                  v-for="t in collaboratingTrips" :key="'collab-' + t.id"
                  class="plan-trip-dd-item" :class="{ active: t.id === activeTrip }"
                  @click="selectTrip(t.id)">
                  <span class="plan-trip-dd-main">
                    <span class="plan-trip-dd-name">{{ t.title }}</span>
                    <span class="plan-trip-dd-dates">{{ t.startDate }} ~ {{ t.endDate }}</span>
                  </span>
                  <svg v-if="t.id === activeTrip" class="plan-trip-dd-check" width="13" height="13" viewBox="0 0 14 14" fill="none">
                    <path d="M2.5 7.5L5.5 10.5L11.5 3.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </button>
              </template>
              <div v-if="!trips.length && !collaboratingTrips.length" class="plan-trip-dd-empty">등록된 일정이 없습니다</div>
              <button class="plan-trip-dd-new" @click="tripMenuOpen = false; openScheduleModal()">
                + 새 일정 만들기
              </button>
            </template>
          </div>
        </Transition>
      </div>

      <div class="plan-modes">
        <button class="plan-mode" :class="{ active: mode === 'explore' }" @click="mode = 'explore'">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
            <circle cx="7" cy="7" r="5" stroke="currentColor" stroke-width="1.6"/>
            <path d="M11 11L14 14" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
          </svg>
          탐색
        </button>
        <button class="plan-mode" :class="{ active: mode === 'organize' }" @click="mode = 'organize'">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
            <rect x="2.5" y="2.5" width="11" height="11" rx="1.5" stroke="currentColor" stroke-width="1.4"/>
            <path d="M2.5 6h11M6 6v7.5" stroke="currentColor" stroke-width="1.4"/>
          </svg>
          일정
        </button>
      </div>

      <span class="plan-header-spacer"></span>

      <!-- 협업자 초대·관리 (일정 선택 시 항상) -->
      <div v-if="currentTrip" class="plan-collab-menu">
        <button ref="collabBtnRef" class="plan-action-btn plan-action-btn--collab"
                :class="{ active: collabPanelOpen }"
                @click="collabPanelOpen = !collabPanelOpen">
          <span class="collab-status-dot" :class="{ connected: collab.connected }"></span>
          협업자
        </button>
        <Transition name="trip-menu-pop">
          <div v-if="collabPanelOpen" ref="collabPanelRef" class="plan-collab-overlay">
            <CollaboratorPanel
              :trip-id="activeTrip"
              :is-owner="activeTripIsOwner"
              :owner-label="activeTripOwnerLabel"
              :participants="collab.participants"
              :color-map="collab.colorMap"
              @close="collabPanelOpen = false"
            />
          </div>
        </Transition>
      </div>

      <!-- 정리 모드 전용 우측 액션 -->
      <template v-if="mode === 'organize' && currentTrip">
        <span class="plan-saved">
          <svg width="13" height="13" viewBox="0 0 14 14" fill="none">
            <path d="M2.5 7.5L5.5 10.5L11.5 3.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          저장됨
        </span>
        <button class="plan-action-btn" @click="toggleBoardMap">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
            <path d="M2 4l4-1.5 4 1.5 4-1.5v9.5L10 13l-4-1.5L2 13V4z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
            <path d="M6 2.5v9M10 4v9.5" stroke="currentColor" stroke-width="1.3"/>
          </svg>
          지도
        </button>
        <button class="plan-action-btn plan-action-btn--share" @click="shareTrip">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
            <circle cx="12" cy="3.5" r="2" stroke="currentColor" stroke-width="1.3"/>
            <circle cx="4" cy="8" r="2" stroke="currentColor" stroke-width="1.3"/>
            <circle cx="12" cy="12.5" r="2" stroke="currentColor" stroke-width="1.3"/>
            <path d="M5.7 7L10.3 4.5M5.7 9L10.3 11.5" stroke="currentColor" stroke-width="1.3"/>
          </svg>
          공유
        </button>
      </template>
    </header>

  <section v-show="mode === 'explore'" id="screen-explore">

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
        <span class="search-icon">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <circle cx="7" cy="7" r="5" stroke="currentColor" stroke-width="1.5"/>
            <path d="M11 11L14 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </span>
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
                  @click="toggleSearchRegion(rg.region, $event)">
            <span class="group-chevron" :class="{ open: !collapsedSearchRegions[rg.region] }">▶</span>
            {{ rg.region }}
            <span class="group-count">{{ rg.total }}</span>
          </button>
          <Transition name="tree-slide" @after-leave="checkVisible">
            <div v-if="!rg.region || !collapsedSearchRegions[rg.region]"
                 :class="{ 'sg-level-indent': !!rg.region }">
              <div v-for="sg in rg.sgGroups" :key="sg.sg ?? '__nosg__'">
                <button v-if="sg.sg" class="sg-section-header"
                        @click="toggleSearchSigungu(`${rg.region}__${sg.sg}`, $event)">
                  <span class="group-chevron" :class="{ open: !collapsedSearchSigungus[`${rg.region}__${sg.sg}`] }">▶</span>
                  {{ sg.sg }}
                  <span class="group-count">{{ sg.total }}</span>
                </button>
                <Transition name="tree-slide" @after-leave="checkVisible">
                  <div v-if="!sg.sg || !collapsedSearchSigungus[`${rg.region}__${sg.sg}`]"
                       class="group-cards-wrap">
                    <div v-for="cg in toRenderGroups(sg)" :key="cg.cat ?? '__all__'" class="cat-section-wrap">
                      <button v-if="cg.cat" class="cat-section-header"
                              @click="toggleSearchCat(`${rg.region ?? ''}__${sg.sg ?? ''}__${cg.cat}`, $event)">
                        <span class="group-chevron" :class="{ open: !collapsedSearchCats[`${rg.region ?? ''}__${sg.sg ?? ''}__${cg.cat}`] }">▶</span>
                        {{ cg.cat }}
                        <span class="group-count">{{ cg.total }}</span>
                      </button>
                      <Transition name="tree-slide" @after-leave="checkVisible">
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
                                 :style="{ '--card-cat-ink': catColor(a.category) }"
                                 draggable="true"
                                 @click="selectAttraction(a)"
                                 @dragstart="onCardDragStart($event, a)"
                                 @dragend="onCardDragEnd">
                              <div v-if="addedIds.has(a.id)" class="candidate-badge">✓</div>
                              <div class="card-img" :style="{ background: colorFor(a.contentTypeId) }">
                                <img v-if="a.firstImage" :src="a.firstImage" loading="lazy" />
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

    </div>

    <!-- 상세 패널 (attr-list 위에 슬라이드인) -->
    <Transition :name="detailSlideName">
      <div v-if="detailOpen && selectedAttraction" class="detail-panel">

        <!-- 상단 액션 바 -->
        <div class="detail-nav">
          <button class="detail-back"
                  @click="detailStack.length ? goBackDetail() : closeDetail()"
                  :aria-label="detailStack.length ? '이전 장소' : '닫기'">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M8.5 2L3.5 7L8.5 12" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            {{ detailStack.length ? '이전' : '목록' }}
          </button>
          <button class="card-add detail-add-btn"
                  :class="{ added: addedIds.has(selectedAttraction.id) }"
                  @click.stop="addedIds.has(selectedAttraction.id) ? removeByAttraction(selectedAttraction.id) : addToTrip(selectedAttraction)">
            {{ addedIds.has(selectedAttraction.id) ? '× 제거' : '+ 추가' }}
          </button>
        </div>

        <!-- 스크롤 영역 -->
        <div class="detail-scroll" ref="detailScrollEl">

          <!-- 히어로 이미지 -->
          <div class="detail-hero" @click="allImages.length && (lightboxOpen = true)">
            <img v-if="allImages[currentImageIdx]"
                 :src="allImages[currentImageIdx].url"
                 class="detail-img"
                 :alt="selectedAttraction.title" />
            <div v-else class="detail-img-empty"
                 :style="{ background: colorFor(selectedAttraction.contentTypeId) }">
              <span class="detail-img-emoji">{{ emojiFor(selectedAttraction.contentTypeId) }}</span>
            </div>
            <!-- 이전/다음 버튼 -->
            <button v-if="currentImageIdx > 0"
                    class="detail-hero-nav detail-hero-prev"
                    @click.stop="currentImageIdx--"
                    aria-label="이전 이미지">‹</button>
            <button v-if="currentImageIdx < allImages.length - 1"
                    class="detail-hero-nav detail-hero-next"
                    @click.stop="currentImageIdx++"
                    aria-label="다음 이미지">›</button>
            <!-- 이미지 카운터 -->
            <div v-if="allImages.length > 1" class="detail-hero-counter">
              {{ currentImageIdx + 1 }} / {{ allImages.length }}
            </div>
          </div>

          <!-- 이미지 갤러리 썸네일 띠 -->
          <div v-if="allImages.length > 1" class="detail-gallery">
            <button
              v-for="(img, idx) in allImages"
              :key="idx"
              class="detail-gallery-thumb"
              :class="{ active: idx === currentImageIdx }"
              :aria-label="`이미지 ${idx + 1}`"
              @click="currentImageIdx = idx">
              <img :src="img.small" :alt="img.name || `이미지 ${idx + 1}`" loading="lazy" />
            </button>
          </div>

          <!-- 본문 -->
          <div class="detail-body">

            <!-- 뱃지 + 지역 -->
            <div class="detail-cat-row">
              <span class="detail-cat-badge"
                    :style="{ background: colorFor(selectedAttraction.contentTypeId) }">
                {{ selectedAttraction.category }}
              </span>
              <span class="detail-region">{{ selectedAttraction.sigunguName || selectedAttraction.region }}</span>
            </div>

            <!-- 제목 -->
            <h2 class="detail-title">{{ selectedAttraction.title }}</h2>

            <!-- 기본 정보 행들 -->
            <div class="detail-info-list">

              <!-- 주소 (클립보드 복사) -->
              <button v-if="selectedAttraction.address || selectedAttractionDetail?.addr1"
                      class="detail-info-row detail-info-row--clickable"
                      :title="'주소 복사'"
                      @click="copyAddress">
                <span class="detail-info-icon">
                  <svg width="13" height="13" viewBox="0 0 14 14" fill="none">
                    <path d="M7 1C4.79 1 3 2.79 3 5c0 3.25 4 8 4 8s4-4.75 4-8c0-2.21-1.79-4-4-4z" stroke="currentColor" stroke-width="1.4" fill="none"/>
                    <circle cx="7" cy="5" r="1.3" fill="currentColor"/>
                  </svg>
                </span>
                <span class="detail-info-text">
                  {{ selectedAttractionDetail?.addr1 || selectedAttraction.address }}{{ selectedAttractionDetail?.addr2 ? ' ' + selectedAttractionDetail.addr2 : '' }}
                </span>
                <span class="detail-info-copy-hint">복사</span>
              </button>

              <!-- 전화 -->
              <a v-if="selectedAttractionDetail?.tel && selectedAttractionDetail.tel.trim()"
                 :href="`tel:${selectedAttractionDetail.tel.trim()}`"
                 class="detail-info-row detail-info-row--link">
                <span class="detail-info-icon">
                  <svg width="13" height="13" viewBox="0 0 14 14" fill="none">
                    <path d="M2 2.5C2 2.5 3.5 1 4.5 2.5L5.5 4C6 4.8 5.3 5.4 5 5.7c.5 1 1.3 1.8 2.3 2.3.3-.3.9-1 1.7-.5l1.5 1C12 9.5 10.5 11 10.5 11 8 13 1 7 2 2.5z" stroke="currentColor" stroke-width="1.4" fill="none"/>
                  </svg>
                </span>
                <span class="detail-info-text">
                  {{ selectedAttractionDetail.tel.trim() }}
                  <template v-if="selectedAttractionDetail.telname && selectedAttractionDetail.telname.trim()">
                    ({{ selectedAttractionDetail.telname.trim() }})
                  </template>
                </span>
              </a>

              <!-- 홈페이지 -->
              <a v-if="selectedAttractionDetail?.homepage && selectedAttractionDetail.homepage.trim()"
                 :href="stripHtml(selectedAttractionDetail.homepage)"
                 target="_blank"
                 rel="noopener noreferrer"
                 class="detail-info-row detail-info-row--link">
                <span class="detail-info-icon">
                  <svg width="13" height="13" viewBox="0 0 14 14" fill="none">
                    <circle cx="7" cy="7" r="5.5" stroke="currentColor" stroke-width="1.4"/>
                    <path d="M7 1.5C7 1.5 5 4 5 7s2 5.5 2 5.5M7 1.5C7 1.5 9 4 9 7s-2 5.5-2 5.5M1.5 7h11" stroke="currentColor" stroke-width="1.2"/>
                  </svg>
                </span>
                <span class="detail-info-text detail-info-text--url">홈페이지 방문</span>
                <span class="detail-info-ext-icon">↗</span>
              </a>

            </div>

            <!-- 로딩 표시 -->
            <div v-if="detailLoading" class="detail-loading-state">
              <span class="detail-loading-dot"></span>
              <span class="detail-loading-dot"></span>
              <span class="detail-loading-dot"></span>
              <span class="detail-loading-label">상세 정보 불러오는 중</span>
            </div>

            <!-- intro 기본 운영 정보 테이블 -->
            <template v-if="selectedAttractionDetail?.intro && Object.keys(selectedAttractionDetail.intro).length">
              <div class="detail-section-divider"></div>

              <!-- heritage 배지 행 -->
              <div v-if="activeHeritages(selectedAttractionDetail.intro).length" class="detail-heritage-row">
                <span
                  v-for="label in activeHeritages(selectedAttractionDetail.intro)"
                  :key="label"
                  class="detail-heritage-badge">
                  {{ label }}
                </span>
              </div>

              <!-- intro 테이블 (heritage* / 빈 chk* 제외) -->
              <div v-if="filteredIntroEntries(selectedAttractionDetail.intro).length" class="detail-info-table">
                <template v-for="[key, val] in filteredIntroEntries(selectedAttractionDetail.intro)" :key="key">
                  <div class="detail-table-row">
                    <span class="detail-table-label">{{ DETAIL_INFO_LABELS[key] || key }}</span>
                    <span class="detail-table-val" v-html="sanitizeHtml(String(val))"></span>
                  </div>
                </template>
              </div>
            </template>

            <!-- infoList 추가 정보 테이블 -->
            <template v-if="selectedAttractionDetail?.infoList && selectedAttractionDetail.infoList.length">
              <div class="detail-section-divider"></div>
              <div class="detail-info-table">
                <div v-for="(info, idx) in selectedAttractionDetail.infoList" :key="idx"
                     class="detail-table-row">
                  <span class="detail-table-label">{{ info.infoname }}</span>
                  <span class="detail-table-val" v-html="sanitizeHtml(String(info.infotext || ''))"></span>
                </div>
              </div>
            </template>

            <!-- overview 소개글 -->
            <template v-if="selectedAttractionDetail?.overview && selectedAttractionDetail.overview.trim()">
              <div class="detail-section-divider"></div>
              <p class="detail-overview">{{ selectedAttractionDetail.overview }}</p>
            </template>

            <!-- AI 챗봇 -->
            <AttractionChat
              :attraction-id="selectedAttraction.id"
              :attraction-title="selectedAttraction.title"
              @select="pinNearby" />

          </div>
        </div>
      </div>
    </Transition>

    <!-- 라이트박스 -->
    <Teleport to="body">
      <Transition name="lightbox-fade">
        <div v-if="lightboxOpen" class="lightbox-overlay" @click.self="lightboxOpen = false">
          <button class="lightbox-close" @click="lightboxOpen = false" aria-label="닫기">✕</button>
          <button v-if="currentImageIdx > 0"
                  class="lightbox-nav lightbox-prev"
                  @click="currentImageIdx--"
                  aria-label="이전">‹</button>
          <img :src="allImages[currentImageIdx]?.url" class="lightbox-img" :alt="allImages[currentImageIdx]?.name" />
          <button v-if="currentImageIdx < allImages.length - 1"
                  class="lightbox-nav lightbox-next"
                  @click="currentImageIdx++"
                  aria-label="다음">›</button>
          <div class="lightbox-counter">{{ currentImageIdx + 1 }} / {{ allImages.length }}</div>
        </div>
      </Transition>
    </Teleport>

    <!-- 우측 하단 트레이: 내 일정 -->
    <div class="trip-tray" :class="{ dragging: isDraggingCard, open: trayOpen }"
         @dragover.prevent
         @drop="onDropToActiveTray">
      <div class="tray-header">
        <span class="tray-icon">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <rect x="3" y="2.5" width="10" height="12" rx="1.5" stroke="currentColor" stroke-width="1.4"/>
            <path d="M5.5 2.5V4h5V2.5" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/>
            <path d="M5.5 7.5h5M5.5 10.5h3" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
          </svg>
        </span>
        <div class="tray-switcher">
          <span class="tray-title">보관함</span>
        </div>
        <button class="tray-toggle-btn" @click="trayOpen = !trayOpen"
                :aria-label="trayOpen ? '보관함 접기' : '보관함 펼치기'">
          <span class="tray-count">{{ activeTripCandidates.length }}</span>
          <span class="tray-toggle-chev" :class="{ open: trayOpen }">▼</span>
        </button>
      </div>

      <Transition name="tray-slide">
        <div v-show="trayOpen" class="tray-body">

          <!-- 로딩 / 일정 없음 -->
          <div v-if="tripsLoading" class="tray-loading">로딩 중...</div>
          <div v-else-if="!trips.length" class="tray-empty">
            <p>등록된 일정이 없어요.<br>상단 여행 메뉴에서 새 일정을 만들어보세요.</p>
          </div>

          <!-- 현재 일정 후보 목록 (일정 전환은 헤더 드롭다운에서) -->
          <template v-else-if="currentTrip">
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
          </template>

          <!-- activeTrip 이 목록에 없을 때(삭제·동기화 지연) 폴백: 빈 패널 방지 -->
          <div v-else class="tray-empty">
            <p>상단 여행 메뉴에서 일정을 선택하세요.</p>
          </div>

        </div>
      </Transition>
    </div>

  </section>

    <!-- 정리(organize) 모드 — 24h 격자 타임테이블 (ScheduleBoard 재사용) -->
    <section v-if="mode === 'organize'" class="plan-organize">
      <div v-if="!activeTrip" class="plan-organize-empty">
        <div class="plan-stub-card">
          <p>아직 작업할 여행이 없어요.<br>먼저 탐색에서 가고 싶은 곳을 담아오세요.</p>
          <button class="btn-primary" @click="mode = 'explore'">탐색 모드로 →</button>
        </div>
      </div>
      <ScheduleBoard
        v-else
        ref="boardRef"
        embedded
        :trip-id="activeTrip"
        @explore="mode = 'explore'" />
    </section>

  </main>
</template>

<script setup>
import { ref, computed, reactive, inject, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { useActiveTripStore } from '@/stores/activeTrip'
import { useCollabStore } from '@/stores/collab'
import { searchAttractions, fetchRegions, fetchAttractionDetail } from '@/api/attraction'
import { tripApi } from '@/api/trip'
import AttractionChat from '@/components/AttractionChat.vue'
import ScheduleBoard from '@/components/ScheduleBoard.vue'
import CollaboratorPanel from '@/components/CollaboratorPanel.vue'

const toast = useToastStore()
const activeTripStore = useActiveTripStore()
const collab = useCollabStore()
const openScheduleModal = inject('openScheduleModal')

const route = useRoute()
const router = useRouter()

// organize 모드: 보드 컴포넌트 ref (지도 토글 제어)
const boardRef = ref(null)
function toggleBoardMap() { boardRef.value?.toggleMap?.() }
function shareTrip() {
  if (!activeTrip.value) return
  router.push({ path: '/community', query: { shareTrip: activeTrip.value } })
}

// ── UI state (모드 / 트레이 토글 / 드래그 감지) ──
const mode = ref('explore') // 'explore' | 'organize'
const trayOpen = ref(false) // 평소 지도를 넓게: 담기/드래그 시 자동으로 열림
const isDraggingCard = ref(false)
const currentTrip = computed(() =>
  trips.value.find(t => t.id === activeTrip.value) ||
  collaboratingTrips.value.find(t => t.id === activeTrip.value)
)

// ── 협업자 패널 (초대·역할) ──
const collabPanelOpen = ref(false)
const collabPanelRef = ref(null)
const collabBtnRef = ref(null)
const activeTripDetail = ref(null)  // 현재 일정 상세 (myRole·ownerNickname)
const activeTripIsOwner = computed(() => activeTripDetail.value?.myRole === 'OWNER')
const activeTripOwnerLabel = computed(() => activeTripDetail.value?.ownerNickname ?? '소유자')

// ── 헤더 현재 여행 드롭다운 ──
const tripMenuOpen = ref(false)
const tripMenuRef = ref(null)
function selectTrip(id) {
  activeTrip.value = id
  activeTripStore.set(id)
  tripMenuOpen.value = false
}
function onTripMenuOutside(e) {
  if (tripMenuRef.value && !tripMenuRef.value.contains(e.target)) tripMenuOpen.value = false
  if (collabPanelOpen.value
      && collabPanelRef.value && !collabPanelRef.value.contains(e.target)
      && collabBtnRef.value && !collabBtnRef.value.contains(e.target)) {
    collabPanelOpen.value = false
  }
}
function onTripMenuKey(e) {
  if (e.key === 'Escape') tripMenuOpen.value = false
}

const trips = ref([])
const collaboratingTrips = ref([])   // 초대받아 참여 중인 일정
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
const detailStack = ref([])     // 상세 → 주변 상세로 드릴다운한 방문 이력(뒤로가기용)
const detailScrollEl = ref(null)
const scrollPositions = ref({}) // 관광지별 상세 패널 스크롤 위치(뒤로가기 시 복원)
const detailCache = {}          // 관광지별 상세 데이터 캐시(뒤로가기 시 재조회 없이 즉시 표시)
const detailLoading = ref(false)
const currentImageIdx = ref(0)
const lightboxOpen = ref(false)
const detailOpen = ref(false)
const detailSlideName = ref('detail-slide')

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
const groupObserverMap = new WeakMap()
const observedElements = new Set()

const vObserve = {
  mounted(el, binding) {
    const callback = () => {
      observedElements.delete(el)
      groupObserverMap.delete(el)
      binding.value()
    }
    groupObserverMap.set(el, callback)
    observedElements.add(el)
  },
  beforeUpdate(el, binding) {
    const callback = () => {
      observedElements.delete(el)
      groupObserverMap.delete(el)
      binding.value()
    }
    groupObserverMap.set(el, callback)
    observedElements.add(el)
  },
  unmounted(el) {
    groupObserverMap.delete(el)
    observedElements.delete(el)
  }
}

const VISIBLE_BATCH = 3

function checkVisible() {
  if (!scrollEl.value) return
  const rootRect = scrollEl.value.getBoundingClientRect()
  let triggered = 0
  for (const el of [...observedElements]) {
    if (triggered >= VISIBLE_BATCH) break
    const rect = el.getBoundingClientRect()
    if (rect.height === 0) continue
    if (rect.bottom > rootRect.top && rect.top < rootRect.bottom) {
      groupObserverMap.get(el)?.()
      triggered++
    }
  }
}

let scrollRafId = null
function onScrollCheck() {
  if (scrollRafId) return
  scrollRafId = requestAnimationFrame(() => {
    scrollRafId = null
    checkVisible()
  })
}

let checkTimer = null
function scheduleCheck() {
  clearTimeout(checkTimer)
  checkTimer = setTimeout(() => requestAnimationFrame(() => checkVisible()), 60)
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
      keyword: resolveSearch().keyword,
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
    if (groupItems[key] && seq === loadSeq) {
      groupItems[key].loading = false
      scheduleCheck()
    }
  }
}

// 지역 칩은 백엔드(sido 테이블) 응답에서 파생 — 광역시 포함 전체 시도 노출
const regions = computed(() => regionsData.value.map(r => r.sido))
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
function toggleSearchRegion(region, e) {
  collapsedSearchRegions[region] = !collapsedSearchRegions[region]
  if (collapsedSearchRegions[region]) scrollGroupToTop(e?.currentTarget)
  nextTick(() => requestAnimationFrame(() => checkVisible()))
}
function toggleSearchSigungu(key, e) {
  collapsedSearchSigungus[key] = !collapsedSearchSigungus[key]
  if (collapsedSearchSigungus[key]) scrollGroupToTop(e?.currentTarget)
  nextTick(() => requestAnimationFrame(() => checkVisible()))
}
function toggleSearchCat(key, e) {
  collapsedSearchCats[key] = !collapsedSearchCats[key]
  if (collapsedSearchCats[key]) scrollGroupToTop(e?.currentTarget)
  nextTick(() => requestAnimationFrame(() => checkVisible()))
}

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

// ── Heritage 배지 / chk* 필터 헬퍼 ──
const heritageLabels = { heritage1: '세계문화유산', heritage2: '세계자연유산', heritage3: '세계기록유산' }
const CHK_FILTER_KEYS = new Set([
  'chkcreditcard', 'chkbabycarriage', 'chkpet',
  'chkcreditcardculture', 'chkbabycarriageculture', 'chkpetculture',
  'chkcreditcardleports', 'chkbabycarriageleports', 'chkpetleports',
  'chkcreditcardfood', 'chkbabycarriageshopping', 'chkcreditcardshopping', 'chkpetshopping',
  // 예약 — 없으면 의미 없음
  'reservation', 'reservationfood', 'reservationlodging',
  // 편의시설 — 없으면 의미 없음
  'kidsfacility', 'karaoke', 'sauna', 'beauty', 'beverage', 'barbecue',
  'campfire', 'fitness', 'bicycle', 'sports', 'seminar', 'publicbath', 'publicpc', 'pickup',
  // 기타
  'culturecenter', 'restroom', 'smoking', 'packing',
])

function filteredIntroEntries(intro) {
  if (!intro) return []
  return Object.entries(intro).filter(([key, val]) => {
    if (Object.prototype.hasOwnProperty.call(heritageLabels, key)) return false // 별도 칩으로 처리
    if (CHK_FILTER_KEYS.has(key)) return val && val !== '없음' && val !== '0'
    return val && String(val).trim()
  })
}

function activeHeritages(intro) {
  if (!intro) return []
  return Object.keys(heritageLabels).filter(k => intro[k] === '1').map(k => heritageLabels[k])
}

const allImages = computed(() => {
  if (!selectedAttraction.value) return []
  const imgs = []
  const f1 = selectedAttraction.value.firstImage || selectedAttractionDetail.value?.firstImage
  const f2 = selectedAttractionDetail.value?.firstImage2
  if (f1) imgs.push({ url: f1, small: f1, name: '대표이미지' })
  if (f2 && f2 !== f1) imgs.push({ url: f2, small: f2, name: '대표이미지2' })
  for (const img of (selectedAttractionDetail.value?.images ?? [])) {
    if (img.originimgurl && !imgs.some(i => i.url === img.originimgurl)) {
      imgs.push({ url: img.originimgurl, small: img.smallimageurl || img.originimgurl, name: img.imgname || '' })
    }
  }
  return imgs
})

const DETAIL_INFO_LABELS = {
  // 관광지 (12)
  expagerange:      '체험 가능 연령',
  expguide:         '체험 안내',
  infocenter:       '문의',
  opendate:         '개장일',
  parking:          '주차',
  restdate:         '쉬는날',
  taketime:         '관람 소요시간',
  theme:            '테마',
  accomcount:       '수용 인원',
  chkbabycarriage:  '유모차 대여',
  chkcreditcard:    '신용카드',
  chkpet:           '반려동물 동반',
  useseason:        '이용 시기',
  usetime:          '이용 시간',

  // 문화시설 (14)
  accomcountculture:      '수용 인원',
  chkbabycarriageculture: '유모차 대여',
  chkcreditcardculture:   '신용카드',
  chkpetculture:          '반려동물 동반',
  infocenterculture:      '문의',
  parkingculture:         '주차',
  parkingfee:             '주차 요금',
  restdateculture:        '쉬는날',
  scale:                  '규모',
  spendtime:              '관람 소요시간',
  usefee:                 '이용 요금',
  usetimeculture:         '이용 시간',

  // 행사/공연/축제 (15)
  agelimit:             '관람 가능 연령',
  bookingplace:         '예매처',
  discountinfofestival: '할인 정보',
  eventhomepage:        '행사 홈페이지',
  eventplace:           '행사 장소',
  eventstartdate:       '행사 시작일',
  eventenddate:         '행사 종료일',
  festivalgrade:        '축제 등급',
  playtime:             '공연 시간',
  program:              '행사 프로그램',
  spendtimefestival:    '관람 소요시간',
  sponsor1:             '주최자',
  sponsor1tel:          '주최자 연락처',
  sponsor2:             '주관사',
  sponsor2tel:          '주관사 연락처',
  subevent:             '부대행사',
  usetimefestival:      '이용 요금',

  // 여행코스 (25)
  distance:             '코스 거리',
  infocentertourcourse: '문의',
  schedule:             '코스 일정',
  taketime_tourcourse:  '소요 시간',
  theme_tourcourse:     '테마',

  // 레포츠 (28)
  accomcountleports:      '수용 인원',
  chkbabycarriageleports: '유모차 대여',
  chkcreditcardleports:   '신용카드',
  chkpetleports:          '반려동물 동반',
  expagerangeleports:     '체험 가능 연령',
  infocenterleports:      '문의',
  openperiod:             '개장 기간',
  parkingfeeleports:      '주차 요금',
  parkingleports:         '주차',
  reservation:            '예약 안내',
  restdateleports:        '쉬는날',
  scaleleports:           '규모',
  usetimeleports:         '이용 시간',
  usefeeleports:          '이용 요금',

  // 숙박 (32)
  accomcountlodging:  '수용 인원',
  barbecue:           '바비큐',
  beauty:             '뷰티 시설',
  beverage:           '음료 판매',
  bicycle:            '자전거 대여',
  campfire:           '캠프파이어',
  checkintime:        '체크인',
  checkouttime:       '체크아웃',
  chkcooking:         '취사',
  fitness:            '피트니스',
  foodplace:          '식사 장소',
  infocenterlodging:  '문의',
  karaoke:            '노래방',
  parkinglodging:     '주차',
  pickup:             '픽업 서비스',
  placeinfo:          '장소 정보',
  publicbath:         '공용 목욕탕',
  publicpc:           '공용 PC',
  refundregulation:   '환불 규정',
  reservationlodging: '예약 안내',
  reservationurl:     '예약 사이트',
  roomcount:          '객실 수',
  roomtype:           '객실 유형',
  sauna:              '사우나',
  scalelodging:       '규모',
  seminar:            '세미나실',
  sports:             '스포츠 시설',
  subfacility:        '부대 시설',

  // 쇼핑 (38)
  chkbabycarriageshopping: '유모차 대여',
  chkcreditcardshopping:   '신용카드',
  chkpetshopping:          '반려동물 동반',
  culturecenter:           '문화센터',
  fairday:                 '장서는날',
  infocentershopping:      '문의',
  opendateshopping:        '개장일',
  opentime:                '영업 시간',
  parkingshopping:         '주차',
  restdateshopping:        '쉬는날',
  restroom:                '화장실',
  saleitem:                '판매 품목',
  saleitemcost:            '판매 품목 가격',
  scaleshopping:           '규모',
  shopguide:               '매장 안내',

  // 음식점 (39)
  chkcreditcardfood:  '신용카드',
  discountinfo:       '할인 정보',
  firstmenu:          '대표 메뉴',
  infocenterfood:     '문의',
  kidsfacility:       '어린이 시설',
  lcnsno:             '인허가 번호',
  opendatefood:       '개업일',
  opentimefood:       '영업 시간',
  packing:            '포장 가능',
  parkingfood:        '주차',
  reservationfood:    '예약 안내',
  restdatefood:       '쉬는날',
  scalefood:          '규모',
  seat:               '좌석 수',
  smoking:            '흡연',
  treatmenu:          '취급 메뉴',
}

function stripHtml(str) {
  if (!str) return ''
  // href 속성이 있으면 URL을 추출 (homepage 필드가 <a href='...'> 형태일 때)
  const hrefMatch = str.match(/href=['"]([^'"]+)['"]/i)
  if (hrefMatch) return hrefMatch[1].trim()
  return str.replace(/<[^>]+>/g, '').trim()
}

function sanitizeHtml(str) {
  if (!str) return ''
  // <br>, <br/> 만 허용하고 나머지 태그 제거
  return str.replace(/<(?!br\s*\/?)[^>]+>/gi, '').trim()
}

async function copyAddress() {
  const addr = (selectedAttractionDetail.value?.addr1 || selectedAttraction.value?.address || '')
    + (selectedAttractionDetail.value?.addr2 ? ' ' + selectedAttractionDetail.value.addr2 : '')
  if (!addr.trim()) return
  try {
    await navigator.clipboard.writeText(addr.trim())
    toast.show('주소가 복사됐어요')
  } catch {
    toast.show('복사에 실패했어요')
  }
}

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
  naver.maps.Event.addListener(naverMap, 'click', () => {
    if (!detailOpen.value) closePin()
  })
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

// ── AI 챗봇 주변 칩 클릭: 현재 상세 패널은 유지하고 지도에만 핀 + 상세보기 인포윈도우 ──
let nearbyMarker = null

function clearNearbyMarker() {
  nearbyMarker?.setMap(null)
  nearbyMarker = null
}

function pinNearby(place) {
  if (!naverMap || !place?.latitude || !place?.longitude) return
  clearNearbyMarker()
  const color = catColor(place.category)
  const latlng = new naver.maps.LatLng(Number(place.latitude), Number(place.longitude))
  window.__naverNearbyClick = () => openNearbyInfo(place, latlng, color)
  nearbyMarker = new naver.maps.Marker({
    map: naverMap,
    position: latlng,
    icon: {
      content: `<div onclick="event.stopPropagation();window.__naverNearbyClick()" style="width:28px;height:28px;background:${color};border:3px solid white;border-radius:50%;box-shadow:0 3px 10px rgba(0,0,0,.4);box-sizing:border-box;cursor:pointer"></div>`,
      anchor: new naver.maps.Point(14, 14)
    },
    zIndex: 99
  })
  // 지도가 크게 이동할 때는 morph(idle) 완료 후 인포윈도우를 열어야 위치가 어긋나지 않음
  const targetZoom = Math.max(naverMap.getZoom(), 14)
  const cur = naverMap.getCenter()
  const needsMove = naverMap.getZoom() < 14
    || Math.abs(cur.lat() - Number(place.latitude)) > 0.0001
    || Math.abs(cur.lng() - Number(place.longitude)) > 0.0001
  naverMap.morph(latlng, targetZoom)
  if (needsMove) {
    naver.maps.Event.once(naverMap, 'idle', () => openNearbyInfo(place, latlng, color))
  } else {
    openNearbyInfo(place, latlng, color)
  }
}

function openNearbyInfo(place, latlng, color) {
  window.__naverOpenNearbyDetail = () => goToNearbyDetail(place)
  infoWindow.setContent(`<div style="padding:10px 12px;font-family:inherit;min-width:160px;max-width:220px"><div style="font-size:11px;color:${color};font-weight:700;margin-bottom:3px">${place.category || '주변'}</div><div style="font-size:13px;font-weight:600;color:#1a1a2e;margin-bottom:8px;line-height:1.3">${place.title}</div><button onclick="window.__naverOpenNearbyDetail()" style="width:100%;padding:6px 0;background:${color};color:#fff;border:none;border-radius:6px;font-size:12px;font-weight:600;cursor:pointer">상세정보 보기 →</button></div>`)
  infoWindow.open(naverMap, latlng)
}

// 현재 상세 패널의 스크롤 위치를 해당 관광지 키로 저장
function saveCurrentScroll() {
  const id = selectedAttraction.value?.id
  if (id != null && detailScrollEl.value) scrollPositions.value[id] = detailScrollEl.value.scrollTop
}

// 앞으로(드릴다운) 전환 시 새 내용만 좌측 슬라이드 인 1회 재생(패널은 유지)
function playPushAnim() {
  const el = detailScrollEl.value
  if (!el) return
  el.classList.remove('detail-push-anim')
  void el.offsetWidth   // 리플로우 강제로 애니메이션 재시작
  el.classList.add('detail-push-anim')
}

// 인포윈도우의 '상세정보 보기'를 명시적으로 눌렀을 때만 해당 장소 상세로 이동.
// 현재 보던 장소를 스택에 쌓아 뒤로가기로 대화까지 복원할 수 있게 한다.
async function goToNearbyDetail(place) {
  saveCurrentScroll()
  clearNearbyMarker()
  if (selectedAttraction.value && selectedAttraction.value.id !== place.id) {
    detailStack.value.push(selectedAttraction.value)
  }
  // 앞으로 갈 때: 패널은 유지(이전이 사라지지 않음)하고 새 내용만 슬라이드 인
  await selectAttraction(place, { keepStack: true, keepDetailOpen: true })
  await openDetail()
  playPushAnim()
}

// 뒤로가기: 직전 방문 장소로 복원 (대화·스크롤 위치 모두 복원)
async function goBackDetail() {
  saveCurrentScroll()
  const prev = detailStack.value.pop()
  if (!prev) { closeDetail(); return }
  await selectAttraction(prev, { keepStack: true, keepDetailOpen: true })
  await openDetail()
}

function updateSelectedMarker(a) {
  clearSelectedMarker()
  if (!naverMap || !a?.latitude || !a?.longitude) return
  const color = catColor(a.category)
  window.__naverPinClick = () => { if (!detailOpen.value) showAttractionInfoWindow(a) }
  selectedMarker = new naver.maps.Marker({
    map: naverMap,
    position: new naver.maps.LatLng(Number(a.latitude), Number(a.longitude)),
    icon: {
      content: `<div onclick="event.stopPropagation();window.__naverPinClick()" style="width:32px;height:32px;background:${color};border:3px solid white;border-radius:50%;box-shadow:0 3px 10px rgba(0,0,0,.4);box-sizing:border-box;outline:3px solid ${color};cursor:pointer"></div>`,
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
      const a = {
        id: c.attractionId, title: c.attractionName, category: c.category,
        contentTypeId: null, region: c.cityName, sigunguName: c.sigunguName,
        sigunguCode: c.sigunguCode, latitude: c.latitude, longitude: c.longitude,
        firstImage: null, address: null
      }
      infoWindow?.close()
      detailSlideName.value = ''
      detailOpen.value = false
      selectedAttractionDetail.value = null
      currentImageIdx.value = 0
      lightboxOpen.value = false
      selectedAttraction.value = a
      nextTick(() => { detailSlideName.value = 'detail-slide' })
      updateSelectedMarker(a)
      showAttractionInfoWindow(a)
    })
    markers.push(marker)
    markerIdSet.add(c.attractionId)
  })
  fitMap()
}

watch(activeTripCandidates, updateMarkers)

watch(activeTrip, async (id) => {
  activeTripStore.set(id ?? null)
  // URL 동기화: 현재 일정과 /plan/:tripId 를 일치시킴(새로고침·공유 링크 정합)
  if (id && String(route.params.tripId ?? '') !== String(id)) {
    router.replace({ path: `/plan/${id}` })
  }
  if (!id) {
    activeTripCandidates.value = []
    addedIds.value = new Set()
    candidateIdMap.value = new Map()
    activeTripDetail.value = null
    return
  }
  try {
    const detail = await tripApi.get(id)
    activeTripDetail.value = detail
    activeTripCandidates.value = detail.candidates || []
    addedIds.value = new Set(detail.candidates.map(c => c.attractionId))
    candidateIdMap.value = new Map(detail.candidates.map(c => [c.attractionId, c.id]))
  } catch {
    activeTripDetail.value = null
    activeTripCandidates.value = []
  }
})

// 라우트 tripId 변경(딥링크/뒤로가기) → 현재 일정 전환 (컴포넌트 재사용 시 onMounted 미실행 대응)
watch(() => route.params.tripId, (val) => {
  const n = val ? Number(val) : null
  const known = trips.value.some(t => t.id === n) || collaboratingTrips.value.some(t => t.id === n)
  if (n && n !== activeTrip.value && known) {
    activeTrip.value = n
  }
})

// 정리→탐색 복귀 시 숨겨졌던 지도 리사이즈(타일 공백 방지). 지도는 v-show 라 인스턴스는 유지됨.
watch(mode, (m) => {
  if (m === 'explore' && naverMap) {
    nextTick(() => { try { window.naver?.maps?.Event?.trigger(naverMap, 'resize') } catch {} })
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
    const [owned, collaborating] = await Promise.allSettled([
      tripApi.list(),
      tripApi.listCollaborating(),
    ])
    trips.value = owned.status === 'fulfilled' ? owned.value : []
    collaboratingTrips.value = collaborating.status === 'fulfilled' ? collaborating.value : []
    const all = [...trips.value, ...collaboratingTrips.value]
    if (all.length) {
      // 우선순위: 라우트 tripId > 공유 store > 첫 일정(소유 우선)
      const routeId = route.params.tripId ? Number(route.params.tripId) : null
      const candidate = [routeId, activeTripStore.id]
        .find(id => id != null && all.some(t => t.id === id))
      activeTrip.value = candidate ?? all[0].id
      activeTripStore.set(activeTrip.value)
    }
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

// "도시 검색" 지원: 입력이 시도 이름과 매칭되면 keyword 가 아니라 region 필터로 해석한다.
// (백엔드 keyword 는 장소명만 매칭하므로 "대전" 입력 시 대전의 모든 장소가 안 나오는 문제 보정)
function resolveSearch() {
  const q = (searchQuery.value || '').trim()
  const matched = q ? regions.value.find(r => r === q || r.includes(q)) : null
  return {
    keyword: matched ? undefined : (q || undefined),
    region: matched || (selectedRegions.value.length ? selectedRegions.value.join(',') : undefined),
  }
}

async function loadAttractions() {
  loadSeq++
  const mySeq = loadSeq
  loading.value = true
  for (const key in groupItems) delete groupItems[key]

  try {
    const s = resolveSearch()
    const data = await searchAttractions({
      keyword: s.keyword,
      region: s.region,
      sigungu: selectedSigungus.value.length ? selectedSigungus.value : undefined,
      category: selectedCats.value.length ? selectedCats.value.join(',') : undefined,
      page: 0,
      size: 1,
    })
    if (mySeq !== loadSeq) return
    statsData.value = data.groupStats || []
    total.value = data.total
    await nextTick()
    requestAnimationFrame(() => requestAnimationFrame(() => checkVisible()))
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

// 그룹 접기 시 스크롤 처리.
//  - 상단에 "스티키로 붙어있는" 그룹을 접으면: 접은 헤더 다음(= 다음 그룹 첫 항목)이 스티키 라인에 오게 이동.
//  - 붙어있지 않은(흐름상 보이는) 그룹을 접으면: 스크롤 변화 없음.
function scrollGroupToTop(el) {
  const sc = scrollEl.value
  if (!sc || !el) return
  const stickyTop = parseFloat(getComputedStyle(el).top) || 0
  // 흐름상 누적 위치(offsetParent 무관)
  let top = 0, node = el
  while (node && node !== sc && sc.contains(node)) {
    top += node.offsetTop
    const op = node.offsetParent
    if (!op || op === node) break
    node = op
  }
  // 현재 이 헤더가 상단 스티키 위치에 붙어있는가? (흐름상 위치가 스크롤+오프셋보다 위면 붙은 상태)
  const isStuck = (top - sc.scrollTop) <= stickyTop + 1
  if (!isStuck) return
  const target = Math.max(0, top + el.offsetHeight - stickyTop)
  // 카드 제거(즉시 collapse) 이후 프레임에 적용 → 앵커링/잔상 영향 없이 다음 그룹이 상단에 옴
  nextTick(() => requestAnimationFrame(() => { if (scrollEl.value) scrollEl.value.scrollTop = target }))
}

function clearFilters() {
  selectedRegions.value = []
  selectedSigungus.value = []
  selectedCats.value = []
  searchQuery.value = ''
  loadAttractions()
  toast.show('필터가 초기화됐어요')
}

async function selectAttraction(a, { keepStack = false, keepDetailOpen = false } = {}) {
  // 목록/마커에서의 새 선택은 드릴다운 이력을 초기화 (주변 드릴다운/뒤로가기는 keepStack)
  if (!keepStack) detailStack.value = []
  if (selectedAttraction.value?.id === a.id) {
    if (detailOpen.value) closeDetail()
    else closePin()
    return
  }
  clearNearbyMarker()
  infoWindow?.close()
  // keepDetailOpen(뒤로가기): 패널을 껐다 켜지 않아 좌측 슬라이드 애니메이션 없이 즉시 전환
  if (!keepDetailOpen) {
    detailSlideName.value = ''
    detailOpen.value = false
    selectedAttractionDetail.value = null
  }
  currentImageIdx.value = 0
  lightboxOpen.value = false
  selectedAttraction.value = a
  if (!keepDetailOpen) nextTick(() => { detailSlideName.value = 'detail-slide' })

  if (naverMap && a.latitude && a.longitude) {
    const latlng = new naver.maps.LatLng(Number(a.latitude), Number(a.longitude))
    const targetZoom = Math.max(naverMap.getZoom(), 13)
    const cur = naverMap.getCenter()
    const needsMove = naverMap.getZoom() < 13
      || Math.abs(cur.lat() - Number(a.latitude)) > 0.0001
      || Math.abs(cur.lng() - Number(a.longitude)) > 0.0001
    naverMap.morph(latlng, targetZoom)
    updateSelectedMarker(a)
    if (needsMove) {
      naver.maps.Event.once(naverMap, 'idle', () => {
        if (selectedAttraction.value?.id === a.id && !detailOpen.value) showAttractionInfoWindow(a)
      })
    } else {
      showAttractionInfoWindow(a)
    }
  } else {
    updateSelectedMarker(a)
    showAttractionInfoWindow(a)
  }
}

function showAttractionInfoWindow(a) {
  if (!naverMap || !a?.latitude || !a?.longitude) return
  const color = catColor(a.category)
  window.__naverOpenDetail = openDetail
  infoWindow.setContent(`<div style="padding:10px 12px;font-family:inherit;min-width:160px;max-width:220px"><div style="font-size:11px;color:${color};font-weight:700;margin-bottom:3px">${a.category}</div><div style="font-size:13px;font-weight:600;color:#1a1a2e;margin-bottom:8px;line-height:1.3">${a.title}</div><button onclick="window.__naverOpenDetail()" style="width:100%;padding:6px 0;background:${color};color:#fff;border:none;border-radius:6px;font-size:12px;font-weight:600;cursor:pointer">상세정보 보기 →</button></div>`)
  infoWindow.open(naverMap, new naver.maps.LatLng(Number(a.latitude), Number(a.longitude)))
}

async function openDetail() {
  if (!selectedAttraction.value) return
  detailOpen.value = true
  infoWindow?.close()
  currentImageIdx.value = 0
  lightboxOpen.value = false
  const id = selectedAttraction.value.id
  if (detailCache[id]) {
    // 캐시 적중(뒤로가기 등): 재조회 없이 즉시 표시
    selectedAttractionDetail.value = detailCache[id]
    detailLoading.value = false
  } else {
    detailLoading.value = true
    selectedAttractionDetail.value = null
    try {
      const d = await fetchAttractionDetail(id)
      detailCache[id] = d
      selectedAttractionDetail.value = d
    } catch { /* 기본 정보로 대체 */ } finally {
      detailLoading.value = false
    }
  }
  // 뒤로가기로 돌아온 경우 저장된 스크롤 위치 복원(없으면 최상단)
  await nextTick()
  if (detailScrollEl.value) {
    detailScrollEl.value.scrollTop = scrollPositions.value[selectedAttraction.value?.id] ?? 0
  }
}

function closeDetail() {
  detailOpen.value = false
  detailStack.value = []
  selectedAttractionDetail.value = null
  currentImageIdx.value = 0
  lightboxOpen.value = false
  showAttractionInfoWindow(selectedAttraction.value)
}

function closePin() {
  selectedAttraction.value = null
  selectedAttractionDetail.value = null
  currentImageIdx.value = 0
  lightboxOpen.value = false
  detailOpen.value = false
  detailStack.value = []
  clearSelectedMarker()
  clearNearbyMarker()
  infoWindow?.close()
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
    trayOpen.value = true   // 담으면 보관함 자동으로 열어 "이런 기능이 있다" 알림
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
  scrollEl.value?.addEventListener('scroll', checkScroll)
  scrollEl.value?.addEventListener('scroll', onScrollCheck)
  document.addEventListener('click', onTripMenuOutside, true)
  document.addEventListener('keydown', onTripMenuKey)

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
  scrollEl.value?.removeEventListener('scroll', onScrollCheck)
  document.removeEventListener('click', onTripMenuOutside, true)
  document.removeEventListener('keydown', onTripMenuKey)
  if (scrollRafId) cancelAnimationFrame(scrollRafId)
  clearTimeout(checkTimer)
})
</script>
