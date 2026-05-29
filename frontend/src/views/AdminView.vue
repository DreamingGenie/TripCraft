<template>
  <div class="admin-wrap">
    <h1 class="admin-title">관리자 패널</h1>

    <!-- TourAPI 수집 -->
    <section class="admin-card">
      <h2>TourAPI 관광지 수집</h2>
      <p class="desc">한국관광공사 TourAPI 4.0에서 전국 관광지 데이터를 수집해 DB에 저장합니다.</p>

      <div class="btn-row">
        <button class="btn-primary" :disabled="loading" @click="syncAll">
          {{ loading && mode === 'all' ? '수집 중...' : '전체 수집' }}
        </button>
        <span class="hint">전국 17개 지역 × 6개 콘텐츠 타입 (3~10분 소요)</span>
      </div>

      <div class="partial-row">
        <select v-model="areaCode">
          <option v-for="a in areas" :key="a.code" :value="a.code">{{ a.name }}</option>
        </select>
        <select v-model="contentTypeId">
          <option v-for="c in contentTypes" :key="c.code" :value="c.code">{{ c.name }}</option>
        </select>
        <button class="btn-outline" :disabled="loading" @click="syncPartial">
          {{ loading && mode === 'partial' ? '수집 중...' : '부분 수집' }}
        </button>
      </div>

      <!-- 결과 -->
      <div v-if="result" class="result-box" :class="result.ok ? 'ok' : 'err'">
        <template v-if="result.ok">
          수집 완료 — <strong>{{ result.total.toLocaleString() }}건</strong> 저장
          ({{ (result.elapsedMs / 1000).toFixed(1) }}초)
        </template>
        <template v-else>
          오류: {{ result.message }}
        </template>
      </div>

      <!-- 진행 중 표시 -->
      <div v-if="loading" class="progress-hint">
        <span class="dot-anim">●</span> 백엔드 로그에서 진행 상황을 확인할 수 있습니다.
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const loading = ref(false)
const mode = ref('')
const result = ref(null)

const areaCode = ref(1)
const contentTypeId = ref(12)

const areas = [
  { code: 1, name: '서울' }, { code: 2, name: '인천' }, { code: 3, name: '대전' },
  { code: 4, name: '대구' }, { code: 5, name: '광주' }, { code: 6, name: '부산' },
  { code: 7, name: '울산' }, { code: 8, name: '세종' }, { code: 31, name: '경기' },
  { code: 32, name: '강원' }, { code: 33, name: '충북' }, { code: 34, name: '충남' },
  { code: 35, name: '경북' }, { code: 36, name: '경남' }, { code: 37, name: '전북' },
  { code: 38, name: '전남' }, { code: 39, name: '제주' },
]
const contentTypes = [
  { code: 12, name: '관광지' }, { code: 14, name: '문화시설' }, { code: 28, name: '레포츠' },
  { code: 32, name: '숙박' },   { code: 38, name: '쇼핑' },    { code: 39, name: '음식점' },
]

async function call(url) {
  result.value = null
  loading.value = true
  try {
    const res = await fetch(url, { method: 'POST', credentials: 'include' })
    const json = await res.json().catch(() => null)
    if (res.ok && json?.success) {
      result.value = { ok: true, ...json.data }
    } else {
      result.value = { ok: false, message: json?.message || `HTTP ${res.status}` }
    }
  } catch (e) {
    result.value = { ok: false, message: e.message }
  } finally {
    loading.value = false
    mode.value = ''
  }
}

function syncAll() {
  mode.value = 'all'
  call('/api/admin/attractions/sync')
}

function syncPartial() {
  mode.value = 'partial'
  call(`/api/admin/attractions/sync/partial?areaCode=${areaCode.value}&contentTypeId=${contentTypeId.value}`)
}
</script>

<style scoped>
.admin-wrap {
  max-width: 720px;
  margin: 40px auto;
  padding: 0 20px;
}

/* 헤더: TripCraft purple-900 */
.admin-title {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 28px;
  color: var(--purple-900);
  letter-spacing: -0.02em;
}

/* 카드: TripCraft 토큰 + left accent */
.admin-card {
  background: var(--bg-surface);
  border: 0.5px solid var(--gray-border);
  border-left: 4px solid var(--purple-900);
  border-radius: var(--radius-xl);
  padding: 28px;
  box-shadow: 0 2px 8px rgba(0,0,0,.06);
}

.admin-card h2 {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--text-primary);
}

.desc {
  color: var(--gray-muted);
  font-size: var(--font-size-md);
  margin-bottom: 20px;
  line-height: 1.6;
}

.btn-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
}

.hint {
  font-size: var(--font-size-sm);
  color: var(--gray-muted);
}

.partial-row {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

/* select: TripCraft 토큰 */
.partial-row select {
  padding: 7px 10px;
  border: 0.5px solid var(--gray-border);
  border-radius: var(--radius-md);
  font-size: var(--font-size-md);
  font-family: inherit;
  background: var(--bg-surface);
  color: var(--text-primary);
  outline: none;
  cursor: pointer;
  transition: border-color .12s;
}
.partial-row select:hover { border-color: var(--gray-dark); }
.partial-row select:focus { border-color: var(--purple-900); box-shadow: 0 0 0 3px rgba(83,74,183,.08); }

/* btn-primary: purple-900 */
.btn-primary {
  padding: 8px 20px;
  background: var(--purple-900);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  font-size: var(--font-size-md);
  font-family: inherit;
  cursor: pointer;
  white-space: nowrap;
  transition: opacity .12s, transform .1s;
}
.btn-primary:hover { opacity: 0.88; transform: translateY(-1px); }
.btn-primary:disabled { opacity: .5; cursor: not-allowed; transform: none; }

/* btn-outline: purple-900 border + text */
.btn-outline {
  padding: 7px 16px;
  background: var(--bg-surface);
  color: var(--purple-900);
  border: 0.5px solid var(--purple-900);
  border-radius: var(--radius-md);
  font-size: var(--font-size-md);
  font-family: inherit;
  cursor: pointer;
  transition: background .12s, transform .1s;
}
.btn-outline:hover { background: var(--purple-50); transform: translateY(-1px); }
.btn-outline:disabled { opacity: .5; cursor: not-allowed; transform: none; }

/* 결과 박스: ok → teal, err → red 유지 */
.result-box {
  margin-top: 18px;
  padding: 12px 16px;
  border-radius: var(--radius-lg);
  font-size: var(--font-size-md);
  line-height: 1.5;
}
.result-box.ok {
  background: var(--teal-50);
  color: var(--teal-600);
  border: 0.5px solid var(--teal-100);
}
.result-box.err {
  background: #fef2f2;
  color: #991b1b;
  border: 0.5px solid #fecaca;
}

.progress-hint {
  margin-top: 12px;
  font-size: var(--font-size-sm);
  color: var(--gray-muted);
  display: flex;
  align-items: center;
  gap: 6px;
}

/* dot-anim: purple-900 */
.dot-anim {
  display: inline-block;
  animation: pulse 1s infinite;
  color: var(--purple-900);
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50%       { opacity: .3; }
}
</style>
