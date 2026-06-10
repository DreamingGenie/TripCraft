/**
 * 날짜/시간 포맷 유틸리티
 * CommunityView, CommunityPostView 등 여러 뷰에서 공통 사용
 */

/**
 * LocalDateTime 문자열을 상대 시간 또는 월/일 형식으로 반환
 * @param {string} dt - ISO 8601 형식 날짜 문자열
 */
export function formatDate(dt) {
  if (!dt) return ''
  // LocalDateTime은 타임존 없이 직렬화되므로 UTC로 명시
  const d = new Date(dt.includes('Z') || dt.includes('+') ? dt : dt + 'Z')
  const diff = Date.now() - d.getTime()
  if (diff < 60000)   return '방금'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}분 전`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}시간 전`
  return `${d.getMonth() + 1}/${d.getDate()}`
}

/**
 * 날짜 문자열(YYYY-MM-DD)을 "M월 D일" 형식으로 반환
 * @param {string} dateStr - 'YYYY-MM-DD' 형식
 */
export function formatTripDate(dateStr) {
  const d = new Date(dateStr + 'T00:00:00')
  return `${d.getMonth() + 1}월 ${d.getDate()}일`
}
