import { apiFetch } from '../api.js'
import { useRef, useState } from 'react'
import { targetTotal } from '../portfolioData.js'
import { tickerColor } from '../chartColors.js'

export default function AllocationTargets({ portfolioId, targets, holdings, loading, error: loadError, onSaved }) {
  const [editing, setEditing] = useState(false)
  const [rows, setRows] = useState([])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [symbol, setSymbol] = useState('')
  const submitting = useRef(false)
  const total = targetTotal(rows)
  const valid = rows.length > 0 && total === 1000000
  function open() {
    const symbols = [...new Set([...(holdings || []).map(row => row.symbol), ...(targets || []).map(row => row.symbol)])].sort()
    setRows(symbols.map(symbol => ({ symbol, targetPercent: String(targets?.find(row => row.symbol === symbol)?.targetPercent ?? 0) })))
    setError(''); setMessage(''); setEditing(true)
  }
  function edit(index, value) { setRows(previous => previous.map((row, i) => i === index ? { ...row, targetPercent: value } : row)) }
  function addSymbol() {
    const normalized = symbol.trim().toUpperCase()
    if (!/^[A-Z][A-Z0-9.-]{0,31}$/.test(normalized) || rows.some(row => row.symbol === normalized)) {
      setError('Enter a valid ticker that is not already in your target.'); return
    }
    setRows(previous => [...previous, { symbol: normalized, targetPercent: '0' }]); setSymbol(''); setError('')
  }
  async function save(next) {
    if (submitting.current) return
    submitting.current = true; setSaving(true); setError(''); setMessage('')
    try {
      const response = await apiFetch(`/api/portfolios/${portfolioId}/allocations`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ targets: next }),
      })
      if (!response.ok) throw new Error(response.status === 400
        ? 'Check your percentages and ticker symbols. Targets must total exactly 100%.'
        : 'Unable to save targets. Your changes are still here; please try again.')
      setEditing(false); setMessage(next.length ? 'Target allocation saved. Updating your chart and drift…' : 'Target allocation cleared.')
      onSaved()
    } catch (error) { setError(error instanceof TypeError ? 'Could not confirm the save. Refresh before retrying.' : error.message) }
    finally { setSaving(false); submitting.current = false }
  }
  return <section className="target-editor" aria-labelledby="targets-heading">
    <h3 id="targets-heading">Your allocation plan</h3>
    {loadError && <p role="alert">{loadError}</p>}
    {!editing ? <button className="primary" disabled={loading || !targets || saving} onClick={open}>
      {targets?.length ? 'Edit target allocation' : 'Set target allocation'}</button>
      : <form className="workspace-form" onSubmit={event => { event.preventDefault(); if (valid) save(rows) }} aria-busy={saving}>
        <p className="muted">Drag a slider or enter an exact percentage. Your holdings are included; you can also plan for other tickers.</p>
        <fieldset disabled={saving}>
          {rows.map((row, index) => <div className="target-row" key={row.symbol}>
            <div className="target-row-heading"><strong className="ticker-label"><i style={{ backgroundColor: tickerColor(row.symbol) }} aria-hidden="true" />{row.symbol}</strong>
              <button className="text-button" type="button" aria-label={`Remove ${row.symbol} from draft`} onClick={() => setRows(rows.filter((_, i) => i !== index))}>Remove</button></div>
            <div className="target-controls"><input aria-label={`${row.symbol} allocation slider`} type="range" min="0" max="100" step="0.0001"
              value={row.targetPercent === '' ? 0 : row.targetPercent} onChange={event => edit(index, event.target.value)} />
              <label><span className="sr-only">{row.symbol} target percentage</span><input type="number" min="0" max="100" step="0.0001" required
                value={row.targetPercent} onChange={event => edit(index, event.target.value)} /></label><span>%</span></div>
          </div>)}
          <div className="add-target"><label htmlFor="additional-ticker">Add another ticker (optional)</label>
            <div><input id="additional-ticker" value={symbol} maxLength={32} placeholder="e.g. VOO" onChange={event => setSymbol(event.target.value)} />
              <button type="button" className="secondary" disabled={rows.length >= 100} onClick={addSymbol}>Add</button></div></div>
          <div className={`target-total ${valid ? 'balanced' : ''}`} role="status">
            <strong>Total allocation: {total === null ? 'Check percentages' : `${(total / 10000).toFixed(4).replace(/\.?0+$/, '')}%`}</strong>
            <span>{total === null ? 'Use 0–100%, with up to four decimal places.' : valid ? 'Ready to save' : `${(Math.abs(1000000 - total) / 10000).toFixed(4).replace(/\.?0+$/, '')}% ${total > 1000000 ? 'over' : 'remaining'}`}</span>
          </div>
          <div className="form-actions"><button className="secondary" type="button" onClick={() => { setEditing(false); setError('') }}>Cancel</button>
            <button className="primary" disabled={!valid} type="submit">{saving ? 'Saving…' : 'Save target'}</button></div>
        </fieldset>
      </form>}
    {targets?.length > 0 && !editing && <button className="text-button reset-target" disabled={saving || loading} onClick={() => {
      if (window.confirm('Clear your saved target allocation? This removes your targets, but keeps your holdings and transactions.')) save([])
    }}>Clear saved targets</button>}
    {error && <p className="auth-feedback auth-error" role="alert">{error}</p>}
    {message && <p className="auth-feedback auth-success" role="status">{message}</p>}
  </section>
}
