import { useEffect, useRef, useState } from 'react'
import { apiFetch } from '../api.js'

export default function PortfolioSelection({ onOpen }) {
  const heading = useRef(null)
  useEffect(() => { heading.current?.focus() }, [])
  const [name, setName] = useState('')
  const [creating, setCreating] = useState(false)
  const [createdPortfolio, setCreatedPortfolio] = useState(null)
  const [createError, setCreateError] = useState('')
  const [inputId, setInputId] = useState('')
  const [request, setRequest] = useState({ id: '', revision: 0 })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!request.id) return
    const controller = new AbortController()
    setLoading(true)
    setError('')

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
        if (!controller.signal.aborted) onOpen(data)
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
  }, [request, onOpen])

  function submit(event) {
    event.preventDefault()
    if (!/^[1-9][0-9]*$/.test(inputId)) { setError('Enter a positive portfolio ID.'); return }
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
        throw new Error(response.status === 400 ? body?.fieldErrors?.name || 'Please enter a valid portfolio name.' : 'Unable to create portfolio. Please try again.')
      }
      const data = await response.json()
      setCreatedPortfolio(data)
      setInputId(String(data.id))
      setName('')
    } catch (error) {
      setCreateError(error instanceof TypeError
        ? 'Unable to confirm creation. Check the backend before retrying.'
        : error.message)
    } finally {
      setCreating(false)
    }
  }

  return <main className="workspace selection">
    <header className="page-heading"><p className="auth-eyebrow">YOUR INVESTMENTS, IN VIEW</p>
      <h1 tabIndex={-1} ref={heading}>A place for your next chapter.</h1><p>Open your portfolio or start building a new one.</p></header>
    <div className="selection-grid">
      <section className="surface" aria-labelledby="find-heading"><span className="step-mark" aria-hidden="true">01</span>
        <h2 id="find-heading">Find a portfolio</h2><p className="muted">Pick up where you left off. Enter a portfolio ID owned by your account.</p>
        <form className="workspace-form" onSubmit={submit} aria-busy={loading}>
          <label htmlFor="portfolio-id">Portfolio ID</label>
          <input id="portfolio-id" type="text" inputMode="numeric" pattern="[1-9][0-9]*" required
            value={inputId} disabled={loading} placeholder="e.g. 17" onChange={event => setInputId(event.target.value)} />
          <button className="primary" disabled={loading}>{loading ? 'Finding portfolio…' : 'Open portfolio →'}</button>
        </form>{error && <p className="auth-feedback auth-error" role="alert">{error}</p>}
      </section>
      <section className="surface" aria-labelledby="create-heading"><span className="step-mark" aria-hidden="true">02</span>
        <h2 id="create-heading">Create a portfolio</h2><p className="muted">Give your investments a home. Start with a name that means something to you.</p>
        {createdPortfolio ? <div className="creation-success" role="status">
          <p className="auth-eyebrow">YOUR PORTFOLIO IS READY</p><h3>{createdPortfolio.name}</h3>
          <p className="portfolio-number">Portfolio ID #{createdPortfolio.id}</p>
          <p>Save this ID. You’ll use it to open your portfolio next time.</p>
          <button className="primary" onClick={() => onOpen(createdPortfolio)}>Continue to portfolio →</button>
        </div> : <form className="workspace-form" onSubmit={createPortfolio} aria-busy={creating}>
          <label htmlFor="portfolio-name">Portfolio name</label>
          <input id="portfolio-name" required maxLength={255} value={name} disabled={creating}
            onChange={event => setName(event.target.value)} placeholder="Long-Term Investments" />
          <button className="primary" disabled={creating}>{creating ? 'Creating…' : 'Create portfolio →'}</button>
        </form>}{createError && <p className="auth-feedback auth-error" role="alert">{createError}</p>}
      </section>
    </div>
  </main>
}
