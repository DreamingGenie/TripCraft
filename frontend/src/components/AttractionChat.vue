<template>
  <div class="ac-wrap">
    <div class="detail-section-divider"></div>

    <!-- 헤더 / 토글 -->
    <button class="ac-header" @click="open = !open">
      <span class="ac-header-title">
        <span class="ac-spark">✨</span> AI에게 이 관광지 물어보기
      </span>
      <span class="ac-chevron" :class="{ 'ac-chevron--open': open }">⌄</span>
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

const CAT_COLORS = {
  '관광지': '#8B85E0', '문화시설': '#48B89A', '레포츠': '#55B36E',
  '숙박': '#6B9FD4', '쇼핑': '#D4844A', '음식점': '#D46070',
}
function catColor(c) { return CAT_COLORS[c] || '#9ca3af' }
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
.ac-wrap { margin-top: 4px; }

.ac-header {
  display: flex; align-items: center; justify-content: space-between;
  width: 100%; padding: 10px 12px; cursor: pointer;
  background: #f5f3ff; border: 1px solid #ddd6fe; border-radius: 10px;
  color: #5b21b6; font-size: 13px; font-weight: 600;
}
.ac-header-title { display: flex; align-items: center; gap: 6px; }
.ac-spark { font-size: 14px; }
.ac-chevron { transition: transform .2s; font-size: 16px; line-height: 1; }
.ac-chevron--open { transform: rotate(180deg); }

.ac-body {
  margin-top: 8px; border: 1px solid #e5e7eb; border-radius: 10px;
  background: #fff; overflow: hidden;
}

.ac-messages {
  max-height: 260px; overflow-y: auto; padding: 12px;
  display: flex; flex-direction: column; gap: 8px;
}
.ac-empty { color: #9ca3af; font-size: 12.5px; text-align: center; margin: 16px 0; }

.ac-row { display: flex; flex-direction: column; gap: 6px; }
.ac-row--user { align-items: flex-end; }
.ac-row--assistant { align-items: flex-start; }
.ac-msg { display: flex; width: 100%; }
.ac-msg--user { justify-content: flex-end; }
.ac-msg--assistant { justify-content: flex-start; }
.ac-msg-places { width: 100%; }
.ac-bubble {
  max-width: 80%; padding: 8px 11px; border-radius: 12px;
  font-size: 13px; line-height: 1.5; white-space: pre-wrap; word-break: break-word;
}
.ac-msg--user .ac-bubble { background: #7c3aed; color: #fff; border-bottom-right-radius: 4px; }
.ac-msg--assistant .ac-bubble { background: #f3f4f6; color: #1f2937; border-bottom-left-radius: 4px; }

.ac-typing { display: flex; gap: 4px; align-items: center; }
.ac-typing span {
  width: 6px; height: 6px; border-radius: 50%; background: #9ca3af;
  animation: ac-blink 1.2s infinite both;
}
.ac-typing span:nth-child(2) { animation-delay: .2s; }
.ac-typing span:nth-child(3) { animation-delay: .4s; }
@keyframes ac-blink { 0%, 80%, 100% { opacity: .3; } 40% { opacity: 1; } }

.ac-suggestions { display: flex; flex-wrap: wrap; gap: 6px; padding: 0 12px 10px; }
.ac-chip {
  padding: 6px 10px; border: 1px solid #ddd6fe; border-radius: 16px;
  background: #faf5ff; color: #6d28d9; font-size: 12px; cursor: pointer;
}
.ac-chip:hover { background: #f3e8ff; }

.ac-nearby-list { display: flex; flex-wrap: wrap; gap: 6px; }
.ac-nearby-chip {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 10px; border: 1px solid #e5e7eb; border-radius: 16px;
  background: #fff; font-size: 12px; color: #374151; cursor: pointer; max-width: 100%;
}
.ac-nearby-chip:hover { background: #f9fafb; border-color: #d1d5db; }
.ac-nearby-dot { width: 8px; height: 8px; border-radius: 50%; flex: 0 0 auto; }
.ac-nearby-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 160px; }
.ac-nearby-dist { color: #9ca3af; font-size: 11px; flex: 0 0 auto; }

.ac-input-row { display: flex; gap: 8px; padding: 10px 12px; border-top: 1px solid #f1f1f3; }
.ac-input {
  flex: 1; padding: 9px 12px; border: 1px solid #e5e7eb; border-radius: 8px;
  font-size: 13px; outline: none;
}
.ac-input:focus { border-color: #c4b5fd; }
.ac-send {
  padding: 0 14px; border: none; border-radius: 8px;
  background: #7c3aed; color: #fff; font-size: 13px; font-weight: 600; cursor: pointer;
}
.ac-send:disabled { background: #c4b5fd; cursor: not-allowed; }

.ac-collapse-enter-active, .ac-collapse-leave-active { transition: opacity .2s ease; }
.ac-collapse-enter-from, .ac-collapse-leave-to { opacity: 0; }
</style>
