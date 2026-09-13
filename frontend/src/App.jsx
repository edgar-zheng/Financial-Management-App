import { useEffect, useState } from 'react'

export default function App() {
  const [inputId, setInputId] = useState('1')
  const [request, setRequest] = useState({ id: '1', revision: 0 })
  const [portfolio, setPortfolio] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError('')
    setPortfolio(null)

    async function loadPortfolio() {
      try {
        const response = await fetch(`/api/portfolios/${request.id}`, {
          signal: controller.signal,
        })
        if (!response.ok) {
          throw new Error(response.status === 404
            ? 'Portfolio not found.'
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

  return (
    <main>
      <h1>Portfolio Tracker</h1>
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
        </section>
      )}
    </main>
  )
}
