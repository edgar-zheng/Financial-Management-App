export default function TransactionHistory({ transactions }) {
  return (
    <section aria-labelledby="history-heading">
      <h3 id="history-heading">Transaction History</h3>
      {transactions.length === 0 ? <p>No transactions yet.</p> : (
        <div className="table-scroll"><table>
          <thead><tr><th scope="col">Symbol</th><th scope="col">Type</th><th scope="col">Quantity</th><th scope="col">Price</th><th scope="col">Timestamp</th></tr></thead>
          <tbody>{transactions.map(transaction => (
            <tr key={transaction.id}>
              <td>{transaction.symbol}</td><td>{transaction.type}</td><td>{transaction.quantity}</td>
              <td>{Number(transaction.price).toLocaleString('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 8 })}</td>
              <td>{transaction.timestamp.replace('T', ' ')}</td>
            </tr>
          ))}</tbody>
        </table></div>
      )}
    </section>
  )
}
