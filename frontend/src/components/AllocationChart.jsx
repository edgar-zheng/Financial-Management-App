import { useState } from 'react'
import { tickerColor } from '../chartColors.js'
import { money, percent } from '../portfolioData.js'

export default function AllocationChart({ summary, targets, drift, loading, error, targetError, driftError }) {
  const [active, setActive] = useState(null)
  const [mode, setMode] = useState('current')
  const visibleError = mode === 'current' ? error : targetError
  const rows = mode === 'target'
    ? (targets || []).map(row => ({ symbol: row.symbol, weight: Number(row.targetPercent) }))
    : (summary?.assets || []).map(asset => ({ symbol: asset.symbol, weight: Number(asset.weightPercent), value: asset.marketValue }))
  const selected = rows.find(row => row.symbol === active)
  let offset = 0
  return <section className="allocation-chart" aria-labelledby="allocation-title">
    <div className="section-heading"><div><p className="auth-eyebrow">THE BIG PICTURE</p><h2 id="allocation-title">Asset allocation</h2></div></div>
    <div className="segmented chart-toggle" role="group" aria-label="Allocation chart mode">
      {['current', 'target'].map(value => <button key={value} type="button" aria-pressed={mode === value}
        onClick={() => { setMode(value); setActive(null) }}>{value === 'current' ? 'Current allocation' : 'Target allocation'}</button>)}
    </div>
    {loading ? <div className="chart-empty" role="status">Loading allocations…</div>
      : visibleError ? <p className="auth-feedback auth-error" role="alert">{visibleError}</p>
      : !rows.some(row => row.weight > 0) ? <div className="chart-empty"><span className="empty-ring" aria-hidden="true" /><h3>{mode === 'target' ? 'Give your portfolio a direction.' : 'Your allocation starts here.'}</h3><p>{mode === 'target' ? 'Set target allocations to compare your plan with your investments.' : 'Add your first transaction to see how your investments are distributed.'}</p></div>
      : <><div className="donut-wrap">
        <svg viewBox="0 0 240 240" className="donut" aria-hidden="true">
          <circle cx="120" cy="120" r="90" fill="none" stroke="#edf1ed" strokeWidth="30" />
          {rows.filter(row => row.weight > 0).map(row => {
            const start = offset; offset += row.weight
            return <circle key={row.symbol} cx="120" cy="120" r="90" fill="none" stroke={tickerColor(row.symbol)} strokeWidth={active === row.symbol ? 35 : 30}
              pathLength="100" strokeDasharray={`${row.weight} ${100 - row.weight}`} strokeDashoffset={-start} transform="rotate(-90 120 120)"
              onMouseEnter={() => setActive(row.symbol)} onMouseLeave={() => setActive(null)}><title>{row.symbol}: {percent(row.weight)}</title></circle>
          })}
        </svg>
        <div className="donut-center"><span>{selected ? selected.symbol : mode === 'target' ? 'Target allocation' : 'Current allocation'}</span><strong>{selected ? percent(selected.weight) : mode === 'target' ? '100%' : money(summary.totalMarketValue)}</strong><small>{selected?.value != null ? money(selected.value) : `${rows.length} assets`}</small></div>
      </div>
      <ul className="chart-legend" aria-label="Allocation values">{rows.map(row => <li key={row.symbol}>
        <button type="button" onClick={() => setActive(row.symbol)} onFocus={() => setActive(row.symbol)} onBlur={() => setActive(null)} onMouseEnter={() => setActive(row.symbol)} onMouseLeave={() => setActive(null)}>
          <span className="ticker-label"><i style={{ backgroundColor: tickerColor(row.symbol) }} aria-hidden="true" />{row.symbol}</span>
          <span>{percent(row.weight)}{row.value != null && <small>{money(row.value)}</small>}</span>
        </button>
      </li>)}</ul></>}
    <div className="drift-section"><h3>Allocation drift</h3><p className="muted">Actual minus target, in percentage points.</p>
      {loading ? <p role="status">Loading drift…</p> : !targets?.length ? <p className="muted">Set a target to see how your holdings compare.</p>
        : driftError ? <p role="alert">{driftError}</p> : <ul className="drift-list">{(drift || []).map(row => <li key={row.symbol}>
          <span className="ticker-label"><i style={{ backgroundColor: tickerColor(row.symbol) }} aria-hidden="true" />{row.symbol}</span>
          <span>{percent(row.actualPercent)} <small> / {percent(row.targetPercent)} target</small></span>
          <strong>{Number(row.driftPercentagePoints) > 0 ? '+' : ''}{Number(row.driftPercentagePoints).toFixed(2)} pp</strong>
        </li>)}</ul>}
    </div>
  </section>
}
