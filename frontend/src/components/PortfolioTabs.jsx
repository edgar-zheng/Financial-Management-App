import { useRef } from 'react'
const tabs = [['add', 'Add transaction'], ['history', 'History'], ['holdings', 'Holdings']]
export default function PortfolioTabs({ value, onChange }) {
  const buttons = useRef([])
  function navigate(event, index) {
    let next
    if (event.key === 'ArrowRight') next = (index + 1) % tabs.length
    if (event.key === 'ArrowLeft') next = (index + tabs.length - 1) % tabs.length
    if (event.key === 'Home') next = 0
    if (event.key === 'End') next = tabs.length - 1
    if (next === undefined) return
    event.preventDefault(); onChange(tabs[next][0]); buttons.current[next]?.focus()
  }
  return <div className="segmented operation-tabs" role="tablist" aria-label="Portfolio operations">
    {tabs.map(([id, label], index) => <button key={id} ref={element => { buttons.current[index] = element }}
      id={`tab-${id}`} role="tab" aria-selected={value === id} aria-controls={`panel-${id}`} tabIndex={value === id ? 0 : -1}
      onClick={() => onChange(id)} onKeyDown={event => navigate(event, index)}>{label}</button>)}
  </div>
}
