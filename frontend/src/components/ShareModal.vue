<template>
  <div class="share-backdrop" @click.self="$emit('close')">
    <div class="share-modal" role="dialog" aria-label="공유">
      <header class="share-head">
        <h3 class="share-title">공유</h3>
        <button class="share-x" @click="$emit('close')" aria-label="닫기">✕</button>
      </header>

      <!-- 링크 접근 설정 + 공유 URL (랜덤 토큰). 접근 레벨은 서버에서 검증 -->
      <div class="share-access">
        <div class="share-access-row">
          <span class="share-link-ico" aria-hidden="true">🔗</span>
          <span class="share-access-label">링크 접근</span>
          <template v-if="isOwner">
            <select class="share-access-select" v-model="access">
              <option value="PRIVATE">비공개</option>
              <option value="VIEW">링크가 있는 사용자: 조회</option>
              <option value="EDIT">링크가 있는 사용자: 편집</option>
            </select>
            <button class="share-access-save" :disabled="!dirty || saving" @click="save">
              {{ saving ? '저장 중…' : '저장' }}
            </button>
          </template>
          <span v-else class="share-access-static">{{ accessLabel }}</span>
        </div>
        <div v-if="shareUrl" class="share-link-row">
          <input class="share-link-input" :value="shareUrl" readonly @focus="$event.target.select()" />
          <button class="share-link-copy" @click="copyLink">{{ copied ? '복사됨' : '링크 복사' }}</button>
        </div>
        <p v-else class="share-access-hint">{{ dirty ? '저장하면 공유 링크가 생성돼요.' : '비공개 — 소유자와 초대된 사용자만 접근할 수 있어요.' }}</p>
      </div>

      <!-- 사용자 검색·추가 (소유자만) -->
      <div v-if="isOwner" class="share-invite">
        <input
          v-model="searchQuery"
          class="share-search-input"
          placeholder="닉네임 또는 이메일로 사용자 추가"
          @input="onSearch"
        />
        <ul v-if="searchResults.length" class="share-search-results">
          <li v-for="m in searchResults" :key="m.id" class="share-search-item" @click="invite(m)">
            <span class="share-search-name">{{ m.nickname }}</span>
            <span class="share-search-email">{{ m.email }}</span>
          </li>
        </ul>
      </div>

      <!-- 편집 권한 사용자 리스트 -->
      <div class="share-people">
        <p class="share-section-label">편집 권한</p>
        <ul class="share-list">
          <li class="share-item">
            <span class="share-avatar owner">{{ ownerLabel?.charAt(0)?.toUpperCase() ?? '?' }}</span>
            <span class="share-name">{{ ownerLabel }} <span class="share-me">(소유자)</span></span>
            <span class="share-role owner">OWNER</span>
          </li>
          <li v-for="c in collaborators" :key="c.memberId" class="share-item">
            <span class="share-avatar" :style="{ background: onlineColor(c.memberId) || 'var(--gray-border)' }">
              <img v-if="c.profileImageUrl" :src="c.profileImageUrl" class="share-avatar-img" alt="" />
              <template v-else>{{ c.nickname?.charAt(0)?.toUpperCase() ?? '?' }}</template>
            </span>
            <span class="share-name">{{ c.nickname }}</span>
            <span class="share-role editor">{{ c.role }}</span>
            <template v-if="isOwner">
              <template v-if="confirmRemoveId === c.memberId">
                <button class="share-confirm yes" @click="confirmRemove(c.memberId)">삭제</button>
                <button class="share-confirm no" @click="confirmRemoveId = null">취소</button>
              </template>
              <button v-else class="share-remove" @click="confirmRemoveId = c.memberId" aria-label="제거">✕</button>
            </template>
          </li>
          <li v-if="!collaborators.length" class="share-empty">아직 추가된 사용자가 없어요.</li>
        </ul>
      </div>

      <!-- 여행 이야기 게시 -->
      <footer class="share-foot">
        <button class="share-publish" @click="$emit('publish')">📢 여행 이야기에 게시</button>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { tripApi } from '@/api/trip'
import { http } from '@/api/http'
import { useToastStore } from '@/stores/toast'

const props = defineProps({
  tripId: { type: Number, required: true },
  isOwner: { type: Boolean, default: false },
  ownerLabel: { type: String, default: '소유자' },
  participants: { type: Array, default: () => [] },
  colorMap: { type: Object, default: () => ({}) },
  shareAccess: { type: String, default: 'PRIVATE' },
  shareToken: { type: String, default: '' },
})
const emit = defineEmits(['close', 'publish', 'update'])

const toast = useToastStore()

