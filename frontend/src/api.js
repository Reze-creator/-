const SESSION_KEY = 'parking-session'

export function getToken() {
  return sessionStorage.getItem(SESSION_KEY)
}

export function saveToken(token) {
  if (token) sessionStorage.setItem(SESSION_KEY, token)
  else sessionStorage.removeItem(SESSION_KEY)
}

export async function api(path, { method = 'GET', body } = {}) {
  const headers = { Accept: 'application/json' }
  if (getToken()) headers.Authorization = `Bearer ${getToken()}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  let response
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new Error('无法连接服务，请检查网络或后端是否启动')
  }
  let result
  try {
    result = await response.json()
  } catch {
    throw new Error('服务返回异常，请稍后重试')
  }
  if (!response.ok) {
    const error = new Error(result.message || '请求失败')
    error.code = result.code
    error.status = response.status
    error.requestId = result.requestId
    if (response.status === 401 && result.code !== 'INVALID_CREDENTIALS') {
      saveToken(null)
      window.dispatchEvent(new Event('parking-session-expired'))
    }
    throw error
  }
  return result.data
}
