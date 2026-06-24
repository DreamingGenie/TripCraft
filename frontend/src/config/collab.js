// 협업 실시간 동작 튜닝값 — 시연(부드러움) ↔ 실서비스(효율) 전환을 .env로 제어.
// 값은 frontend/.env 의 VITE_COLLAB_* 에서 읽고, 없으면 시연 친화 기본값을 쓴다.
// (나중에 application.yml/별도 설정으로 옮기더라도 이 모듈만 바꾸면 됨)

const num = (v, fallback) => {
  const n = Number(v)
  return Number.isFinite(n) ? n : fallback
}

export const collabConfig = {
  // 적응형 전송 간격 하한(ms) — 한가할 때(부드러움). 송신측이 혼잡 감지 시 이 값에서 max까지 늘림.
  cursorThrottleMs: num(import.meta.env.VITE_COLLAB_CURSOR_THROTTLE_MS, 40),
  // 적응형 전송 간격 상한(ms) — 움직임이 전송보다 빠를 때(혼잡) back off 한계.
  cursorThrottleMaxMs: num(import.meta.env.VITE_COLLAB_CURSOR_THROTTLE_MAX_MS, 90),
  // 수신 보간 transition 상한(ms) — 한가할 때 최대 글라이드. 실제 transition은 갱신 간격(dt)에
  // 맞춰 동적으로 정해지며 항상 dt 이하라 백로그가 생기지 않음(드래그로 촘촘하면 자동 스냅).
  cursorSmoothMs: num(import.meta.env.VITE_COLLAB_CURSOR_SMOOTH_MS, 110),
  // presence keepalive 주기(ms). 길수록 트래픽↓·stale 위험↑
  keepaliveMs: num(import.meta.env.VITE_COLLAB_KEEPALIVE_MS, 4000),
  // 끊김 후 재연결 시도 지연(ms)
  reconnectMs: num(import.meta.env.VITE_COLLAB_RECONNECT_MS, 3000),
}
