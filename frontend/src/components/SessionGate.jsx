import { useEffect, useState } from 'react'
import { apiFetch } from '../api.js'

export default function SessionGate({ children }) {
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

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    setBusy(true); setError(''); setMessage('')
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
      setPassword('')
      if (registering) {
        setRegistering(false); setMessage('Account created. Sign in with your new password.')
      } else {
        const me = await apiFetch('/api/auth/me')
        if (!me.ok) throw new Error('Unable to confirm your session. Please sign in again.')
        setAccount(await me.json())
      }
    } catch (error) { setError(error.message) }
    finally { setBusy(false) }
  }

  async function logout() {
    if (busy) return
    setBusy(true); setError('')
    try {
      const response = await apiFetch('/api/auth/logout', { method: 'POST' })
      if (!response.ok) throw new Error('Logout failed. Please retry.')
      setAccount(null); setPassword(''); setMessage('Signed out.')
    } catch (error) { setError(error.message) }
    finally { setBusy(false) }
  }

  if (checking) return <main><p role="status">Checking session…</p></main>
  if (account) return <>
    <header className="session-bar"><span>Signed in as {account.email}</span>
      <button type="button" disabled={busy} onClick={logout}>Sign out</button>
      {error && <p role="alert">{error}</p>}
    </header>
    {children}
  </>
  return <main>
    <h1>Portfolio Tracker</h1><h2>{registering ? 'Create an account' : 'Sign in'}</h2>
    <form onSubmit={submit}><fieldset disabled={busy}>
      <label>Email<input type="email" required autoComplete="username" value={email} onChange={event => setEmail(event.target.value)} /></label>
      <label>Password<input type="password" required minLength={registering ? 12 : undefined}
        autoComplete={registering ? 'new-password' : 'current-password'} value={password} onChange={event => setPassword(event.target.value)} /></label>
      <button type="submit">{busy ? 'Please wait…' : registering ? 'Create account' : 'Sign in'}</button>
      <button type="button" onClick={() => { setRegistering(!registering); setError(''); setMessage(''); setPassword('') }}>
        {registering ? 'Back to sign in' : 'Create an account'}
      </button>
    </fieldset></form>
    {registering && <p>Use at least 12 characters and at most 72 UTF-8 bytes for your password.</p>}
    {error && <p role="alert">{error}</p>}{message && <p role="status">{message}</p>}
  </main>
}
