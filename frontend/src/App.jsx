import { apiFetch } from './api.js'
import SessionGate from './components/SessionGate.jsx'
import { useEffect, useState } from 'react'
import PortfolioActivity from './components/PortfolioActivity.jsx'

export default function App() {
  return <SessionGate><PortfolioDashboard /></SessionGate>
}

function PortfolioDashboard() {
  const [name, setName] = useState('')
  const [creating, setCreating] = useState(false)
  const [createdPortfolio, setCreatedPortfolio] = useState(null)
  const [createError, setCreateError] = useState('')
  const [inputId, setInputId] = useState('')
  const [request, setRequest] = useState({ id: '', revision: 0 })
  const [portfolio, setPortfolio] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!request.id) return
    const controller = new AbortController()
    setLoading(true)
    setError('')
    setPortfolio(null)

    async function loadPortfolio() {
      try {
        const response = await apiFetch(`/api/portfolios/${request.id}`, {
          signal: controller.signal,
        })
        if (!response.ok) {
          throw new Error(response.status === 404
            ? 'Portfolio not found or not owned by this account.'
            : 'Unable to load portfolio. Please try again.')
        }
        const data = await response.json()
        if (!controller.signal.aborted) setPortfolio(data)
      } catch (error) {
        if (!controller.signal.aborted) {
          setError(error instanceof TypeError
            ? 'Unable to connect. Check that the backend is running.'
            : error.message)
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }

    loadPortfolio()
    return () => controller.abort()
  }, [request])

  function submit(event) {
    event.preventDefault()
    setRequest(previous => ({ id: inputId, revision: previous.revision + 1 }))
  }

  async function createPortfolio(event) {
    event.preventDefault()
    if (creating) return
    setCreatedPortfolio(null)
    setCreateError('')
    const trimmedName = name.trim()
    if (!trimmedName) {
      setCreateError('Portfolio name must not be blank.')
      return
    }
    setCreating(true)
    try {
      const response = await apiFetch('/api/portfolios', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: trimmedName }),
      })
      if (!response.ok) {
        const body = await response.json().catch(() => null)
        throw new Error(body?.fieldErrors?.name || body?.message || 'Unable to create portfolio.')
      }
      const data = await response.json()
      setCreatedPortfolio(data)
      setInputId(String(data.id))
      setRequest(previous => ({ id: String(data.id), revision: previous.revision + 1 }))
      setName('')
    } catch (error) {
      setCreateError(error instanceof TypeError
        ? 'Unable to confirm creation. Check the backend before retrying.'
        : error.message)
    } finally {
      setCreating(false)
    }
  }

  return (
    <main>
      <h1>Portfolio Tracker</h1>
      <section aria-labelledby="create-heading">
        <h2 id="create-heading">Create a portfolio</h2>
        <form onSubmit={createPortfolio}>
          <label htmlFor="portfolio-name">Portfolio Name</label>
          <input id="portfolio-name" required maxLength={255} value={name}
            disabled={creating} onChange={event => setName(event.target.value)}
            placeholder="My Portfolio" />
          <button type="submit" disabled={creating}>
            {creating ? 'Creating…' : 'Create Portfolio'}
          </button>
        </form>
        {createError && <p role="alert">{createError}</p>}
        {createdPortfolio && (
          <div role="status">
            <p>Portfolio created successfully.</p>
            <pre>{JSON.stringify(createdPortfolio, null, 2)}</pre>
          </div>
        )}
      </section>
      <h2>Find a portfolio</h2>
      <p>Create a portfolio or enter the ID of one owned by your account.</p>
      <form onSubmit={submit}>
        <label htmlFor="portfolio-id">Portfolio ID</label>
        <input id="portfolio-id" type="text" inputMode="numeric" pattern="[1-9][0-9]*"
          required value={inputId} onChange={event => setInputId(event.target.value)} />
        <button type="submit">Load portfolio</button>
      </form>
      {loading && <p role="status">Loading portfolio…</p>}
      {error && <p role="alert">{error}</p>}
      {!loading && portfolio && (
        <section aria-label="Portfolio details">
          <h2>Portfolio: {portfolio.name}</h2>
          <p>ID: {portfolio.id}</p>
          <PortfolioActivity key={portfolio.id} portfolioId={portfolio.id} />
        </section>
      )}
    </main>
  )
}
