// Session cookies stay in the browser; no passwords or session tokens are stored in localStorage.
export function createApiClient(baseUrl = '') {
  const base = baseUrl.trim().replace(/\/+$/, '')

  async function initializeCsrf({ signal } = {}) {
    const response = await fetch(`${base}/api/auth/csrf`, { credentials: 'include', signal })
    if (!response.ok) throw new Error(`Unable to initialize request security (HTTP ${response.status}). Check the backend URL and try signing in again.`)
    const csrf = await response.json()
    if (typeof csrf.headerName !== 'string' || !csrf.headerName || typeof csrf.token !== 'string' || !csrf.token) {
      throw new Error('The backend returned an invalid CSRF response. Check the backend URL.')
    }
    return csrf
  }

  async function apiFetch(path, options = {}) {
    const method = (options.method || 'GET').toUpperCase()
    const headers = new Headers(options.headers)
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      const csrf = await initializeCsrf({ signal: options.signal })
      headers.set(csrf.headerName, csrf.token)
    }
    const response = await fetch(`${base}${path}`, { ...options, method, headers, credentials: 'include' })
    if (response.status === 401 && !path.startsWith('/api/auth/')) {
      window.dispatchEvent(new Event('session-expired'))
    }
    return response
  }

  return { apiFetch, initializeCsrf }
}

// Keep the factory available for callers that only need requests.
export const createApiFetch = baseUrl => createApiClient(baseUrl).apiFetch
// Empty/unset supports same-origin proxies; Vercel builds require an explicit backend URL.
export const { apiFetch, initializeCsrf } = createApiClient(import.meta.env?.VITE_API_BASE_URL)
