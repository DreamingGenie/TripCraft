// 공유 링크(?s=)로 일정을 보는 동안 모든 API 요청에 공유 토큰을 싣는다.
// 서버는 소유자·협업자가 아니면 이 토큰이 맞을 때만 링크 공유 권한을 준다(일정 id만으로는 열리지 않음).
let shareToken = null
export function setShareToken(token) {
  shareToken = token || null
}

function withShareToken(headers) {
  return shareToken ? { ...headers, 'X-Share-Token': shareToken } : headers
}

async function doFetch(method, url, body) {
  const opts = {
    method,
    credentials: 'include',
    headers: withShareToken(body ? { 'Content-Type': 'application/json' } : {}),
  }
  if (body) opts.body = JSON.stringify(body)
  const res = await fetch(url, opts)
  const json = await res.json().catch(() => null)
  return { res, json }
}

async function request(method, url, body) {
  let { res, json } = await doFetch(method, url, body)

  if (res.status === 401) {
    const refreshRes = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
    if (refreshRes.ok) {
      const retried = await doFetch(method, url, body)
      res = retried.res
      json = retried.json
    }
  }

  if (!res.ok || !json?.success) {
    const err = new Error(json?.message || '오류가 발생했습니다.')
    err.status = res.status
    throw err
  }
  return json.data
}

async function requestForm(method, url, formData) {
  const opts = { method, credentials: 'include', body: formData, headers: withShareToken({}) }
  let res = await fetch(url, opts)
  let json = await res.json().catch(() => null)

  if (res.status === 401) {
    const refreshRes = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
    if (refreshRes.ok) {
      const retried = await fetch(url, opts)
      res = retried
      json = await retried.json().catch(() => null)
    }
  }

  if (!res.ok || !json?.success) {
    const err = new Error(json?.message || '오류가 발생했습니다.')
    err.status = res.status
    throw err
  }
  return json.data
}

export const http = {
  get: (url) => request('GET', url),
  post: (url, body) => request('POST', url, body),
  put: (url, body) => request('PUT', url, body),
  patch: (url, body) => request('PATCH', url, body),
  del: (url, body) => request('DELETE', url, body),
  postForm: (url, formData) => requestForm('POST', url, formData),
}
