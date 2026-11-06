// Session cookies stay in the browser; no passwords or session tokens are stored in localStorage.
export function createApiFetch(baseUrl = '') {
  const base = baseUrl.trim().replace(/\/+$/, '')
  return async function apiFetch(path, options = {}) {
    const method = (options.method || 'GET').toUpperCase()
    const headers = new Headers(options.headers)
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      const csrfResponse = await fetch(`${base}/api/auth/csrf`, { credentials: 'include', signal: options.signal })
      if (!csrfResponse.ok) throw new Error('Unable to initialize request security. Please sign in again.')
      const csrf = await csrfResponse.json()
      headers.set(csrf.headerName, csrf.token)
    }
    const response = await fetch(`${base}${path}`, { ...options, method, headers, credentials: 'include' })
    if (response.status === 401 && !path.startsWith('/api/auth/')) {
      window.dispatchEvent(new Event('session-expired'))
    }
    return response
  }
}

// Empty/unset keeps same-origin requests for Vite or a production reverse proxy.
export const apiFetch = createApiFetch(import.meta.env?.VITE_API_BASE_URL)
