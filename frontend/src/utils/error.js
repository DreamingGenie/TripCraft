export function getErrorMessage(err, defaultMsg = '오류가 발생했습니다.') {
  return err?.message || defaultMsg
}
