async function doFetch(method, url, body) {
  const opts = {
    method,
    credentials: 'include',
    headers: body ? { 'Content-Type': 'application/json' } : {},
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

export const http = {
  get: (url) => request('GET', url),
  post: (url, body) => request('POST', url, body),
  put: (url, body) => request('PUT', url, body),
  del: (url) => request('DELETE', url),
}
