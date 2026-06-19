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

async function requestForm(method, url, formData) {
  const opts = { method, credentials: 'include', body: formData }
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
  del: (url) => request('DELETE', url),
  postForm: (url, formData) => requestForm('POST', url, formData),
}
