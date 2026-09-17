import { test, expect } from '@playwright/test'

async function mockApi(page, { signedIn = true, empty = false, priceFailure = false, single = false } = {}) {
  let loggedIn = signedIn
  let targets = empty ? [] : [{ symbol: 'AAPL', targetPercent: 40 }, { symbol: 'MSFT', targetPercent: 60 }]
  let transactions = empty ? [] : [{ id: 1, symbol: 'AAPL', type: 'BUY', quantity: 10, price: 200, timestamp: '2026-09-15T10:00:00' }]
  let holdings = empty ? [] : single ? [{ symbol: 'AAPL', quantity: 10 }] : [{ symbol: 'AAPL', quantity: 10 }, { symbol: 'MSFT', quantity: 5 }]
  const writes = []
  const reads = []
  let failTarget = false
  await page.route('**/api/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname
    const method = request.method()
    const reply = (json, status = 200) => route.fulfill({ status, json })
    if (path === '/api/auth/csrf') return reply({ headerName: 'X-CSRF-TOKEN', token: 'test-token' })
    if (path === '/api/auth/me') return reply(loggedIn ? { email: 'test@example.com' } : {}, loggedIn ? 200 : 401)
    if (path === '/api/auth/login') { loggedIn = true; return route.fulfill({ status: 204 }) }
    if (path === '/api/auth/logout') { loggedIn = false; return route.fulfill({ status: 204 }) }
    if (method === 'POST' && path === '/api/portfolios') return reply({ id: 17, name: request.postDataJSON().name, createdAt: '2026-09-15T10:00:00' }, 201)
    if (path === '/api/portfolios/999') return reply({ message: 'internal details' }, 404)
    if (path === '/api/portfolios/17') return reply({ id: 17, name: 'Retirement Account' })
    if (method === 'POST' && path.endsWith('/transactions')) {
      const body = request.postDataJSON(); writes.push(body)
      if (body.type === 'SELL' && Number(body.quantity) > 10) return reply({}, 409)
      transactions = [...transactions, { ...body, id: transactions.length + 1, timestamp: '2026-09-16T10:00:00' }]
      holdings = [{ symbol: 'AAPL', quantity: body.type === 'SELL' ? 9 : 11 }, { symbol: 'MSFT', quantity: 5 }]
      return reply(transactions.at(-1), 201)
    }
    if (method === 'PUT') {
      if (failTarget) return reply({}, 500)
      targets = request.postDataJSON().targets; writes.push({ targets }); return reply(targets)
    }
    reads.push(path)
    if (path.endsWith('/transactions')) return reply(transactions)
    if (path.endsWith('/holdings')) return reply(holdings)
    if (path.endsWith('/allocations')) return reply(targets)
    if (priceFailure) return reply({ message: 'private upstream details' }, 503)
    if (path.endsWith('/drift')) return reply(targets.map(row => ({ ...row, actualPercent: 50, driftPercentagePoints: 50 - Number(row.targetPercent) })))
    const assets = holdings.map(row => ({ ...row, weightPercent: single ? 100 : 50, marketValue: row.quantity * (row.symbol === 'MSFT' ? 500 : 250), closingPrice: row.symbol === 'MSFT' ? 500 : 250, unrealizedGainLoss: 500, asOf: '2026-09-15T20:00:00Z', currency: 'USD' }))
    if (path.endsWith('/valuation')) return reply(assets)
    if (path.endsWith('/analytics')) return reply({ totalMarketValue: assets.reduce((sum, row) => sum + row.marketValue, 0), totalGainLoss: holdings.length ? 1000 : 0, totalCostBasis: holdings.length ? 4000 : 0, unrealizedGainLoss: holdings.length ? 1000 : 0, realizedGainLoss: 0, assets })
    return reply({}, 404)
  })
  return { writes, reads, failTargets: value => { failTarget = value } }
}
async function openPortfolio(page) {
  await page.goto('/')
  await page.getByLabel('Portfolio ID', { exact: true }).fill('17')
  await page.getByRole('button', { name: 'Open portfolio' }).click()
  await expect(page.getByRole('heading', { name: 'Retirement Account' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Refresh data' })).toBeEnabled()
}

test('login, find, create confirmation and logout preserve the workflow', async ({ page }) => {
  await mockApi(page, { signedIn: false })
  await page.goto('/')
  await page.screenshot({ path: 'test-results/login.png', fullPage: true })
  await page.getByLabel('Email address').fill('test@example.com')
  await page.getByLabel('Password', { exact: true }).fill('test-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.screenshot({ path: 'test-results/selection.png', fullPage: true })
  await page.getByLabel('Portfolio ID', { exact: true }).fill('999')
  await page.getByRole('button', { name: 'Open portfolio' }).click()
  await expect(page.getByRole('alert')).toContainText('not found')
  await expect(page.getByRole('alert')).not.toContainText('internal')
  await page.getByLabel('Portfolio name').fill('   ')
  await page.getByRole('button', { name: 'Create portfolio' }).click()
  await expect(page.getByText('Portfolio name must not be blank.')).toBeVisible()
  await page.getByLabel('Portfolio name').fill('Long-Term Investments')
  await page.getByRole('button', { name: 'Create portfolio' }).click()
  await expect(page.getByText('Portfolio ID #17', { exact: true })).toBeVisible()
  await expect(page.getByText('Save this ID.', { exact: false })).toBeVisible()
  await page.getByRole('button', { name: 'Continue to portfolio' }).click()
  await expect(page.getByRole('heading', { name: 'Long-Term Investments' })).toBeVisible()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible()
})

test('chart modes, precise targets, failed save, success and confirmed reset', async ({ page }) => {
  const api = await mockApi(page)
  await openPortfolio(page)
  await page.getByRole('button', { name: 'Target allocation', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Target allocation', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByLabel('Allocation values')).toContainText('40%')
  const color = await page.getByLabel('Allocation values').locator('i').first().getAttribute('style')
  await page.getByRole('button', { name: 'Current allocation', exact: true }).click()
  await expect(page.getByLabel('Allocation values').locator('i').first()).toHaveAttribute('style', color)
  await page.getByRole('button', { name: 'Edit target allocation' }).click()
  await page.screenshot({ path: 'test-results/target-editor.png', fullPage: true })
  await page.getByLabel('AAPL target percentage', { exact: true }).fill('27')
  await expect(page.getByRole('button', { name: 'Save target', exact: true })).toBeDisabled()
  await expect(page.getByText('13% remaining', { exact: true })).toBeVisible()
  await page.getByLabel('AAPL target percentage', { exact: true }).fill('48')
  await expect(page.getByText('8% over', { exact: true })).toBeVisible()
  await page.getByLabel('AAPL allocation slider').fill('40')
  await expect(page.getByLabel('AAPL target percentage', { exact: true })).toHaveValue('40')
  api.failTargets(true)
  await page.getByRole('button', { name: 'Save target', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Unable to save targets')
  api.failTargets(false)
  await page.getByRole('button', { name: 'Save target', exact: true }).click()
  await expect(page.getByText('Target allocation saved.', { exact: false })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Clear saved targets' })).toBeEnabled()
  page.once('dialog', dialog => dialog.dismiss())
  await page.getByRole('button', { name: 'Clear saved targets' }).click()
  expect(api.writes.filter(write => write.targets?.length === 0)).toHaveLength(0)
  page.once('dialog', dialog => dialog.accept())
  await page.getByRole('button', { name: 'Clear saved targets' }).click()
  await expect(page.getByRole('button', { name: 'Set target allocation' })).toBeEnabled()
})

test('BUY, invalid SELL, valid SELL refresh all views and tabs work by keyboard', async ({ page }) => {
  const api = await mockApi(page)
  await openPortfolio(page)
  const suffixes = ['/analytics', '/transactions', '/holdings', '/allocations', '/drift', '/valuation']
  const initialReads = Object.fromEntries(suffixes.map(suffix => [suffix, api.reads.filter(path => path.endsWith(suffix)).length]))
  await page.getByLabel('Symbol', { exact: true }).fill('AAPL')
  await page.getByLabel('Quantity', { exact: true }).fill('1')
  await page.getByLabel('Price', { exact: true }).fill('250')
  await page.getByRole('button', { name: 'Add transaction', exact: true }).click()
  await expect(page.getByText('Transaction saved.', { exact: true })).toBeVisible()
  await expect.poll(() => api.reads.filter(path => path.endsWith('/analytics')).length).toBe(initialReads['/analytics'] + 1)
  await expect(page.getByLabel('Portfolio summary').getByText('$5,250.00', { exact: true })).toBeVisible()
  await page.getByRole('tab', { name: 'History' }).click()
  await expect(page.getByText('2 trades', { exact: true })).toBeVisible()
  await page.getByRole('tab', { name: 'History' }).press('ArrowRight')
  await expect(page.getByRole('tab', { name: 'Holdings' })).toBeFocused()
  await expect(page.getByText('11 shares', { exact: true })).toBeVisible()
  await page.getByRole('tab', { name: 'Add transaction' }).click()
  await page.getByRole('button', { name: 'SELL', exact: true }).click()
  await page.getByLabel('Quantity', { exact: true }).fill('100')
  await page.getByLabel('Price', { exact: true }).fill('250')
  await page.getByRole('button', { name: 'Add transaction', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('exceeds your available shares')
  await page.getByLabel('Quantity', { exact: true }).fill('1')
  await page.getByRole('button', { name: 'Add transaction', exact: true }).click()
  await expect.poll(() => api.reads.filter(path => path.endsWith('/analytics')).length).toBe(initialReads['/analytics'] + 2)
  for (const suffix of suffixes) expect(api.reads.filter(path => path.endsWith(suffix)).length).toBe(initialReads[suffix] + 2)
})

for (const width of [375, 768, 1440]) test(`empty and populated dashboard fit ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 1000 })
  await mockApi(page, { empty: width === 375 })
  await openPortfolio(page)
  if (width === 375) await expect(page.getByText('Your allocation starts here.')).toBeVisible()
  await expect(page.getByRole('tab', { name: 'Add transaction' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: `test-results/dashboard-${width}.png`, fullPage: true })
})

test('market failure leaves history and holdings usable without raw errors', async ({ page }) => {
  await mockApi(page, { priceFailure: true })
  await openPortfolio(page)
  await expect(page.getByText('private upstream details')).toHaveCount(0)
  await page.getByRole('tab', { name: 'Holdings' }).click()
  await expect(page.getByRole('tabpanel', { name: 'Holdings' }).getByText('10 shares', { exact: true })).toBeVisible()
  await page.getByRole('tab', { name: 'History' }).click()
  await expect(page.getByText('1 trades', { exact: true })).toBeVisible()
})


test('one holding fills the donut and switching away returns to selection', async ({ page }) => {
  await mockApi(page, { single: true })
  await openPortfolio(page)
  await expect(page.getByLabel('Allocation values')).toContainText('100%')
  await expect(page.getByLabel('Allocation values').getByRole('button')).toHaveCount(1)
  await page.getByRole('button', { name: 'Switch portfolio' }).click()
  await expect(page.getByRole('heading', { name: 'Find a portfolio' })).toBeVisible()
  await page.getByLabel('Portfolio ID', { exact: true }).fill('-1')
  await page.getByRole('button', { name: 'Open portfolio' }).click()
  expect(await page.getByLabel('Portfolio ID', { exact: true }).evaluate(input => input.validity.valid)).toBe(false)
})

test('empty target mode and four-decimal precision are usable on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 900 })
  await mockApi(page, { empty: true })
  await openPortfolio(page)
  await page.getByRole('button', { name: 'Target allocation', exact: true }).click()
  await expect(page.getByText('Give your portfolio a direction.')).toBeVisible()
  await page.getByRole('button', { name: 'Set target allocation' }).click()
  await page.getByLabel('Add another ticker (optional)').fill('VOO')
  await page.getByRole('button', { name: 'Add', exact: true }).click()
  await page.getByLabel('VOO target percentage', { exact: true }).fill('99.9999')
  await expect(page.getByRole('button', { name: 'Save target', exact: true })).toBeDisabled()
  await page.getByLabel('VOO target percentage', { exact: true }).fill('100')
  await expect(page.getByRole('button', { name: 'Save target', exact: true })).toBeEnabled()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.getByRole('button', { name: 'Save target', exact: true }).click()
  await expect(page.getByLabel('Allocation values')).toContainText('VOO')
  await expect(page.getByLabel('Allocation values')).toContainText('100%')
})
