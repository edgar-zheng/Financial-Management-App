import { apiFetch } from '../api.js'
import { useRef, useState } from 'react'

export default function TransactionForm({ portfolioId, onCreated }) {
  const [symbol, setSymbol] = useState('')
  const [type, setType] = useState('BUY')
  const [quantity, setQuantity] = useState('')
  const [price, setPrice] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const submitting = useRef(false)

  async function submit(event) {
    event.preventDefault()
    if (submitting.current) return
    setError('')
    setMessage('')
    if (!symbol.trim()) {
      setError('Symbol must not be blank.')
      return
    }
    // Keep decimal inputs as strings so conversion to JavaScript numbers cannot round them.
    const decimal = /^\d{1,11}(\.\d{1,8})?$/
    if (![quantity, price].every(value => decimal.test(value) && /[1-9]/.test(value))) {
      setError('Quantity and price must be positive, with at most 11 integer and 8 decimal digits.')
      return
    }
    submitting.current = true
    setSaving(true)
    try {
      const response = await apiFetch(`/api/portfolios/${portfolioId}/transactions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ symbol: symbol.trim().toUpperCase(), type, quantity, price }),
      })
      if (!response.ok) {
        const body = await response.json().catch(() => null)
        const fields = Object.entries(body?.fieldErrors || {}).map(([key, value]) => `${key}: ${value}`)
        throw new Error(fields.join('; ') || body?.message || `Unable to save transaction (${response.status}).`)
      }
      setQuantity('')
      setPrice('')
      setMessage('Transaction saved.')
      onCreated()
    } catch (error) {
      setError(error instanceof TypeError
        ? 'Could not confirm the save. Refresh transaction history before retrying.'
        : error.message)
    } finally {
      submitting.current = false
      setSaving(false)
    }
  }

  return (
    <section aria-labelledby="transaction-heading">
      <h3 id="transaction-heading">Add transaction</h3>
      <form onSubmit={submit}>
        <fieldset disabled={saving}>
          <label>Symbol<input required maxLength={32} value={symbol} onChange={event => setSymbol(event.target.value)} placeholder="AAPL" /></label>
          <label>Type<select value={type} onChange={event => setType(event.target.value)}><option>BUY</option><option>SELL</option></select></label>
          <label>Quantity<input required inputMode="decimal" value={quantity} onChange={event => setQuantity(event.target.value)} placeholder="10" /></label>
          <label>Price<input required inputMode="decimal" value={price} onChange={event => setPrice(event.target.value)} placeholder="245.30" /></label>
          <button type="submit">{saving ? 'Saving…' : 'Add transaction'}</button>
        </fieldset>
      </form>
      {error && <p role="alert">{error}</p>}
      {message && <p role="status">{message}</p>}
    </section>
  )
}
