import { useEffect, useState } from 'react'
import TransactionForm from './TransactionForm.jsx'
import TransactionHistory from './TransactionHistory.jsx'
import HoldingsTable from './HoldingsTable.jsx'
import PortfolioAnalytics from './PortfolioAnalytics.jsx'

export default function PortfolioActivity({ portfolioId }) {
  const [revision, setRevision] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [valuations, setValuations] = useState(null)
  const [priceError, setPriceError] = useState('')

  function refresh() {
    setData(null)
    setError('')
    setValuations(null)
    setPriceError('')
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
    async function loadValuations() {
      try {
        const response = await fetch(`/api/portfolios/${portfolioId}/holdings/valuation`, {
          signal: controller.signal,
        })
        if (!response.ok) {
          const body = await response.json().catch(() => null)
          throw new Error(body?.message || `Unable to load market values (${response.status}).`)
        }
        const values = await response.json()
        if (!controller.signal.aborted) setValuations(values)
      } catch (error) {
        if (!controller.signal.aborted) setPriceError(error.message)
      }
    }
    load()
    loadValuations()
    return () => controller.abort()
  }, [portfolioId, revision])

  return (
    <>
      <PortfolioAnalytics key={`${portfolioId}-${revision}`} portfolioId={portfolioId} />
      <TransactionForm portfolioId={portfolioId} onCreated={refresh} />
      <button type="button" onClick={refresh}>Refresh history and holdings</button>
      {!data && !error && <p role="status">Loading history and holdings…</p>}
      {error && <p role="alert">{error}</p>}
      {data && <>
        <TransactionHistory transactions={data.transactions} />
        <HoldingsTable holdings={data.holdings} valuations={valuations} priceError={priceError} />
      </>}
    </>
  )
}
