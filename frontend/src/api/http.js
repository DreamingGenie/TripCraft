async function request(method, url, body) {
  const opts = {
    method,
    credentials: 'include',
    headers: body ? { 'Content-Type': 'application/json' } : {},
  }
  if (body) opts.body = JSON.stringify(body)
  const res = await fetch(url, opts)
  const json = await res.json().catch(() => null)
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
  del: (url) => request('DELETE', url),
}
