<template>
  <div class="myplaces">
    <section class="mp-card">
      <h3 class="mp-h">새 장소 추가</h3>
      <p class="mp-sub">검색해서 저장해두면 일정 보관함에서 바로 꺼내 쓸 수 있어요.</p>
      <PlaceAddForm ref="formRef" submit-label="내 장소에 저장" @submit="addPlace" />
    </section>

    <section class="mp-card">
      <h3 class="mp-h">내 장소 <span class="mp-count">{{ places.length }}</span></h3>
      <ul v-if="places.length" class="mp-list">
        <li v-for="p in places" :key="p.id" class="mp-item">
          <span class="mp-dot" :style="{ background: catColor(p.category) }"></span>
          <span class="mp-info">
            <span class="mp-name">{{ p.name }}</span>
            <span class="mp-addr">{{ p.category }}<template v-if="p.address"> · {{ p.address }}</template></span>
          </span>
          <button class="mp-del" @click="remove(p)">삭제</button>
        </li>
      </ul>
      <p v-else class="mp-empty">아직 저장한 장소가 없어요.</p>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PlaceAddForm from '@/components/PlaceAddForm.vue'
import { myPlaceApi } from '@/api/place'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const places = ref([])
const formRef = ref(null)

const CAT = {
  관광지: 'var(--cat-sights-ink)', 문화시설: 'var(--cat-culture-ink)', 레포츠: 'var(--cat-leisure-ink)',
  숙박: 'var(--cat-stay-ink)', 쇼핑: 'var(--cat-shop-ink)', 음식점: 'var(--cat-food-ink)',
}
function catColor(c) { return CAT[c] || 'var(--purple-900)' }

async function load() {
  try { places.value = (await myPlaceApi.list()) || [] } catch { places.value = [] }
}
async function addPlace(place) {
  try { await myPlaceApi.create(place); toast.show('내 장소에 저장했어요'); formRef.value?.reset(); load() }
  catch (e) { toast.show(e?.message || '저장에 실패했어요') }
}
async function remove(p) {
  try { await myPlaceApi.remove(p.id); load() } catch { toast.show('삭제에 실패했어요') }
}
onMounted(load)
</script>

<style scoped>
.myplaces { display: flex; flex-direction: column; gap: var(--space-5); }
.mp-card {
  background: var(--bg-surface); border: 1px solid var(--gray-border);
  border-radius: var(--radius-2xl); box-shadow: var(--shadow-sm); padding: 20px 22px;
}
.mp-h { margin: 0 0 4px; font-size: var(--text-lg); font-weight: 700; color: var(--text-primary); }
.mp-sub { margin: 0 0 14px; font-size: var(--text-sm); color: var(--gray-muted); }
.mp-count { font-size: var(--text-sm); color: var(--purple-900); font-weight: 700; }
.mp-list { list-style: none; margin: 12px 0 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.mp-item { display: flex; align-items: center; gap: 12px; padding: 12px 14px; background: var(--bg-page); border-radius: var(--radius-lg); }
.mp-dot { width: 9px; height: 9px; border-radius: 50%; flex-shrink: 0; }
.mp-info { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1; }
.mp-name { font-size: var(--text-base); font-weight: 650; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mp-addr { font-size: var(--text-xs); color: var(--gray-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mp-del {
  flex-shrink: 0; border: 1px solid var(--gray-border); background: var(--bg-surface);
  color: var(--gray-dark); font-family: inherit; font-size: var(--text-xs); font-weight: 600;
  padding: 6px 12px; border-radius: var(--radius-md); cursor: pointer; transition: background .12s, color .12s, border-color .12s;
}
.mp-del:hover { background: #fee2e2; color: #dc2626; border-color: #fca5a5; }
.mp-empty { margin: 12px 0 0; font-size: var(--text-sm); color: var(--gray-muted); text-align: center; padding: 12px 0; }
</style>
