function money(value, currency) {
  return Number(value).toLocaleString('en-US', {
    style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 8,
  })
}

export default function HoldingsTable({ holdings, valuations, priceError }) {
  const prices = new Map((valuations || []).map(value => [value.symbol, value]))
  return (
    <section aria-labelledby="holdings-heading">
      <h3 id="holdings-heading">Holdings</h3>
      <p>Valued using the previous completed trading session’s close. Prices may be cached for six hours.</p>
      {priceError && <p role="alert">Market values unavailable: {priceError} Quantities are still shown.</p>}
      {holdings.length === 0 ? <p>No current holdings.</p> : (
        <div className="table-scroll"><table>
          <thead><tr><th scope="col">Symbol</th><th scope="col">Shares</th>
            <th scope="col">Closing price</th><th scope="col">Market value</th>
            <th scope="col">Session date</th></tr></thead>
          <tbody>{holdings.map(holding => {
            const price = prices.get(holding.symbol)
            const unavailable = priceError || valuations ? 'Unavailable' : 'Loading…'
            return (
              <tr key={holding.symbol}>
                <td>{holding.symbol}</td><td>{holding.quantity}</td>
                <td>{price ? money(price.closingPrice, price.currency) : unavailable}</td>
                <td>{price ? money(price.marketValue, price.currency) : unavailable}</td>
                <td>{price?.asOf ? new Date(price.asOf).toLocaleDateString('en-US', {
                  timeZone: 'America/New_York',
                }) : 'Unavailable'}</td>
              </tr>
            )
          })}</tbody>
        </table></div>
      )}
    </section>
  )
}
