export default function HoldingsTable({ holdings }) {
  return (
    <section aria-labelledby="holdings-heading">
      <h3 id="holdings-heading">Holdings</h3>
      {holdings.length === 0 ? <p>No current holdings.</p> : (
        <table>
          <thead><tr><th scope="col">Symbol</th><th scope="col">Shares</th></tr></thead>
          <tbody>{holdings.map(holding => (
            <tr key={holding.symbol}><td>{holding.symbol}</td><td>{holding.quantity}</td></tr>
          ))}</tbody>
        </table>
      )}
    </section>
  )
}
