import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'

// 관광지별 챗봇 대화를 보존한다. 네비게이션 스택으로 이전 장소로 돌아가도
// 해당 관광지의 대화(메시지 + conversationId)가 그대로 복원된다.
export const useAttractionChatStore = defineStore('attractionChat', () => {
  const sessions = reactive({})   // { [attractionId]: { messages: [], conversationId: null } }
  const chatOpen = ref(false)     // 챗봇 아코디언 펼침 상태(패널 리마운트와 무관하게 유지)

  function session(id) {
    const key = String(id)
    if (!sessions[key]) sessions[key] = { messages: [], conversationId: null }
    return sessions[key]
  }

  return { sessions, session, chatOpen }
})
