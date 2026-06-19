<template>
  <div class="tdp-overlay" @click.self="$emit('close')">
    <div class="tdp-panel">
      <div class="tdp-header">
        <span class="tdp-title">경로 상세</span>
        <button class="tdp-close" @click="$emit('close')">✕</button>
      </div>

      <div v-if="loading" class="tdp-state">불러오는 중…</div>
      <div v-else-if="!detail" class="tdp-state">경로 정보가 없습니다</div>

      <template v-else>
        <!-- 경로 탭 (2개 이상일 때만) -->
        <div v-if="detail.intercityPaths.length > 1" class="tdp-tabs">
          <button
            v-for="(path, i) in detail.intercityPaths"
            :key="i"
            class="tdp-tab"
            :class="{ active: selectedIndex === i }"
            @click="selectedIndex = i"
          >
            <span class="tdp-tab-label">{{ pathLabel(path) }}</span>
            <span class="tdp-tab-time">{{ pathTotalMinutes(path) }}분</span>
          </button>
        </div>

        <!-- 경로 단계 -->
        <div class="tdp-steps">
          <!-- 도시간: 출발지→출발역 로컬 구간 -->
          <template v-if="isIntercity && (selectedPath?.localFrom?.minutes ?? 0) > 0">
            <template v-if="selectedPath.localFrom.subPath?.length">
              <template v-for="(sub, si) in selectedPath.localFrom.subPath" :key="`lf-${si}`">
                <div class="tdp-step" :class="subStepClass(sub.trafficType)">
                  <span class="tdp-icon">{{ modeIcon(sub.trafficType) }}</span>
                  <div class="tdp-step-body">
                    <div class="tdp-step-main">
                      <span class="tdp-step-title">{{ stepInfo(sub).title }}</span>
                      <span v-if="stepInfo(sub).route" class="tdp-step-route">{{ stepInfo(sub).route }}</span>
                    </div>
                    <span class="tdp-step-time">{{ sub.sectionTime }}분</span>
                  </div>
                </div>
                <div class="tdp-connector"></div>
              </template>
            </template>
            <template v-else>
              <div class="tdp-step tdp-step--local">
                <span class="tdp-icon">🚶</span>
                <div class="tdp-step-body">
                  <div class="tdp-step-main">
                    <span class="tdp-step-title">출발지 → 출발역/터미널</span>
                  </div>
                  <span class="tdp-step-time">
                    {{ selectedPath.localFrom.minutes }}분
                    <span v-if="selectedPath.localFrom.estimated" class="tdp-est">(추정)</span>
                  </span>
                </div>
              </div>
              <div class="tdp-connector"></div>
            </template>
          </template>

          <!-- 메인 subPath -->
          <template v-for="(sub, i) in displaySubPaths" :key="i">
            <div class="tdp-step" :class="subStepClass(sub.trafficType)">
              <span class="tdp-icon">{{ modeIcon(sub.trafficType) }}</span>
              <div class="tdp-step-body">
                <div class="tdp-step-main">
                  <span class="tdp-step-title">{{ stepInfo(sub).title }}</span>
                  <span v-if="stepInfo(sub).route" class="tdp-step-route">{{ stepInfo(sub).route }}</span>
                  <span v-if="stepInfo(sub).detail" class="tdp-step-detail">{{ stepInfo(sub).detail }}</span>
                </div>
                <span class="tdp-step-time">{{ sub.sectionTime }}분</span>
              </div>
            </div>
            <div v-if="i < displaySubPaths.length - 1" class="tdp-connector"></div>
          </template>

          <!-- 도시간: 도착역→목적지 로컬 구간 -->
          <template v-if="isIntercity && (selectedPath?.localTo?.minutes ?? 0) > 0">
            <div class="tdp-connector"></div>
            <template v-if="selectedPath.localTo.subPath?.length">
              <template v-for="(sub, si) in selectedPath.localTo.subPath" :key="`lt-${si}`">
                <div v-if="si > 0" class="tdp-connector"></div>
                <div class="tdp-step" :class="subStepClass(sub.trafficType)">
                  <span class="tdp-icon">{{ modeIcon(sub.trafficType) }}</span>
                  <div class="tdp-step-body">
                    <div class="tdp-step-main">
                      <span class="tdp-step-title">{{ stepInfo(sub).title }}</span>
                      <span v-if="stepInfo(sub).route" class="tdp-step-route">{{ stepInfo(sub).route }}</span>
                    </div>
                    <span class="tdp-step-time">{{ sub.sectionTime }}분</span>
                  </div>
                </div>
              </template>
            </template>
            <template v-else>
              <div class="tdp-step tdp-step--local">
                <span class="tdp-icon">🚶</span>
                <div class="tdp-step-body">
                  <div class="tdp-step-main">
                    <span class="tdp-step-title">도착역/터미널 → 목적지</span>
                  </div>
                  <span class="tdp-step-time">
                    {{ selectedPath.localTo.minutes }}분
                    <span v-if="selectedPath.localTo.estimated" class="tdp-est">(추정)</span>
                  </span>
                </div>
              </div>
            </template>
          </template>
        </div>

        <!-- 요약 + 저장 -->
        <div class="tdp-footer">
          <div class="tdp-summary">
            <div class="tdp-summary-item">
              <span class="tdp-summary-label">총 소요</span>
              <span class="tdp-summary-value">{{ totalMinutes }}분</span>
            </div>
            <div v-if="fare > 0" class="tdp-summary-item">
              <span class="tdp-summary-label">요금</span>
              <span class="tdp-summary-value">{{ fare.toLocaleString() }}원</span>
            </div>
            <div v-if="transferCount > 0" class="tdp-summary-item">
              <span class="tdp-summary-label">환승</span>
              <span class="tdp-summary-value">{{ transferCount }}회</span>
            </div>
          </div>
          <button class="tdp-save-btn" @click="$emit('select', selectedIndex)">
            이 경로로 저장
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  detail: { type: Object, default: null },
  loading: { type: Boolean, default: false },
})
defineEmits(['close', 'select'])

