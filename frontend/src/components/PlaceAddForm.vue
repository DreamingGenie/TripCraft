<template>
  <div class="place-form">
    <div class="pf-search">
      <input v-model="query" class="pf-input" placeholder="장소·주소 검색 (예: 신라호텔)"
             @keydown.enter.prevent="doSearch" />
      <button class="pf-search-btn" :disabled="searching" @click="doSearch">{{ searching ? '검색…' : '검색' }}</button>
    </div>

    <ul v-if="results.length" class="pf-results">
      <li v-for="(r, i) in results" :key="i" class="pf-result" :class="{ sel: picked === r }" @click="pick(r)">
        <span class="pf-name">{{ r.name }}</span>
        <span class="pf-addr">{{ r.address }}</span>
      </li>
    </ul>
    <p v-else-if="searched && !searching" class="pf-empty">검색 결과가 없어요.</p>

    <div class="pf-fields">
      <input v-model="name" class="pf-input" placeholder="장소 이름" />
      <select v-model="category" class="pf-input">
        <option v-for="c in CATEGORIES" :key="c" :value="c">{{ c }}</option>
      </select>
      <p v-if="address" class="pf-addr-line">📍 {{ address }}</p>
    </div>

    <slot name="extra" />

    <button class="pf-submit" :disabled="!canSubmit" @click="submit">{{ submitLabel }}</button>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { placeApi } from '@/api/place'
import { useToastStore } from '@/stores/toast'

defineProps({ submitLabel: { type: String, default: '추가' } })
const emit = defineEmits(['submit'])
const toast = useToastStore()

const CATEGORIES = ['관광지', '문화시설', '레포츠', '숙박', '쇼핑', '음식점']
const query = ref('')
const results = ref([])
const searching = ref(false)
const searched = ref(false)
const picked = ref(null)
const name = ref('')
const category = ref('숙박')
const address = ref('')
const lat = ref(null)
const lng = ref(null)

async function doSearch() {
  if (!query.value.trim()) return
  searching.value = true; searched.value = true
  try { results.value = (await placeApi.search(query.value)) || [] }
  catch { results.value = []; toast.show('검색에 실패했어요') }
  finally { searching.value = false }
}
function pick(r) {
  picked.value = r
  name.value = r.name
  category.value = r.category || '관광지'
  address.value = r.address || ''
  lat.value = r.latitude
  lng.value = r.longitude
}
const canSubmit = computed(() => !!name.value.trim())
function submit() {
  if (!canSubmit.value) return
  emit('submit', {
    name: name.value.trim(), category: category.value, address: address.value,
    latitude: lat.value, longitude: lng.value,
  })
}
function reset() {
  query.value = ''; results.value = []; searched.value = false; picked.value = null
  name.value = ''; address.value = ''; lat.value = null; lng.value = null
}
defineExpose({ reset })
</script>

<style scoped>
.place-form { display: flex; flex-direction: column; gap: 10px; }
.pf-search { display: flex; gap: 8px; }
.pf-input {
  flex: 1; min-width: 0; box-sizing: border-box; padding: 9px 12px;
  border: 1px solid var(--gray-border); border-radius: var(--radius-md);
  font-family: inherit; font-size: var(--text-sm); color: var(--text-primary); outline: none;
}
.pf-input:focus { border-color: var(--purple-900); }
.pf-search-btn {
  flex-shrink: 0; border: none; cursor: pointer; font-family: inherit; font-weight: 650;
  font-size: var(--text-sm); color: #fff; background: var(--purple-900);
  padding: 0 16px; border-radius: var(--radius-md);
}
.pf-results {
  list-style: none; margin: 0; padding: 4px; max-height: 200px; overflow-y: auto;
  border: 1px solid var(--gray-border); border-radius: var(--radius-md); background: var(--bg-page);
}
.pf-result { display: flex; flex-direction: column; gap: 2px; padding: 8px 10px; border-radius: var(--radius-sm); cursor: pointer; }
.pf-result:hover, .pf-result.sel { background: var(--purple-50); }
.pf-name { font-size: var(--text-sm); font-weight: 600; color: var(--text-primary); }
.pf-addr { font-size: var(--text-xs); color: var(--gray-muted); }
.pf-empty { font-size: var(--text-sm); color: var(--gray-muted); text-align: center; padding: 8px 0; margin: 0; }
.pf-fields { display: flex; flex-direction: column; gap: 8px; }
.pf-addr-line { margin: 0; font-size: var(--text-xs); color: var(--gray-muted); }
.pf-submit {
  border: none; cursor: pointer; font-family: inherit; font-weight: 700; font-size: var(--text-base);
  color: #fff; background: var(--purple-900); padding: 10px; border-radius: var(--radius-md);
}
.pf-submit:disabled { opacity: .45; cursor: default; }
</style>
