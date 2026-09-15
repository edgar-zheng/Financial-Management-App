// Session cookies stay in the browser; no passwords or session tokens are stored in localStorage.
export async function apiFetch(path, options = {}) {
  const method = (options.method || 'GET').toUpperCase()
  const headers = new Headers(options.headers)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrfResponse = await fetch('/api/auth/csrf', { credentials: 'same-origin', signal: options.signal })
    if (!csrfResponse.ok) throw new Error('Unable to initialize request security. Please sign in again.')
    const csrf = await csrfResponse.json()
    headers.set(csrf.headerName, csrf.token)
  }
  const response = await fetch(path, { ...options, method, headers, credentials: 'same-origin' })
  if (response.status === 401 && !path.startsWith('/api/auth/')) {
    window.dispatchEvent(new Event('session-expired'))
  }
  return response
}