const selectedIndex = ref(0)

const selectedPath = computed(() => props.detail?.intercityPaths?.[selectedIndex.value] ?? null)
const isIntercity = computed(() => (selectedPath.value?.pathType ?? 0) >= 11)

const displaySubPaths = computed(() => {
  if (!selectedPath.value?.subPath) return []
  const subs = selectedPath.value.subPath
  return isIntercity.value ? subs.filter(s => s.trafficType !== 3) : subs
})

const totalMinutes = computed(() => {
  if (!selectedPath.value) return 0
  const info = selectedPath.value.info ?? {}
  if (!isIntercity.value) return info.totalTime ?? 0
  return (selectedPath.value.localFrom?.minutes ?? 0)
       + (info.totalTime ?? 0)
       + (selectedPath.value.localTo?.minutes ?? 0)
})

const fare = computed(() => {
  if (!selectedPath.value) return 0
  const info = selectedPath.value.info ?? {}
  return info.totalPayment ?? info.payment ?? 0
})

const transferCount = computed(() => {
  if (!selectedPath.value) return 0
  const info = selectedPath.value.info ?? {}
  if (info.transitCount != null) return Math.max(0, info.transitCount - 1)
  return (info.busTransitCount ?? 0) + (info.subwayTransitCount ?? 0)
})

const TRAIN_TYPE = { 1:'KTX', 2:'새마을호', 3:'무궁화호', 4:'누리로', 5:'통근열차', 6:'ITX-새마을', 7:'ITX-청춘', 8:'SRT' }
const PATH_TYPE_LABEL = { 1:'지하철', 2:'버스', 3:'버스+지하철', 11:'열차', 12:'고속/시외버스', 13:'항공', 20:'복합' }

function pathLabel(path) {
  return PATH_TYPE_LABEL[path.pathType] ?? '기타'
}

function pathTotalMinutes(path) {
  const info = path.info ?? {}
  const intercity = info.totalTime ?? 0
  if ((path.pathType ?? 0) < 11) return intercity
  return (path.localFrom?.minutes ?? 0) + intercity + (path.localTo?.minutes ?? 0)
}

function modeIcon(type) {
  return { 1:'🚇', 2:'🚌', 3:'🚶', 4:'🚅', 5:'🚌', 6:'🚌', 7:'✈️' }[type] ?? '🚌'
}

function subStepClass(type) {
  if (type === 3) return 'tdp-step--walk'
  if (type === 1) return 'tdp-step--subway'
  if (type === 4) return 'tdp-step--rail'
  return 'tdp-step--bus'
}

function stepInfo(sub) {
  const t = sub.trafficType
  if (t === 3) return { title: '도보', detail: sub.distance ? `${sub.distance}m` : null, route: null }
  if (t === 1) {
    const lineName = sub.lane?.[0]?.name ?? '지하철'
    const parts = []
    if (sub.way) parts.push(`${sub.way} 방면`)
    if (sub.stationCount) parts.push(`${sub.stationCount}개 역`)
    return { title: lineName, route: `${sub.startName} → ${sub.endName}`, detail: parts.join(' · ') || null }
  }
  if (t === 2) {
    const busNo = sub.lane?.[0]?.busNo || sub.lane?.[0]?.busNoGov || ''
    return {
      title: `버스${busNo ? ' ' + busNo : ''}`,
      route: `${sub.startName} → ${sub.endName}`,
      detail: sub.stationCount ? `${sub.stationCount}개 정류장` : null,
    }
  }
  if (t === 4) {
    return {
      title: TRAIN_TYPE[sub.trainType] ?? '열차',
      route: `${sub.startName} → ${sub.endName}`,
      detail: sub.payment ? `${sub.payment.toLocaleString()}원` : null,
    }
  }
  const label = t === 5 ? '고속버스' : t === 6 ? '시외버스' : t === 7 ? '항공' : '버스'
  return {
    title: label,
    route: `${sub.startName} → ${sub.endName}`,
    detail: sub.payment ? `${sub.payment.toLocaleString()}원` : null,
  }
}
</script>

