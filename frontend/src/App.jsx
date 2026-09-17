import { useEffect, useRef, useState } from 'react'
import SessionGate from './components/SessionGate.jsx'
import PortfolioSelection from './components/PortfolioSelection.jsx'
import PortfolioActivity from './components/PortfolioActivity.jsx'

export default function App() {
  return <SessionGate><PortfolioWorkspace /></SessionGate>
}

function PortfolioWorkspace() {
  const [portfolio, setPortfolio] = useState(null)
  const heading = useRef(null)
  useEffect(() => { if (portfolio) heading.current?.focus() }, [portfolio])
  if (!portfolio) return <PortfolioSelection onOpen={setPortfolio} />
  return <main className="workspace">
    <header className="page-heading dashboard-heading">
      <div><p className="auth-eyebrow">PORTFOLIO OVERVIEW · ID #{portfolio.id}</p><h1 tabIndex={-1} ref={heading}>{portfolio.name}</h1>
        <p>Your investments. A clearer perspective.</p></div>
      <button className="secondary" onClick={() => setPortfolio(null)}>← Switch portfolio</button>
    </header>
    <PortfolioActivity key={portfolio.id} portfolioId={portfolio.id} />
  </main>
}
