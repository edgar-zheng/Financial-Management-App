export default function TransactionHistory({ transactions }) {
  return <section aria-labelledby="history-heading">
    <div className="list-heading"><h3 id="history-heading">Transaction history</h3><span>{transactions.length} trades</span></div>
    {transactions.length === 0 ? <div className="panel-empty"><h4>No trades yet.</h4><p>Add a transaction to start your history.</p></div>
      : <ol className="activity-list">{[...transactions].reverse().map(transaction => <li key={transaction.id}>
        <div className="activity-row"><div><span className={`trade-badge ${transaction.type.toLowerCase()}`}>{transaction.type}</span><strong>{transaction.symbol}</strong></div>
          <strong>{Number(transaction.price).toLocaleString('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 8 })} <small>/ share</small></strong></div>
        <div className="activity-detail"><span>{transaction.quantity} shares</span><time dateTime={transaction.timestamp}>{transaction.timestamp.replace('T', ' ')}</time></div>
      </li>)}</ol>}
  </section>
}