<style scoped>
.tdp-overlay {
  position: fixed; inset: 0;
  background: rgba(0,0,0,.35);
  display: flex; align-items: center; justify-content: center;
  z-index: 300;
}

.tdp-panel {
  background: #fff; border-radius: 14px;
  width: 380px; max-height: 82vh;
  display: flex; flex-direction: column; overflow: hidden;
  box-shadow: 0 8px 32px rgba(0,0,0,.18);
}

.tdp-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px 12px;
  border-bottom: 1px solid #f0f0f0;
}
.tdp-title { font-weight: 700; font-size: 15px; color: #222; }
.tdp-close { background: none; border: none; font-size: 16px; color: #999; cursor: pointer; padding: 2px 4px; }
.tdp-close:hover { color: #333; }

.tdp-state { padding: 32px 20px; text-align: center; color: #999; font-size: 13px; }

/* 경로 탭 */
.tdp-tabs {
  display: flex; gap: 6px;
  padding: 10px 16px 0;
  overflow-x: auto;
  flex-shrink: 0;
}
.tdp-tab {
  display: flex; flex-direction: column; align-items: center;
  gap: 2px; padding: 6px 12px;
  border: 1px solid #e0e0e0; border-radius: 8px;
  background: #fafafa; cursor: pointer;
  white-space: nowrap; font-size: 12px; color: #666;
  transition: all .15s;
}
.tdp-tab.active {
  border-color: #534AB7; background: #f0eeff; color: #534AB7;
}
.tdp-tab:hover:not(.active) { background: #f5f5f5; }
.tdp-tab-label { font-weight: 600; }
.tdp-tab-time { font-size: 11px; }

.tdp-steps {
  padding: 12px 16px 4px;
  overflow-y: auto; flex: 1;
}

.tdp-step {
  display: flex; align-items: flex-start;
  gap: 10px; padding: 8px 10px; border-radius: 8px;
  background: #f8f8f8;
}
.tdp-step--walk   { background: #f5f5f5; }
.tdp-step--subway { background: #eef3ff; }
.tdp-step--bus    { background: #fff8ec; }
.tdp-step--rail   { background: #f0faf4; }
.tdp-step--local  { background: #fdf5ff; }

.tdp-icon { font-size: 18px; line-height: 1.5; flex-shrink: 0; }

.tdp-step-body {
  flex: 1; display: flex; justify-content: space-between;
  align-items: flex-start; gap: 8px; min-width: 0;
}
.tdp-step-main { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1; }
.tdp-step-title  { font-size: 13px; font-weight: 600; color: #222; }
.tdp-step-route  { font-size: 12px; color: #555; }
.tdp-step-detail { font-size: 11px; color: #888; }
.tdp-step-time   { font-size: 13px; font-weight: 700; color: #534AB7; white-space: nowrap; flex-shrink: 0; padding-top: 1px; }
.tdp-est { font-weight: 400; color: #999; font-size: 11px; }

.tdp-connector {
  width: 2px; height: 12px; background: #ddd;
  margin: 2px 0 2px 19px;
}

/* 푸터: 요약 + 저장 버튼 */
.tdp-footer {
  border-top: 1px solid #f0f0f0;
  padding: 12px 16px;
  display: flex; flex-direction: column; gap: 10px;
  flex-shrink: 0;
}

.tdp-summary { display: flex; }
.tdp-summary-item {
  display: flex; flex-direction: column; align-items: center; flex: 1;
}
.tdp-summary-item + .tdp-summary-item { border-left: 1px solid #f0f0f0; }
.tdp-summary-label { font-size: 11px; color: #999; margin-bottom: 2px; }
.tdp-summary-value { font-size: 14px; font-weight: 700; color: #222; }

.tdp-save-btn {
  width: 100%; padding: 10px;
  background: #534AB7; color: #fff;
  border: none; border-radius: 8px;
  font-size: 13px; font-weight: 600;
  cursor: pointer; transition: background .15s;
}
.tdp-save-btn:hover { background: #4339a0; }
.tdp-save-btn:disabled { background: #bbb; cursor: default; }
</style>
