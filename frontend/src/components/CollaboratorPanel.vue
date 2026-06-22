<template>
  <div class="collab-panel">
    <h3 class="collab-panel-title">협업자 관리</h3>

    <!-- 초대 (OWNER만) -->
    <div v-if="isOwner" class="collab-invite">
      <input
        v-model="searchQuery"
        class="collab-search-input"
        placeholder="닉네임 또는 이메일 검색"
        @input="onSearch"
      />
      <ul v-if="searchResults.length" class="collab-search-results">
        <li v-for="m in searchResults" :key="m.id" class="collab-search-item"
            @click="invite(m)">
          <span class="collab-search-name">{{ m.nickname }}</span>
          <span class="collab-search-email">{{ m.email }}</span>
        </li>
      </ul>
    </div>

    <!-- 협업자 목록 -->
    <ul class="collab-list">
      <li class="collab-item owner-item">
        <span class="collab-avatar">👑</span>
        <span class="collab-name">{{ ownerLabel }}</span>
        <span class="collab-role-badge owner">OWNER</span>
      </li>
      <li v-for="c in collaborators" :key="c.memberId" class="collab-item">
        <span class="collab-avatar">👤</span>
        <span class="collab-name">{{ c.nickname }}</span>
        <span class="collab-role-badge" :class="c.role.toLowerCase()">{{ c.role }}</span>
        <button v-if="isOwner" class="collab-remove-btn" @click="remove(c.memberId)">✕</button>
      </li>
      <li v-if="!collaborators.length" class="collab-empty">협업자가 없습니다.</li>
    </ul>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { tripApi } from '@/api/trip'
import { http } from '@/api/http'

const props = defineProps({
  tripId: { type: Number, required: true },
  isOwner: { type: Boolean, default: false },
  ownerLabel: { type: String, default: '일정 소유자' },
})

const collaborators = ref([])
const searchQuery = ref('')
const searchResults = ref([])

let searchTimer = null

async function loadCollaborators() {
  try {
    collaborators.value = await tripApi.getCollaborators(props.tripId)
  } catch {
    collaborators.value = []
  }
}

function onSearch() {
  clearTimeout(searchTimer)
  if (!searchQuery.value.trim()) { searchResults.value = []; return }
  searchTimer = setTimeout(async () => {
    try {
      const res = await http.get(`/api/members/search?q=${encodeURIComponent(searchQuery.value)}`)
      searchResults.value = res
    } catch {
      searchResults.value = []
    }
  }, 300)
}

async function invite(member) {
  try {
    await tripApi.inviteCollaborator(props.tripId, member.id, 'EDITOR')
    searchQuery.value = ''
    searchResults.value = []
    await loadCollaborators()
  } catch (e) {
    alert(e?.message || '초대에 실패했습니다.')
  }
}

async function remove(memberId) {
  if (!confirm('협업자를 제거하시겠습니까?')) return
  try {
    await tripApi.removeCollaborator(props.tripId, memberId)
    await loadCollaborators()
  } catch {
    alert('제거에 실패했습니다.')
  }
}

onMounted(loadCollaborators)
</script>

<style scoped>
.collab-panel { display: flex; flex-direction: column; gap: 12px; padding: 16px; }
.collab-panel-title { font-size: 15px; font-weight: 600; margin: 0; }

.collab-invite { position: relative; }
.collab-search-input {
  width: 100%; box-sizing: border-box; padding: 8px 12px;
  border: 1px solid #ddd; border-radius: 8px; font-size: 13px;
}
.collab-search-results {
  position: absolute; top: 100%; left: 0; right: 0; z-index: 100;
  background: #fff; border: 1px solid #ddd; border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,.12); list-style: none; margin: 4px 0; padding: 4px 0;
}
.collab-search-item {
  display: flex; align-items: center; gap: 8px; padding: 8px 12px;
  cursor: pointer; font-size: 13px;
}
.collab-search-item:hover { background: #f5f5f5; }
.collab-search-email { color: #888; font-size: 12px; }

.collab-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
.collab-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; background: #f9f9f9; border-radius: 8px; font-size: 13px;
}
.collab-avatar { font-size: 16px; }
.collab-name { flex: 1; font-weight: 500; }
.collab-role-badge {
  font-size: 11px; font-weight: 600; padding: 2px 7px; border-radius: 4px;
}
.collab-role-badge.owner { background: #fff3cd; color: #856404; }
.collab-role-badge.editor { background: #d1ecf1; color: #0c5460; }
.collab-role-badge.viewer { background: #e2e3e5; color: #383d41; }
.collab-remove-btn {
  background: none; border: none; cursor: pointer; color: #999;
  font-size: 12px; padding: 2px 6px; border-radius: 4px;
}
.collab-remove-btn:hover { background: #fee2e2; color: #dc2626; }
.collab-empty { color: #aaa; font-size: 13px; text-align: center; padding: 8px 0; }
</style>
