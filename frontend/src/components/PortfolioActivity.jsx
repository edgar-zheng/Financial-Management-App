import { useEffect, useState } from 'react'
import PortfolioTabs from './PortfolioTabs.jsx'
import { loadPortfolioData } from '../portfolioData.js'
import AllocationChart from './AllocationChart.jsx'
import AllocationTargets from './AllocationTargets.jsx'
import TransactionForm from './TransactionForm.jsx'
import TransactionHistory from './TransactionHistory.jsx'
import HoldingsTable from './HoldingsTable.jsx'
import PortfolioAnalytics from './PortfolioAnalytics.jsx'

export default function PortfolioActivity({ portfolioId }) {
  const [tab, setTab] = useState('add')
  const [revision, setRevision] = useState(0)
  const [snapshot, setSnapshot] = useState(null)
  function refresh() { setSnapshot(null); setRevision(value => value + 1) }
  useEffect(() => {
    const controller = new AbortController()
    loadPortfolioData(portfolioId, controller.signal).then(result => {
      if (!controller.signal.aborted) setSnapshot(result)
    }).catch(() => {}) // Aborted requests never publish data from an older refresh.
    return () => controller.abort()
  }, [portfolioId, revision])
  const data = key => snapshot?.[key]?.data
  const error = key => snapshot?.[key]?.error
  return <>
    <PortfolioAnalytics summary={data('summary')} holdings={data('holdings')} transactions={data('transactions')}
      loading={!snapshot} error={error('summary')} />
    <div className="dashboard-toolbar"><span className="muted">Portfolio workspace</span>
      <button className="secondary" disabled={!snapshot} onClick={refresh}>↻ Refresh data</button></div>
    <div className="dashboard-grid">
      <div className="surface analytics-panel"><AllocationChart summary={data('summary')} targets={data('targets')} drift={data('drift')}
        loading={!snapshot} error={error('summary')} targetError={error('targets')} driftError={error('drift')} /><AllocationTargets portfolioId={portfolioId} targets={data('targets')} holdings={data('holdings')}
        loading={!snapshot} error={error('targets')} onSaved={refresh} /></div>
      <aside className="surface operations-panel" aria-label="Portfolio operations">
        <div className="section-heading"><div><p className="auth-eyebrow">MAKE YOUR NEXT MOVE</p><h2>Portfolio menu</h2></div></div>
        <PortfolioTabs value={tab} onChange={setTab} />
        <div id="panel-add" role="tabpanel" aria-labelledby="tab-add" hidden={tab !== 'add'} tabIndex={0}>
          <TransactionForm portfolioId={portfolioId} onCreated={refresh} refreshing={!snapshot} />
        </div>
        <div id="panel-history" role="tabpanel" aria-labelledby="tab-history" hidden={tab !== 'history'} tabIndex={0}>
          {!snapshot && <p role="status">Loading transaction history…</p>}
          {error('transactions') && <p role="alert">{error('transactions')}</p>}
          {data('transactions') && <TransactionHistory transactions={data('transactions')} />}
        </div>
        <div id="panel-holdings" role="tabpanel" aria-labelledby="tab-holdings" hidden={tab !== 'holdings'} tabIndex={0}>
          {!snapshot && <p role="status">Loading holdings…</p>}
          {error('holdings') && <p role="alert">{error('holdings')}</p>}
          {data('holdings') && <HoldingsTable holdings={data('holdings')} valuations={data('valuations')} priceError={error('valuations')} assets={data('summary')?.assets} />}
        </div>
      </aside>
    </div>
  </>
}
