import { defineStore } from 'pinia'
import { ref } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { tripApi } from '@/api/trip'
import { collabConfig } from '@/config/collab'

const PALETTE = ['#e74c3c', '#3498db', '#2ecc71', '#f39c12', '#9b59b6', '#1abc9c', '#e67e22', '#e91e63']

export const useCollabStore = defineStore('collab', () => {
  const participants = ref([])   // [{ memberId, nickname, zone, interaction, targetBlockId, dayIndex, colRatioX, contentY, mapRatioX, mapRatioY, grabRatioX, grabOffsetMin }]
  const grabMap = ref({})        // blockId → memberId
  const colorMap = ref({})       // memberId → hex color
  const connected = ref(false)

  let stompClient = null
  let reconnectTimer = null
  let isReconnecting = false
  let keepaliveTimer = null
  let observer = false   // 익명 관전 모드: 구독만(수신), 전송·keepalive·인증 프리페치 없음

  // 이벤트 핸들러 콜백 — ScheduleBoard에서 주입
  let onTripEvent = null
  let onPresence = null
  let onReconnect = null  // 재연결 시 loadTrip() 콜백

  function setHandlers({ tripEvent, presence, reconnect }) {
    onTripEvent = tripEvent
    onPresence = presence
    onReconnect = reconnect ?? null
  }

  async function connect(tripId, opts = {}) {
    observer = !!opts.observer   // 비로그인 관전: 구독만, 전송·keepalive 없음
    // 재호출 방어: 이전 연결이 살아 있으면 정리 후 새로 연결 (클라이언트 누수·중복 구독 방지)
    if (stompClient) {
      stopKeepalive()
      clearTimeout(reconnectTimer)
      stompClient.deactivate()
      stompClient = null
    }
    isReconnecting = false
    await _doConnect(tripId)
  }

  async function _doConnect(tripId) {
    // 핸드셰이크 전 REST 호출로 access_token 쿠키 최신화 (만료 시 http.js 자동 갱신).
    // observer(비로그인 관전)는 인증이 없으므로 생략 — 익명 SUBSCRIBE 는 백엔드가 share_access 로 허용.
    if (!observer) {
      try {
        await tripApi.get(tripId)
      } catch {
        connected.value = false
        return  // 인증 불가 또는 일정 접근 불가 → 재연결 중단
      }
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
          // 백엔드 grab 정의와 일치: interaction === 'grab' 인 경우만 잠금으로 본다
          // (커서 이동 시 잔존 targetBlockId 가 잠금으로 오인되는 것을 방지)
          list.forEach(p => {
            if (p.interaction === 'grab' && p.targetBlockId) map[p.targetBlockId] = p.memberId
          })
          grabMap.value = map
          if (onPresence) onPresence(list)
        })

        if (!observer) startKeepalive(tripId)  // 관전 모드는 전송 없음

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
        reconnectTimer = setTimeout(() => _doConnect(tripId), collabConfig.reconnectMs)
      },
      onStompError: (frame) => {
        console.warn('[collab] STOMP error', frame)
      },
    })

    stompClient.activate()
  }

  function startKeepalive(tripId) {
    stopKeepalive()
    keepaliveTimer = setInterval(() => {
      if (!stompClient?.connected) return
      stompClient.publish({
        destination: `/app/trip/${tripId}/pointer`,
        body: JSON.stringify({ zone: 'other', interaction: '', targetBlockId: null, nickname: '' }),
      })
    }, collabConfig.keepaliveMs)
  }

  function stopKeepalive() {
    if (keepaliveTimer) { clearInterval(keepaliveTimer); keepaliveTimer = null }
  }

  function disconnect() {
    stopKeepalive()
    clearTimeout(reconnectTimer)
    isReconnecting = false
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }
    connected.value = false
    participants.value = []
    grabMap.value = {}
    colorMap.value = {}
  }

  function sendPointer(tripId, payload) {
    if (observer) return   // 관전 모드(비로그인)는 전송 안 함
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

  function assignColors(list, myMemberId) {
    list.forEach(p => {
      if (p.memberId === myMemberId) return
      if (!colorMap.value[p.memberId]) {
        colorMap.value[p.memberId] = PALETTE[p.memberId % PALETTE.length]
      }
    })
  }

  return {
    participants,
    grabMap,
    colorMap,
    connected,
    connect,
    disconnect,
    sendPointer,
    setHandlers,
    isGrabbedByOther,
    assignColors,
  }
})
