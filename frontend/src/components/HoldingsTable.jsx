import { money, percent } from '../portfolioData.js'
import { tickerColor } from '../chartColors.js'
export default function HoldingsTable({ holdings, valuations, priceError, assets }) {
  const prices = new Map((valuations || []).map(value => [value.symbol, value]))
  const allocations = new Map((assets || []).map(value => [value.symbol, value]))
  return <section aria-labelledby="holdings-heading">
    <div className="list-heading"><h3 id="holdings-heading">Your holdings</h3><span>{holdings.length} assets</span></div>
    {priceError && <p className="auth-feedback auth-error" role="alert">Market values are unavailable. Share quantities are still shown.</p>}
    {holdings.length === 0 ? <div className="panel-empty"><h4>A fresh start.</h4><p>Your holdings will appear after your first purchase.</p></div>
      : <ul className="activity-list">{holdings.map(holding => {
        const price = prices.get(holding.symbol)
        const allocation = allocations.get(holding.symbol)
        return <li key={holding.symbol}>
          <div className="activity-row"><strong className="ticker-label"><i style={{ backgroundColor: tickerColor(holding.symbol) }} aria-hidden="true" />{holding.symbol}</strong>
            <strong>{price ? money(price.marketValue) : 'Value unavailable'}</strong></div>
          <div className="activity-detail"><span>{holding.quantity} shares</span>{allocation && <span>{percent(allocation.weightPercent)} allocation</span>}</div>
          {price && <div className="holding-meta"><span>Close {Number(price.closingPrice).toLocaleString('en-US', { style: 'currency', currency: price.currency, maximumFractionDigits: 8 })}</span>
            {price.asOf && <span>{new Date(price.asOf).toLocaleDateString('en-US', { timeZone: 'America/New_York' })}</span>}</div>}
          {allocation && <p className="holding-gain">Unrealized gain / loss <strong>{money(allocation.unrealizedGainLoss)}</strong></p>}
        </li>
      })}</ul>}
    <p className="panel-note">Previous-session closing prices, cached up to six hours. Gain/loss uses moving average cost, excluding fees and dividends.</p>
  </section>
}
