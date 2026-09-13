import { useEffect, useState } from 'react'

const money = value => Number(value).toLocaleString('en-US', { style: 'currency', currency: 'USD' })

export default function PortfolioAnalytics({ portfolioId }) {
  const [summary, setSummary] = useState(null)
  const [error, setError] = useState('')
  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      try {
        const response = await fetch(`/api/portfolios/${portfolioId}/analytics`, { signal: controller.signal })
        if (!response.ok) {
          const body = await response.json().catch(() => null)
          throw new Error(body?.message || 'Unable to load analytics.')
        }
        const data = await response.json()
        if (!controller.signal.aborted) setSummary(data)
      } catch (error) {
        if (!controller.signal.aborted) setError(error.message)
      }
    }
    load()
    return () => controller.abort()
  }, [portfolioId])

  return (
    <section aria-labelledby="analytics-heading">
      <h3 id="analytics-heading">Portfolio analytics</h3>
      <p>Previous-session closing prices, cached for up to six hours. Gain/loss uses moving average cost, excluding fees and dividends.</p>
      {error && <p role="alert">{error} Use Refresh history and holdings to retry.</p>}
      {!summary && !error && <p role="status">Loading analytics…</p>}
      {summary && <>
        <dl className="analytics-summary">
          <div><dt>Total market value</dt><dd>{money(summary.totalMarketValue)}</dd></div>
          <div><dt>Remaining cost basis</dt><dd>{money(summary.totalCostBasis)}</dd></div>
          <div><dt>Unrealized gain/loss</dt><dd>{money(summary.unrealizedGainLoss)}</dd></div>
          <div><dt>Realized gain/loss</dt><dd>{money(summary.realizedGainLoss)}</dd></div>
          <div><dt>Total gain/loss</dt><dd>{money(summary.totalGainLoss)}</dd></div>
        </dl>
        {summary.assets.length === 0 ? <p>No current allocations.</p> : <div className="table-scroll"><table>
          <thead><tr><th>Asset</th><th>Market value</th><th>Weight</th><th>Unrealized gain/loss</th></tr></thead>
          <tbody>{summary.assets.map(asset => <tr key={asset.symbol}>
            <td>{asset.symbol}</td><td>{money(asset.marketValue)}</td>
            <td><meter min="0" max="100" value={asset.weightPercent} aria-label={`${asset.symbol} allocation`} /> {Number(asset.weightPercent).toFixed(2)}%</td>
            <td>{money(asset.unrealizedGainLoss)}</td>
          </tr>)}</tbody>
        </table></div>}
      </>}
    </section>
  )
}