// ── 링크 접근 설정 + 공유 URL ──
const access = ref(props.shareAccess || 'PRIVATE')        // 셀렉트 선택값(미저장 포함)
const savedAccess = ref(props.shareAccess || 'PRIVATE')   // 현재 저장된 상태
const token = ref(props.shareToken || '')
const saving = ref(false)
const ACCESS_LABEL = { PRIVATE: '비공개', VIEW: '링크 조회', EDIT: '링크 편집' }
const accessLabel = computed(() => ACCESS_LABEL[savedAccess.value] || '비공개')
const dirty = computed(() => access.value !== savedAccess.value)
const shareUrl = computed(() =>
  savedAccess.value !== 'PRIVATE' && token.value
    ? `${window.location.origin}/plan/${props.tripId}?s=${token.value}` : '')
async function save() {
  if (!dirty.value || saving.value) return
  saving.value = true
  try {
    const res = await tripApi.setShareAccess(props.tripId, access.value)
    savedAccess.value = access.value
    if (res?.token) token.value = res.token
    emit('update', { access: savedAccess.value, token: token.value })  // 부모 activeTripDetail 갱신
    toast.show('공유 설정을 저장했어요')
  } catch {
    access.value = savedAccess.value
    toast.show('저장에 실패했어요')
  } finally {
    saving.value = false
  }
}
const copied = ref(false)
async function copyLink() {
  if (!shareUrl.value) return
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    copied.value = true
    setTimeout(() => (copied.value = false), 1500)
  } catch {
    toast.show('링크 복사에 실패했어요')
  }
}

// ── 협업자(편집 권한) 목록 ──
const collaborators = ref([])
function onlineColor(memberId) {
  return props.participants.some(p => p.memberId === memberId) ? props.colorMap[memberId] : null
}
async function loadCollaborators() {
  try { collaborators.value = await tripApi.getCollaborators(props.tripId) }
  catch { collaborators.value = [] }
}

// ── 사용자 검색·추가 ──
const searchQuery = ref('')
const searchResults = ref([])
let searchTimer = null
function onSearch() {
  clearTimeout(searchTimer)
  if (!searchQuery.value.trim()) { searchResults.value = []; return }
  searchTimer = setTimeout(async () => {
    try {
      const res = await http.get(`/api/members/search?q=${encodeURIComponent(searchQuery.value)}`)
      searchResults.value = Array.isArray(res) ? res : []
    } catch {
      toast.show('검색 중 오류가 발생했습니다.')
      searchResults.value = []
    }
  }, 300)
}
async function invite(member) {
  try {
    await tripApi.inviteCollaborator(props.tripId, member.id, 'EDITOR')
    searchQuery.value = ''
    searchResults.value = []
    toast.show(`${member.nickname}님에게 편집 권한을 부여했어요.`)
    await loadCollaborators()
  } catch (e) {
    toast.show(e?.message || '추가에 실패했습니다.')
  }
}

const confirmRemoveId = ref(null)
async function confirmRemove(memberId) {
  try {
    await tripApi.removeCollaborator(props.tripId, memberId)
    confirmRemoveId.value = null
    toast.show('편집 권한을 제거했어요.')
    await loadCollaborators()
  } catch {
    toast.show('제거에 실패했습니다.')
  }
}

onMounted(loadCollaborators)
</script>

<style scoped>
.share-backdrop {
  position: fixed; inset: 0; z-index: 100;
  background: rgba(20, 20, 30, .38);
  display: flex; align-items: center; justify-content: center;
  padding: 24px;
}
.share-modal {
  width: 100%; max-width: 440px;
  max-height: 86vh; overflow-y: auto;
  background: var(--bg-surface);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-lg, 0 16px 48px rgba(20,20,30,.24));
  padding: 20px 22px 22px;
}
.share-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.share-title { font-size: var(--text-xl); font-weight: 700; color: var(--text-primary); margin: 0; }
.share-x {
  border: none; background: none; cursor: pointer; font-size: 15px;
  color: var(--gray-muted); padding: 4px 8px; border-radius: var(--radius-md);
}
.share-x:hover { background: var(--bg-page); color: var(--text-primary); }

/* 엑세스 링크 */
.share-link-row {
  display: flex; align-items: center; gap: 8px;
  background: var(--bg-page);
  border: 1px solid var(--gray-border);
  border-radius: var(--radius-lg);
  padding: 6px 6px 6px 12px;
  margin-bottom: 16px;
}
.share-link-ico { flex-shrink: 0; font-size: 13px; }
.share-link-input {
  flex: 1; min-width: 0; border: none; background: none; outline: none;
  font-size: var(--text-sm); color: var(--gray-dark);
}
.share-link-copy {
  flex-shrink: 0; border: none; cursor: pointer;
  background: var(--purple-900); color: #fff;
  font-size: var(--text-sm); font-weight: 650;
  padding: 7px 14px; border-radius: var(--radius-md);
  transition: background .14s;
}
.share-link-copy:hover { background: var(--primary-dark); }

