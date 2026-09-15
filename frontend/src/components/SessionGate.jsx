import { useEffect, useRef, useState } from 'react'
import { apiFetch } from '../api.js'

export default function SessionGate({ children }) {
  const [showPassword, setShowPassword] = useState(false)
  const feedback = useRef(null)
  const passwordInput = useRef(null)
  const [account, setAccount] = useState(null)
  const [checking, setChecking] = useState(true)
  const [registering, setRegistering] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    function expired() { setAccount(null); setError('Your session expired. Please sign in again.') }
    window.addEventListener('session-expired', expired)
    async function check() {
      try {
        const response = await apiFetch('/api/auth/me', { signal: controller.signal })
        if (response.ok) {
          const user = await response.json()
          if (!controller.signal.aborted) setAccount(user)
        } else if (response.status !== 401) throw new Error('Unable to check your session.')
      } catch (error) {
        if (!controller.signal.aborted) setError('Unable to connect. Check that the backend is running.')
      } finally { if (!controller.signal.aborted) setChecking(false) }
    }
    check()
    return () => { controller.abort(); window.removeEventListener('session-expired', expired) }
  }, [])

  useEffect(() => {
    if (error) feedback.current?.focus()
  }, [error])

  function changeMode() {
    setRegistering(!registering)
    setError(''); setMessage(''); setPassword(''); setShowPassword(false)
  }

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    setError(''); setMessage('')
    if (registering && new TextEncoder().encode(password).length > 72) {
      setError('Choose a password of at most 72 bytes. Accented characters and emoji can use more than one byte.'); return
    }
    setBusy(true)
    try {
      const path = registering ? '/api/auth/register' : '/api/auth/login'
      const response = await apiFetch(path, {
        method: 'POST',
        headers: { 'Content-Type': registering ? 'application/json' : 'application/x-www-form-urlencoded' },
        body: registering ? JSON.stringify({ email: email.trim(), password })
          : new URLSearchParams({ email: email.trim(), password }),
      })
      if (!response.ok) {
        const body = await response.json().catch(() => null)
        throw new Error(Object.values(body?.fieldErrors || {}).join('; ') || body?.message || 'Authentication failed.')
      }
      setPassword(''); setShowPassword(false)
      if (registering) {
        setRegistering(false); setMessage('Your account is ready. Sign in to create your first portfolio.'); passwordInput.current?.focus()
      } else {
        const me = await apiFetch('/api/auth/me')
        if (!me.ok) throw new Error('Unable to confirm your session. Please sign in again.')
        setAccount(await me.json())
      }
    } catch (error) { setError(error instanceof TypeError ? 'We couldn’t reach the server. Check your connection and try again.' : error.message) }
    finally { setBusy(false) }
  }

  async function logout() {
    if (busy) return
    setBusy(true); setError('')
    try {
      const response = await apiFetch('/api/auth/logout', { method: 'POST' })
      if (!response.ok) throw new Error('Logout failed. Please retry.')
      setAccount(null); setPassword(''); setShowPassword(false); setRegistering(false); setMessage('You’ve been signed out.')
    } catch (error) { setError(error.message) }
    finally { setBusy(false) }
  }

  if (checking) return <main className="auth-loading"><span className="auth-mark" aria-hidden="true">P</span><p role="status">Checking your session…</p></main>
  if (account) return <>
    <header className="account-bar">
      <div className="account-brand"><span className="auth-mark" aria-hidden="true">P</span><strong>Portfolio Tracker</strong></div>
      <div className="account-controls"><div><span className="account-caption">Signed in</span><span className="account-email">{account.email}</span></div>
        <button className="account-signout" type="button" disabled={busy} onClick={logout}>{busy ? 'Signing out…' : 'Sign out'}</button>
      </div>
      {error && <p className="auth-feedback auth-error" role="alert" tabIndex={-1} ref={feedback}>{error}</p>}
    </header>
    {children}
  </>
  return <main className="auth-shell">
    <aside className="auth-intro">
      <div className="auth-brand"><span className="auth-mark" aria-hidden="true">P</span><span>Portfolio Tracker</span></div>
      <div><p className="auth-eyebrow">YOUR INVESTMENTS, IN VIEW</p><h1>A clearer picture<br />of your portfolio.</h1>
        <p className="auth-description">Track your trades, understand your holdings, and see how your allocations compare with your goals.</p></div>
      <div className="auth-features"><span>Holdings &amp; performance</span><span>Allocation &amp; drift</span></div>
    </aside>
    <section className="auth-panel" aria-labelledby="auth-title">
      <p className="auth-eyebrow">{registering ? 'GET STARTED' : 'WELCOME BACK'}</p>
      <h2 id="auth-title">{registering ? 'Create your account' : 'Sign in'}</h2>
      <p className="auth-subtitle">{registering ? 'Start with an account. Build your portfolio from there.' : 'Your portfolio is right where you left it.'}</p>
      {message && <p className="auth-feedback auth-success" role="status">{message}</p>}
      {error && <p id="auth-error" className="auth-feedback auth-error" role="alert" tabIndex={-1} ref={feedback}>{error}</p>}
      <form className="auth-form" onSubmit={submit} aria-busy={busy}>
        <fieldset disabled={busy}>
          <label htmlFor="auth-email">Email address</label>
          <input id="auth-email" type="email" required maxLength={254} autoComplete="username" autoCapitalize="none" spellCheck={false}
            placeholder="you@example.com" value={email} onChange={event => setEmail(event.target.value)} />
          <label htmlFor="auth-password">Password</label>
          <div className="auth-password">
            <input id="auth-password" ref={passwordInput} type={showPassword ? 'text' : 'password'} required minLength={registering ? 12 : undefined}
              aria-describedby={registering ? 'password-help' : undefined}
              autoComplete={registering ? 'new-password' : 'current-password'} value={password} onChange={event => setPassword(event.target.value)} />
            <button type="button" className="password-toggle" aria-label={showPassword ? 'Hide password' : 'Show password'} aria-pressed={showPassword}
              onClick={() => setShowPassword(!showPassword)}>{showPassword ? 'Hide' : 'Show'}</button>
          </div>
          {registering && <p id="password-help" className="auth-help">At least 12 characters, up to 72 UTF-8 bytes. A few memorable words make a good starting point.</p>}
          <button className="auth-submit" type="submit">{busy ? registering ? 'Creating account…' : 'Signing in…' : registering ? 'Create account' : 'Sign in'}<span aria-hidden="true">→</span></button>
        </fieldset>
      </form>
      <p className="auth-switch">{registering ? 'Already have an account?' : 'New to Portfolio Tracker?'}{' '}
        <button type="button" disabled={busy} onClick={changeMode}>{registering ? 'Sign in' : 'Create an account'}</button>
      </p>
    </section>
  </main>
}
