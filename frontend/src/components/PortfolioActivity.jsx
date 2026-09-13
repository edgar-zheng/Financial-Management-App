import { useEffect, useState } from 'react'
import TransactionForm from './TransactionForm.jsx'
import TransactionHistory from './TransactionHistory.jsx'
import HoldingsTable from './HoldingsTable.jsx'

export default function PortfolioActivity({ portfolioId }) {
  const [revision, setRevision] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  function refresh() {
    setData(null)
    setError('')
    setRevision(value => value + 1)
  }

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      try {
        const [transactions, holdings] = await Promise.all(['transactions', 'holdings'].map(async resource => {
          const response = await fetch(`/api/portfolios/${portfolioId}/${resource}`, { signal: controller.signal })
          if (!response.ok) throw new Error(`Unable to load ${resource} (${response.status}).`)
          return response.json()
        }))
        if (!controller.signal.aborted) setData({ transactions, holdings })
      } catch (error) {
        if (!controller.signal.aborted) setError(`${error.message} Use Refresh history and holdings to try again.`)
      }
    }
    load()
    return () => controller.abort()
  }, [portfolioId, revision])

  return (
    <>
      <TransactionForm portfolioId={portfolioId} onCreated={refresh} />
      <button type="button" onClick={refresh}>Refresh history and holdings</button>
      {!data && !error && <p role="status">Loading history and holdings…</p>}
      {error && <p role="alert">{error}</p>}
      {data && <>
        <TransactionHistory transactions={data.transactions} />
        <HoldingsTable holdings={data.holdings} />
      </>}
    </>
  )
}
