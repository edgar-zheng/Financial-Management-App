// Muted companions to the login's forest and pale-green accents. Never random per render.
const palette = ['#205443', '#729746', '#46798b', '#a57b39', '#776487', '#a95749', '#466d5c', '#68889a', '#936444', '#858448']
export function tickerColor(ticker) {
  let hash = 0
  for (const character of ticker.trim().toUpperCase()) hash = (hash * 31 + character.charCodeAt(0)) >>> 0
  return palette[hash % palette.length]
}
