<template>
  <div class="apm-backdrop" @click.self="$emit('close')">
    <div class="apm-modal" role="dialog" aria-label="장소 추가">
      <header class="apm-head">
        <h3 class="apm-title">장소 추가</h3>
        <button class="apm-x" @click="$emit('close')" aria-label="닫기">✕</button>
      </header>

      <div class="apm-tabs">
        <button class="apm-tab" :class="{ active: tab === 'my' }" @click="tab = 'my'">내 장소</button>
        <button class="apm-tab" :class="{ active: tab === 'new' }" @click="tab = 'new'">직접 추가</button>
      </div>

      <div v-if="tab === 'my'" class="apm-body">
        <ul v-if="myPlaces.length" class="apm-list">
          <li v-for="p in myPlaces" :key="p.id" class="apm-item" @click="addFromPlace(p)">
            <span class="apm-dot" :style="{ background: catColor(p.category) }"></span>
            <span class="apm-info">
              <span class="apm-name">{{ p.name }}</span>
              <span class="apm-addr">{{ p.address }}</span>
            </span>
            <span class="apm-add">+ 추가</span>
          </li>
        </ul>
        <p v-else class="apm-empty">저장된 내 장소가 없어요.<br>"직접 추가"에서 만들고 "내 장소로도 저장"하면 여기에 쌓여요.</p>
      </div>

      <div v-else class="apm-body">
        <PlaceAddForm submit-label="보관함에 추가" @submit="addCustom">
          <template #extra>
            <label class="apm-check"><input type="checkbox" v-model="saveToMyPlaces" /> 내 장소로도 저장</label>
          </template>
        </PlaceAddForm>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PlaceAddForm from './PlaceAddForm.vue'
import { myPlaceApi } from '@/api/place'
import { tripApi } from '@/api/trip'
import { useToastStore } from '@/stores/toast'

const props = defineProps({ tripId: { type: Number, required: true } })
const emit = defineEmits(['close', 'added'])
const toast = useToastStore()

const tab = ref('my')
const myPlaces = ref([])
const saveToMyPlaces = ref(false)

const CAT = {
  관광지: 'var(--cat-sights-ink)', 문화시설: 'var(--cat-culture-ink)', 레포츠: 'var(--cat-leisure-ink)',
  숙박: 'var(--cat-stay-ink)', 쇼핑: 'var(--cat-shop-ink)', 음식점: 'var(--cat-food-ink)',
}
function catColor(c) { return CAT[c] || 'var(--purple-900)' }

async function loadMyPlaces() {
  try { myPlaces.value = (await myPlaceApi.list()) || [] } catch { myPlaces.value = [] }
}
async function addFromPlace(p) {
  try { await tripApi.addCandidateFromMyPlace(props.tripId, p.id); toast.show(`${p.name} 추가됨`); emit('added') }
  catch (e) { toast.show(e?.message || '추가에 실패했어요') }
}
async function addCustom(place) {
  try {
    await tripApi.addCustomCandidate(props.tripId, { ...place, saveToMyPlaces: saveToMyPlaces.value })
    toast.show(`${place.name} 추가됨`)
    if (saveToMyPlaces.value) loadMyPlaces()
    emit('added')
  } catch (e) { toast.show(e?.message || '추가에 실패했어요') }
}
onMounted(loadMyPlaces)
</script>

<style scoped>
.apm-backdrop {
  position: fixed; inset: 0; z-index: 100; background: rgba(20,20,30,.38);
  display: flex; align-items: center; justify-content: center; padding: 24px;
}
.apm-modal {
  width: 100%; max-width: 420px; max-height: 86vh; overflow-y: auto;
  background: var(--bg-surface); border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-lg, 0 16px 48px rgba(20,20,30,.24)); padding: 18px 20px 20px;
}
.apm-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.apm-title { margin: 0; font-size: var(--text-xl); font-weight: 700; color: var(--text-primary); }
.apm-x { border: none; background: none; cursor: pointer; font-size: 15px; color: var(--gray-muted); padding: 4px 8px; border-radius: var(--radius-md); }
.apm-x:hover { background: var(--bg-page); color: var(--text-primary); }
.apm-tabs { display: flex; gap: 4px; background: var(--bg-page); border-radius: var(--radius-full); padding: 4px; margin-bottom: 14px; }
.apm-tab {
  flex: 1; border: none; background: none; cursor: pointer; font-family: inherit;
  font-size: var(--text-sm); font-weight: 650; color: var(--gray-dark); padding: 8px; border-radius: var(--radius-full);
}
.apm-tab.active { background: var(--bg-surface); color: var(--purple-900); box-shadow: var(--shadow-sm); }
.apm-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
.apm-item { display: flex; align-items: center; gap: 10px; padding: 10px 12px; background: var(--bg-page); border-radius: var(--radius-lg); cursor: pointer; }
.apm-item:hover { background: var(--purple-50); }
.apm-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.apm-info { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1; }
.apm-name { font-size: var(--text-sm); font-weight: 600; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.apm-addr { font-size: var(--text-xs); color: var(--gray-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.apm-add { flex-shrink: 0; font-size: var(--text-xs); font-weight: 700; color: var(--purple-900); }
.apm-empty { font-size: var(--text-sm); color: var(--gray-muted); text-align: center; line-height: 1.6; padding: 16px 0; margin: 0; }
.apm-check { display: flex; align-items: center; gap: 6px; font-size: var(--text-sm); color: var(--gray-dark); cursor: pointer; }
</style>
