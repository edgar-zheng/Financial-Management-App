import { test } from 'node:test'
import assert from 'node:assert/strict'
import { loadPortfolioData, targetTotal, targetUnits } from './portfolioData.js'

test('target totals use exact backend precision', () => {
  assert.equal(targetTotal([{ targetPercent: '33.3333' }, { targetPercent: '33.3333' }, { targetPercent: '33.3334' }]), 1000000)
  assert.equal(targetTotal([{ targetPercent: 87 }]), 870000)
  assert.equal(targetTotal([{ targetPercent: 60 }, { targetPercent: 48 }]), 1080000)
  for (const invalid of ['', '-1', '100.0001', '0.00001', 'NaN']) assert.equal(targetUnits(invalid), null)
})
test('refresh loads every dependent view and isolates market failures', async () => {
  const original = globalThis.fetch
  const calls = []
  globalThis.fetch = async path => {
    calls.push(path)
    return path.endsWith('/analytics') ? new Response('private provider details', { status: 503 }) : Response.json([])
  }
  try {
    const result = await loadPortfolioData(17, new AbortController().signal)
    assert.equal(calls.length, 6)
    assert.deepEqual(result.holdings.data, [])
    assert.deepEqual(result.transactions.data, [])
    assert.equal(result.summary.data, null)
    assert.ok(!result.summary.error.includes('private'))
    assert.ok(calls.includes('/api/portfolios/17/allocations/drift'))
  } finally { globalThis.fetch = original }
})

test('ticker colors are normalized and stable across chart modes', async () => {
  const { tickerColor } = await import('./chartColors.js')
  assert.equal(tickerColor(' aapl '), tickerColor('AAPL'))
  assert.equal(tickerColor('AAPL'), tickerColor('AAPL'))
  assert.notEqual(tickerColor('AAPL'), tickerColor('MSFT'))
})
