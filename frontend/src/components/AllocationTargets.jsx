import { apiFetch } from '../api.js'
import { useEffect, useState } from 'react'

export default function AllocationTargets({ portfolioId }) {
  const [rows, setRows] = useState([])
  const [drift, setDrift] = useState(null)
  const [error, setError] = useState('')
  const [driftError, setDriftError] = useState('')
  const [ready, setReady] = useState(false)
  const [saving, setSaving] = useState(false)
  const [revision, setRevision] = useState(0)
  const [message, setMessage] = useState('')
  const path = `/api/portfolios/${portfolioId}/allocations`

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      try {
        const response = await apiFetch(path, { signal: controller.signal })
        if (!response.ok) throw new Error('Unable to load targets.')
        const data = await response.json()
        if (!controller.signal.aborted) { setRows(data); setReady(true) }
      } catch (error) { if (!controller.signal.aborted) setError(error.message) }
    }
    load()
    return () => controller.abort()
  }, [path])

  useEffect(() => {
    const controller = new AbortController()
    setDrift(null)
    setDriftError('')
    async function load() {
      try {
        const response = await apiFetch(`${path}/drift`, { signal: controller.signal })
        const data = await response.json()
        if (!response.ok) throw new Error(data.message || 'Unable to calculate drift.')
        if (!controller.signal.aborted) setDrift(data)
      } catch (error) { if (!controller.signal.aborted) setDriftError(error.message) }
    }
    load()
    return () => controller.abort()
  }, [path, revision])

  async function save(event) {
    event.preventDefault()
    if (saving) return
    setSaving(true); setError(''); setMessage('')
    try {
      const response = await apiFetch(path, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ targets: rows }),
      })
      const data = await response.json()
      if (!response.ok) throw new Error(Object.values(data.fieldErrors || {}).join('; ') || data.message || 'Unable to save targets.')
      setRows(data); setMessage('Targets saved.'); setRevision(value => value + 1)
    } catch (error) { setError(error.message) }
    finally { setSaving(false) }
  }

  function edit(index, field, value) {
    setRows(rows.map((row, i) => i === index ? { ...row, [field]: value } : row))
  }

  return <section aria-labelledby="targets-heading">
    <h3 id="targets-heading">Target allocation and drift</h3>
    <p>Targets must total 100%. Remove all rows to clear targets. Drift is actual minus target in percentage points.</p>
    {!ready && !error && <p role="status">Loading targets…</p>}
    <form onSubmit={save}><fieldset disabled={!ready || saving}>
      {rows.map((row, index) => <div key={index}>
        <label>Symbol<input required maxLength={32} value={row.symbol} onChange={event => edit(index, 'symbol', event.target.value)} /></label>
        <label>Target %<input type="number" required min="0" max="100" step="0.0001" value={row.targetPercent} onChange={event => edit(index, 'targetPercent', event.target.value)} /></label>
        <button type="button" onClick={() => setRows(rows.filter((_, i) => i !== index))}>Remove {row.symbol || 'row'}</button>
      </div>)}
      <button type="button" disabled={rows.length >= 100} onClick={() => setRows([...rows, { symbol: '', targetPercent: '' }])}>Add target</button>
      <button type="submit">{saving ? 'Saving…' : 'Save targets'}</button>
    </fieldset></form>
    {error && <p role="alert">{error}</p>}{message && <p role="status">{message}</p>}
    <button type="button" onClick={() => setRevision(value => value + 1)}>Refresh drift</button>
    {driftError && <p role="alert">{driftError}</p>}
    {!drift && !driftError && <p role="status">Calculating drift…</p>}
    {drift && <div className="table-scroll"><table>
      <thead><tr><th>Symbol</th><th>Target</th><th>Actual</th><th>Drift (pp)</th></tr></thead>
      <tbody>{drift.map(row => <tr key={row.symbol}>
        <td>{row.symbol}</td><td>{Number(row.targetPercent).toFixed(2)}%</td><td>{Number(row.actualPercent).toFixed(2)}%</td>
        <td><meter min="-100" max="100" value={row.driftPercentagePoints} aria-label={`${row.symbol} drift`} /> {Number(row.driftPercentagePoints) > 0 ? '+' : ''}{Number(row.driftPercentagePoints).toFixed(2)}</td>
      </tr>)}</tbody>
    </table>{drift.length === 0 && <p>No targets or holdings yet.</p>}</div>}
  </section>
}
