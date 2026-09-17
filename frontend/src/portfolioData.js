import { apiFetch } from './api.js'

const resources = {
  transactions: 'transactions', holdings: 'holdings', summary: 'analytics',
  targets: 'allocations', drift: 'allocations/drift', valuations: 'holdings/valuation',
}

// Publish a complete refresh together; individual provider failures must not hide trade history.
export async function loadPortfolioData(id, signal) {
  const entries = await Promise.all(Object.entries(resources).map(async ([key, resource]) => {
    try {
      const response = await apiFetch(`/api/portfolios/${id}/${resource}`, { signal })
      if (!response.ok) throw new Error(response.status === 404
        ? 'This portfolio is unavailable to your account.'
        : ['summary', 'drift', 'valuations'].includes(key)
          ? 'Closing-price data is unavailable right now. Try refreshing shortly.'
          : `Unable to load ${key}. Please refresh to try again.`)
      return [key, { data: await response.json(), error: '' }]
    } catch (error) {
      if (signal.aborted) throw error
      return [key, { data: null, error: error instanceof TypeError ? 'Unable to connect. Please try again.' : error.message }]
    }
  }))
  return Object.fromEntries(entries)
}

export const money = value => Number(value).toLocaleString('en-US', { style: 'currency', currency: 'USD' })
export const percent = value => `${Number(value).toLocaleString('en-US', { maximumFractionDigits: 2 })}%`

// Integer units match the backend's four decimal places; no floating-point sum comparison.
export function targetUnits(value) {
  if (!/^\d{1,3}(\.\d{1,4})?$/.test(String(value))) return null
  const [whole, decimal = ''] = String(value).split('.')
  const units = Number(whole) * 10000 + Number(decimal.padEnd(4, '0'))
  return units <= 1000000 ? units : null
}
export function targetTotal(rows) {
  const values = rows.map(row => targetUnits(row.targetPercent))
  return values.some(value => value === null) ? null : values.reduce((sum, value) => sum + value, 0)
}
