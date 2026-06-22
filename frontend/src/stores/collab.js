import { defineStore } from 'pinia'
import { ref } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { tripApi } from '@/api/trip'

export const useCollabStore = defineStore('collab', () => {
  const participants = ref([])   // [{ memberId, nickname, x, y, interaction, targetBlockId }]
  const grabMap = ref({})        // blockId → memberId
  const connected = ref(false)

  let stompClient = null
  let activeTripId = null
  let reconnectTimer = null
  let isReconnecting = false

  // 이벤트 핸들러 콜백 — ScheduleView에서 주입
  let onTripEvent = null
  let onPresence = null
  let onReconnect = null  // 재연결 시 loadTrip() 콜백

  function setHandlers({ tripEvent, presence, reconnect }) {
    onTripEvent = tripEvent
    onPresence = presence
    onReconnect = reconnect ?? null
  }

  async function connect(tripId) {
    activeTripId = tripId
    await _doConnect(tripId)
  }

  async function _doConnect(tripId) {
    // 핸드셰이크 전 REST 호출로 access_token 쿠키 최신화 (만료 시 http.js 자동 갱신)
    try {
      await tripApi.get(tripId)
    } catch {
      connected.value = false
      return  // 인증 불가 또는 일정 접근 불가 → 재연결 중단
    }

    stompClient = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: {},   // 인증은 쿠키(access_token)로 핸드셰이크 시 처리
      reconnectDelay: 0,    // 수동 재연결
      onConnect: async () => {
        connected.value = true
        clearTimeout(reconnectTimer)

        stompClient.subscribe(`/topic/trip/${tripId}`, (msg) => {
          const event = JSON.parse(msg.body)
          if (onTripEvent) onTripEvent(event)
        })

        stompClient.subscribe(`/topic/trip/${tripId}/presence`, (msg) => {
          const event = JSON.parse(msg.body)
          const payload = event.payload || {}
          const list = payload.participants || []
          participants.value = list
          const map = {}
          list.forEach(p => { if (p.targetBlockId) map[p.targetBlockId] = p.memberId })
          grabMap.value = map
          if (onPresence) onPresence(list)
        })

        // 재연결인 경우에만 놓친 변경 사항 재동기화
        if (isReconnecting && onReconnect) {
          try { await onReconnect() }
          catch (e) { console.warn('[collab] reconnect sync failed', e) }
        }
        isReconnecting = false
      },
      onDisconnect: () => {
        connected.value = false
        isReconnecting = true
        reconnectTimer = setTimeout(() => _doConnect(tripId), 3000)
      },
      onStompError: (frame) => {
        console.warn('[collab] STOMP error', frame)
      },
    })

    stompClient.activate()
  }

  function disconnect() {
    clearTimeout(reconnectTimer)
    activeTripId = null
    isReconnecting = false
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }
    connected.value = false
    participants.value = []
    grabMap.value = {}
  }

  function sendPointer(tripId, payload) {
    if (!stompClient?.connected) return
    stompClient.publish({
      destination: `/app/trip/${tripId}/pointer`,
      body: JSON.stringify(payload),
    })
  }

  function isGrabbedByOther(blockId, myMemberId) {
    const owner = grabMap.value[blockId]
    return owner != null && owner !== myMemberId
  }

  return {
    participants,
    grabMap,
    connected,
    connect,
    disconnect,
    sendPointer,
    setHandlers,
    isGrabbedByOther,
  }
})
