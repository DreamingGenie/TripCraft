<template>
  <div class="ac-wrap">
    <div class="detail-section-divider"></div>

    <!-- 헤더 / 토글 -->
    <button class="ac-header" @click="open = !open">
      <span class="ac-header-title">
        <svg class="ac-spark" viewBox="0 0 24 24" width="16" height="16" fill="none"
             stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M12 3v4M12 17v4M3 12h4M17 12h4" />
          <path d="m6.3 6.3 2.4 2.4M15.3 15.3l2.4 2.4M17.7 6.3l-2.4 2.4M8.7 15.3l-2.4 2.4" />
        </svg>
        AI에게 이 관광지 물어보기
      </span>
      <svg class="ac-chevron" :class="{ 'ac-chevron--open': open }" viewBox="0 0 24 24"
           width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"
           stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="m6 9 6 6 6-6" />
      </svg>
    </button>

    <Transition name="ac-collapse">
      <div v-if="open" class="ac-body">
        <!-- 대화 영역 -->
        <div ref="scrollEl" class="ac-messages">
          <p v-if="!messages.length" class="ac-empty">
            "{{ attractionTitle }}"에 대해 궁금한 점을 물어보세요.
          </p>

          <div v-for="(m, i) in messages" :key="i" class="ac-row" :class="`ac-row--${m.role}`">
            <div class="ac-msg" :class="`ac-msg--${m.role}`">
              <div class="ac-bubble">{{ m.content }}</div>
            </div>
            <!-- 답변에 언급된 주변 장소만 버튼으로 -->
            <div v-if="m.places && m.places.length" class="ac-nearby-list ac-msg-places">
              <button v-for="p in m.places" :key="p.id" class="ac-nearby-chip"
                      @click="pickNearby(p)">
                <span class="ac-nearby-dot" :style="{ background: catColor(p.category) }"></span>
                <span class="ac-nearby-name">{{ p.title }}</span>
                <span class="ac-nearby-dist">{{ fmtDist(p.distanceM) }}</span>
              </button>
            </div>
          </div>

          <div v-if="loading" class="ac-msg ac-msg--assistant">
            <div class="ac-bubble ac-typing">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>

        <!-- 추천 질문 (첫 대화 시) -->
        <div v-if="!messages.length && !loading" class="ac-suggestions">
          <button v-for="q in suggestions" :key="q" class="ac-chip"
                  @click="send(q)">{{ q }}</button>
        </div>

        <!-- 입력 -->
        <form class="ac-input-row" @submit.prevent="send()">
          <input v-model="input" class="ac-input" type="text"
                 :placeholder="loading ? '답변 생성 중…' : '질문을 입력하세요'"
                 :disabled="loading" maxlength="500" />
          <button class="ac-send" type="submit" :disabled="loading || !input.trim()">
            보내기
          </button>
        </form>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { sendAttractionChat } from '@/api/attraction'
import { useAttractionChatStore } from '@/stores/attractionChat'

const props = defineProps({
  attractionId: { type: [Number, String], required: true },
  attractionTitle: { type: String, default: '' },
})
const emit = defineEmits(['select'])

const chatStore = useAttractionChatStore()

// 아코디언 펼침 상태는 스토어에 보존(패널 리마운트/네비게이션에도 유지)
const open = computed({
  get: () => chatStore.chatOpen,
  set: (v) => { chatStore.chatOpen = v },
})
const input = ref('')
const loading = ref(false)
const scrollEl = ref(null)

// 대화는 관광지별로 스토어에 보존 — 다른 장소로 갔다 돌아와도 그대로 복원됨
const session = computed(() => chatStore.sessions[String(props.attractionId)] || { messages: [], conversationId: null })
const messages = computed(() => session.value.messages)

const suggestions = ['어떤 곳이야?', '가볼 만해?', '운영 시간은?', '주변에 뭐가 있어?']

// 분류 색은 디자인 시스템 --cat-*-ink 토큰과 일치(전 화면 공통)
const CAT_COLORS = {
  '관광지': '#6357C9', '문화시설': '#0F6E56', '레포츠': '#2E8B57',
  '숙박': '#185FA5', '쇼핑': '#A8650E', '음식점': '#993556',
}
function catColor(c) { return CAT_COLORS[c] || 'var(--gray-muted)' }
function fmtDist(m) {
  if (m == null) return ''
  return m < 1000 ? `${Math.round(m)}m` : `${(m / 1000).toFixed(1)}km`
}
function pickNearby(place) { emit('select', place) }

// 관광지가 바뀌어도 대화는 보존(스토어). 세션만 보장하고 입력/로딩 초기화 + 복원 대화로 스크롤
watch(() => props.attractionId, () => {
  chatStore.session(props.attractionId)
  input.value = ''
  loading.value = false
  scrollToBottom()
}, { immediate: true })

async function scrollToBottom() {
  await nextTick()
  if (scrollEl.value) scrollEl.value.scrollTop = scrollEl.value.scrollHeight
}

