import { money } from '../portfolioData.js'

export default function PortfolioAnalytics({ summary, holdings, transactions, loading, error }) {
  const metrics = [
    ['Total market value', summary ? money(summary.totalMarketValue) : '—'],
    ['Total gain / loss', summary ? money(summary.totalGainLoss) : '—'],
    ['Holdings', holdings ? holdings.length : '—'],
    ['Transactions', transactions ? transactions.length : '—'],
  ]
  return <section className="summary-region" aria-label="Portfolio summary" aria-busy={loading}>
    <dl className="summary-bar">{metrics.map(([label, value]) => <div key={label}>
      <dt>{label}</dt><dd className={loading ? 'metric-loading' : ''}>{loading ? '…' : value}</dd>
    </div>)}</dl>
    <div className="summary-footnote"><span>Valued at the previous trading session’s close.</span>
      {summary && <span>Unrealized {money(summary.unrealizedGainLoss)} · Realized {money(summary.realizedGainLoss)} · Cost basis {money(summary.totalCostBasis)}</span>}
    </div>
    {loading && <p className="muted" role="status">Updating portfolio data…</p>}
    {error && <p className="auth-feedback auth-error" role="alert">{error}</p>}
  </section>
}
