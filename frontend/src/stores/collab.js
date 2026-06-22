import { defineStore } from 'pinia'
import { ref } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore } from './auth'

export const useCollabStore = defineStore('collab', () => {
  const participants = ref([])   // [{ memberId, nickname, x, y, interaction, targetBlockId }]
  const grabMap = ref({})        // blockId → memberId
  const connected = ref(false)

  let stompClient = null
  let activeTripId = null
  let reconnectTimer = null

  // 이벤트 핸들러 콜백 — ScheduleView에서 주입
  let onTripEvent = null
  let onPresence = null

  function setHandlers({ tripEvent, presence }) {
    onTripEvent = tripEvent
    onPresence = presence
  }

  function connect(tripId) {
    activeTripId = tripId
    _doConnect(tripId)
  }

  function _doConnect(tripId) {
    const auth = useAuthStore()
    const token = auth.user?.token

    stompClient = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 0,   // 수동 재연결
      onConnect: () => {
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
      },
      onDisconnect: () => {
        connected.value = false
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