/* 링크 접근 설정 */
.share-access { margin-bottom: 16px; }
.share-access-row { display: flex; align-items: center; gap: 8px; }
.share-access-label { font-size: var(--text-sm); font-weight: 650; color: var(--text-primary); }
.share-access-select {
  margin-left: auto; font-family: inherit; font-size: var(--text-sm);
  color: var(--text-primary); background: var(--bg-page);
  border: 1px solid var(--gray-border); border-radius: var(--radius-md);
  padding: 6px 10px; cursor: pointer;
}
.share-access-save {
  font-family: inherit; font-size: var(--text-sm); font-weight: 650;
  color: #fff; background: var(--purple-900);
  border: none; border-radius: var(--radius-md); padding: 7px 14px; cursor: pointer;
  transition: background .14s, opacity .14s;
}
.share-access-save:hover:not(:disabled) { background: var(--primary-dark); }
.share-access-save:disabled { opacity: .45; cursor: default; }
.share-access-static { margin-left: auto; font-size: var(--text-sm); color: var(--gray-dark); }
.share-access .share-link-row { margin-top: 10px; margin-bottom: 0; }
.share-access-hint { margin: 10px 0 0; font-size: var(--text-xs); color: var(--gray-muted); }

/* 검색·추가 */
.share-invite { position: relative; margin-bottom: 16px; }
.share-search-input {
  width: 100%; box-sizing: border-box; padding: 10px 12px;
  border: 1px solid var(--gray-border); border-radius: var(--radius-lg);
  font-size: var(--text-sm); font-family: inherit; color: var(--text-primary);
  outline: none; transition: border-color .14s;
}
.share-search-input:focus { border-color: var(--purple-900); }
.share-search-results {
  position: absolute; top: calc(100% + 4px); left: 0; right: 0; z-index: 10;
  background: var(--bg-surface); border: 1px solid var(--gray-border);
  border-radius: var(--radius-lg); box-shadow: var(--shadow-md);
  list-style: none; margin: 0; padding: 4px; max-height: 220px; overflow-y: auto;
}
.share-search-item {
  display: flex; align-items: center; gap: 8px; padding: 8px 10px;
  cursor: pointer; font-size: var(--text-sm); border-radius: var(--radius-md);
}
.share-search-item:hover { background: var(--bg-page); }
.share-search-name { font-weight: 600; color: var(--text-primary); }
.share-search-email { color: var(--gray-muted); font-size: var(--text-xs); }

/* 편집 권한 리스트 */
.share-section-label {
  font-size: var(--text-xs); font-weight: 700; color: var(--gray-muted);
  letter-spacing: .04em; margin: 0 0 8px;
}
.share-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
.share-item {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 10px; background: var(--bg-page); border-radius: var(--radius-lg);
  font-size: var(--text-sm);
}
.share-avatar {
  width: 30px; height: 30px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; overflow: hidden;
  font-size: 13px; font-weight: 700; color: #fff;
  background: var(--purple-900);
}
.share-avatar.owner { background: #f59e0b; }
.share-avatar-img { width: 100%; height: 100%; object-fit: cover; }
.share-name { flex: 1; min-width: 0; font-weight: 600; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.share-me { color: var(--gray-muted); font-weight: 500; }
.share-role { font-size: var(--text-2xs); font-weight: 700; padding: 2px 7px; border-radius: var(--radius-sm); flex-shrink: 0; }
.share-role.owner { background: #fff3cd; color: #856404; }
.share-role.editor { background: var(--purple-50); color: var(--purple-900); }
.share-remove {
  border: none; background: none; cursor: pointer; color: var(--gray-muted);
  font-size: 12px; padding: 3px 7px; border-radius: var(--radius-sm); flex-shrink: 0;
}
.share-remove:hover { background: #fee2e2; color: #dc2626; }
.share-confirm { font-size: var(--text-2xs); font-weight: 650; padding: 3px 8px; border-radius: var(--radius-sm); cursor: pointer; border: 1px solid; flex-shrink: 0; }
.share-confirm.yes { background: #fee2e2; border-color: #fca5a5; color: #dc2626; }
.share-confirm.no { background: var(--bg-surface); border-color: var(--gray-border); color: var(--gray-dark); }
.share-empty { color: var(--gray-muted); font-size: var(--text-sm); text-align: center; padding: 12px 0; }

/* 게시 */
.share-foot { margin-top: 18px; padding-top: 16px; border-top: 1px solid var(--gray-border); }
.share-publish {
  width: 100%; border: 1px solid var(--gray-border); cursor: pointer;
  background: var(--bg-surface); color: var(--text-primary);
  font-family: inherit; font-size: var(--text-base); font-weight: 650;
  padding: 11px; border-radius: var(--radius-lg);
  transition: background .14s, border-color .14s, color .14s;
}
.share-publish:hover { background: var(--purple-50); border-color: var(--purple-100); color: var(--purple-900); }
</style>