async function send(preset) {
  const text = (preset ?? input.value).trim()
  if (!text || loading.value) return

  // 요청 중 다른 장소로 이동해도 올바른 세션에 기록되도록 id/세션을 캡처
  const id = props.attractionId
  const s = chatStore.session(id)
  s.messages.push({ role: 'user', content: text })
  input.value = ''
  loading.value = true
  scrollToBottom()

  try {
    const data = await sendAttractionChat(id, text, s.conversationId)
    s.conversationId = data.conversationId
    // 답변 텍스트에 이름이 언급된 주변 장소만 버튼으로 노출
    const places = Array.isArray(data.nearby)
      ? data.nearby.filter(p => p.title && data.reply && data.reply.includes(p.title))
      : []
    s.messages.push({ role: 'assistant', content: data.reply, places })
  } catch (err) {
    const msg = err?.status === 401
      ? '로그인 후 이용할 수 있어요.'
      : '답변을 가져오지 못했어요. 잠시 후 다시 시도해 주세요.'
    s.messages.push({ role: 'assistant', content: msg })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}
</script>

<style scoped>
.ac-wrap { margin-top: var(--space-1); }

.ac-header {
  display: flex; align-items: center; justify-content: space-between;
  width: 100%; padding: 10px 14px; cursor: pointer;
  background: var(--purple-50); border: 1px solid var(--purple-100);
  border-radius: var(--radius-lg);
  color: var(--purple-900); font-size: var(--text-sm); font-weight: 600;
  transition: background .14s, border-color .14s;
}
.ac-header:hover { background: var(--purple-100); }
.ac-header-title { display: flex; align-items: center; gap: var(--space-2); }
.ac-spark { flex-shrink: 0; }
.ac-chevron { transition: transform .2s; flex-shrink: 0; }
.ac-chevron--open { transform: rotate(180deg); }

.ac-body {
  margin-top: var(--space-2);
  border: 1px solid var(--gray-border); border-radius: var(--radius-lg);
  background: var(--bg-surface); overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.ac-messages {
  max-height: 260px; overflow-y: auto; padding: var(--space-3);
  display: flex; flex-direction: column; gap: var(--space-2);
}
.ac-empty { color: var(--gray-muted); font-size: var(--text-sm); text-align: center; margin: var(--space-4) 0; }

.ac-row { display: flex; flex-direction: column; gap: 6px; }
.ac-row--user { align-items: flex-end; }
.ac-row--assistant { align-items: flex-start; }
.ac-msg { display: flex; width: 100%; }
.ac-msg--user { justify-content: flex-end; }
.ac-msg--assistant { justify-content: flex-start; }
.ac-msg-places { width: 100%; }
.ac-bubble {
  max-width: 80%; padding: 9px 12px; border-radius: var(--radius-xl);
  font-size: var(--text-sm); line-height: 1.5; white-space: pre-wrap; word-break: break-word;
}
.ac-msg--user .ac-bubble { background: var(--purple-900); color: #fff; border-bottom-right-radius: var(--radius-sm); }
.ac-msg--assistant .ac-bubble { background: var(--bg-page); color: var(--text-primary); border-bottom-left-radius: var(--radius-sm); }

.ac-typing { display: flex; gap: 4px; align-items: center; }
.ac-typing span {
  width: 6px; height: 6px; border-radius: 50%; background: var(--gray-muted);
  animation: ac-blink 1.2s infinite both;
}
.ac-typing span:nth-child(2) { animation-delay: .2s; }
.ac-typing span:nth-child(3) { animation-delay: .4s; }
@keyframes ac-blink { 0%, 80%, 100% { opacity: .3; } 40% { opacity: 1; } }

.ac-suggestions { display: flex; flex-wrap: wrap; gap: 6px; padding: 0 var(--space-3) var(--space-3); }
.ac-chip {
  padding: 6px 12px; border: 1px solid var(--purple-100); border-radius: var(--radius-full);
  background: var(--purple-50); color: var(--purple-900); font-size: var(--text-xs);
  font-weight: 500; cursor: pointer; transition: background .14s;
}
.ac-chip:hover { background: var(--purple-100); }

.ac-nearby-list { display: flex; flex-wrap: wrap; gap: 6px; }
.ac-nearby-chip {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 12px; border: 1px solid var(--gray-border); border-radius: var(--radius-full);
  background: var(--bg-surface); font-size: var(--text-xs); color: var(--gray-dark);
  cursor: pointer; max-width: 100%; transition: background .14s, border-color .14s;
}
.ac-nearby-chip:hover { background: var(--bg-page); border-color: var(--purple-100); }
.ac-nearby-dot { width: 8px; height: 8px; border-radius: 50%; flex: 0 0 auto; }
.ac-nearby-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 160px; }
.ac-nearby-dist { color: var(--gray-muted); font-size: var(--text-2xs); flex: 0 0 auto; font-variant-numeric: tabular-nums; }

.ac-input-row { display: flex; gap: var(--space-2); padding: 10px 12px; border-top: 1px solid var(--gray-border); }
.ac-input {
  flex: 1; padding: 9px 12px; border: 1px solid var(--gray-border); border-radius: var(--radius-md);
  font-size: var(--text-sm); color: var(--text-primary); background: var(--bg-page);
  outline: none; transition: border-color .14s, background .14s, box-shadow .14s;
}
.ac-input:focus {
  border-color: var(--purple-900); background: var(--bg-surface);
  box-shadow: 0 0 0 3px rgba(83,74,183,.08);
}
.ac-send {
  padding: 0 16px; border: none; border-radius: var(--radius-md);
  background: var(--purple-900); color: #fff; font-size: var(--text-sm); font-weight: 600;
  cursor: pointer; transition: opacity .14s;
}
.ac-send:hover:not(:disabled) { opacity: .92; }
.ac-send:disabled { background: var(--purple-100); cursor: not-allowed; }

.ac-collapse-enter-active, .ac-collapse-leave-active { transition: opacity .2s ease; }
.ac-collapse-enter-from, .ac-collapse-leave-to { opacity: 0; }
</style>
