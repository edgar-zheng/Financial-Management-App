import { test } from 'node:test'
import assert from 'node:assert/strict'
import { apiFetch, createApiFetch } from './api.js'

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
    assert.ok(calls.every(call => call.options.credentials === 'include'))
  } finally { globalThis.fetch = original }
})

test('configured backend receives both CSRF and API requests with session cookies', async () => {
  const original = globalThis.fetch
  const calls = []
  const remoteFetch = createApiFetch(' https://backend.example.com/ ')
  const controller = new AbortController()
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    return path.endsWith('/api/auth/csrf')
      ? new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'remote-token' }))
      : new Response(null, { status: 204 })
  }
  try {
    await remoteFetch('/api/portfolios', { method: 'POST', signal: controller.signal })
    await remoteFetch('/api/portfolios/1/holdings')
    assert.deepEqual(calls.map(call => call.path), [
      'https://backend.example.com/api/auth/csrf',
      'https://backend.example.com/api/portfolios',
      'https://backend.example.com/api/portfolios/1/holdings',
    ])
    assert.ok(calls.every(call => call.options.credentials === 'include'))
    assert.equal(calls[1].options.headers.get('X-CSRF-TOKEN'), 'remote-token')
    assert.equal(calls[0].options.signal, controller.signal)
    assert.equal(calls[1].options.signal, controller.signal)
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

test('all unsafe methods use the returned header name and a fresh token', async () => {
  const original = globalThis.fetch
  const calls = []
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    return path.endsWith('/csrf')
      ? new Response(JSON.stringify({ headerName: 'X-APPLICATION-CSRF', token: `fresh-${calls.length}` }))
      : new Response(null, { status: 204 })
  }
  try {
    const remoteFetch = createApiFetch('https://backend.example.com')
    for (const method of ['POST', 'PUT', 'PATCH', 'DELETE']) {
      await remoteFetch('/api/resource', { method })
      const last = calls.at(-1)
      assert.equal(last.options.headers.get('X-APPLICATION-CSRF'), `fresh-${calls.length - 1}`)
      assert.equal(last.options.headers.has('X-CSRF-TOKEN'), false)
    }
    assert.equal(calls.length, 8)
  } finally { globalThis.fetch = original }
})

test('failed or malformed CSRF initialization never sends a mutation', async () => {
  const original = globalThis.fetch
  try {
    for (const response of [new Response(null, { status: 404 }), new Response(JSON.stringify({ token: 'missing-header-name' }))]) {
      let calls = 0
      globalThis.fetch = async () => { calls++; return response }
      await assert.rejects(apiFetch('/api/auth/register', { method: 'POST' }), /backend URL/)
      assert.equal(calls, 1)
    }
  } finally { globalThis.fetch = original }
})
