import { test } from 'node:test'
import assert from 'node:assert/strict'
import { apiFetch } from './api.js'

test('mutations obtain a fresh CSRF token and send session cookies', async () => {
  const original = globalThis.fetch
  const calls = []
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    return path === '/api/auth/csrf'
      ? new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: `token-${calls.length}` }))
      : new Response(null, { status: 204 })
  }
  try {
    await apiFetch('/api/auth/login', { method: 'POST', body: new URLSearchParams({ email: 'test@example.com', password: 'test' }) })
    await apiFetch('/api/portfolios', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"name":"Test"}' })
    assert.equal(calls.length, 4)
    assert.equal(calls[1].options.headers.get('X-CSRF-TOKEN'), 'token-1')
    assert.equal(calls[3].options.headers.get('X-CSRF-TOKEN'), 'token-3')
    assert.equal(calls[3].options.headers.get('Content-Type'), 'application/json')
    assert.equal(calls[3].options.credentials, 'same-origin')
  } finally { globalThis.fetch = original }
})

test('unauthorized protected GET notifies UI without retrying', async () => {
  const original = globalThis.fetch
  const originalWindow = globalThis.window
  const events = []
  let calls = 0
  globalThis.window = { dispatchEvent: event => events.push(event.type) }
  globalThis.fetch = async () => { calls++; return new Response(null, { status: 401 }) }
  try {
    await apiFetch('/api/portfolios/1')
    assert.equal(calls, 1)
    assert.deepEqual(events, ['session-expired'])
    await apiFetch('/api/auth/me')
    assert.equal(events.length, 1)
  } finally { globalThis.fetch = original; globalThis.window = originalWindow }
})
