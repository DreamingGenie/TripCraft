// 협업 실시간 동작 튜닝값 — 시연(부드러움) ↔ 실서비스(효율) 전환을 .env로 제어.
// 값은 frontend/.env 의 VITE_COLLAB_* 에서 읽고, 없으면 시연 친화 기본값을 쓴다.
// (나중에 application.yml/별도 설정으로 옮기더라도 이 모듈만 바꾸면 됨)

const num = (v, fallback) => {
  const n = Number(v)
  return Number.isFinite(n) ? n : fallback
}

export const collabConfig = {
  // 커서 전송 throttle(ms). 작을수록 부드럽지만 트래픽↑. 시연 33(≈30fps) / 실서비스 80~100 권장
  cursorThrottleMs: num(import.meta.env.VITE_COLLAB_CURSOR_THROTTLE_MS, 50),
  // 수신 커서/ghost가 갱신 사이를 미끄러지듯 보간하는 CSS transition(ms). 0이면 즉시(점프)
  // throttle보다 크게 잡으면 전환이 누적돼 드래그 후 한참 따라오는 랙이 생기므로 throttle 이하 권장.
  cursorSmoothMs: num(import.meta.env.VITE_COLLAB_CURSOR_SMOOTH_MS, 45),
  // presence keepalive 주기(ms). 길수록 트래픽↓·stale 위험↑
  keepaliveMs: num(import.meta.env.VITE_COLLAB_KEEPALIVE_MS, 4000),
  // 끊김 후 재연결 시도 지연(ms)
  reconnectMs: num(import.meta.env.VITE_COLLAB_RECONNECT_MS, 3000),
}
