# Frontend workflow and verification

## Backend URL

Vite reads `.env` from the **repository root**, alongside `mvnw`. Set the backend
origin (no `/api` suffix), for example:

```properties
VITE_API_BASE_URL=https://your-backend.example.com
```

Restart `npm run dev` after editing it. Production builds embed this value, so
rebuild after changing it. Only `VITE_` variables are exposed to browser code;
never use that prefix for secrets. If unset or empty, requests remain relative
and use the existing Vite `/api` proxy locally or the production reverse proxy.

All API and CSRF calls use this base URL and include session cookies. For direct
cross-origin requests, the backend must allow the exact frontend origin through
`FRONTEND_URL` and credentialed CORS. Cross-site sessions require HTTPS and
`SESSION_COOKIE_SAME_SITE=none` with `SESSION_COOKIE_SECURE=true`; browser
third-party-cookie restrictions can still block them. CSRF remains enabled.
No backend settings are changed by this frontend configuration.

## Production Docker image

From the repository root:

```sh
docker build -t portfolio-frontend:local frontend
docker run --rm --name portfolio-frontend -p 8081:8080 \
  -e BACKEND_URL=http://host.docker.internal:8080 portfolio-frontend:local
```

Open http://localhost:8081. This example reaches a backend running on the host
through Docker Desktop. On Linux Docker Engine, add
`--add-host=host.docker.internal:host-gateway` to the run command. For a backend
container on a shared Docker network, pass `--network <network>` and set
`BACKEND_URL=http://<backend-container-name>:8080` instead.

`BACKEND_URL` is required and must be an origin with no trailing slash or path.
Leave `VITE_API_BASE_URL` unset when building for this same-origin proxy setup.
Nginx substitutes it at container startup; changing it requires recreating the
container, not rebuilding the JavaScript. Browser requests stay on relative
`/api` URLs, preserving session cookies and CSRF behavior. The production server
is unprivileged nginx on container port 8080, not Vite. Unknown client routes return `index.html`; API responses and
missing built assets do not use the SPA fallback.

The build context is `frontend/`, with an allowlist in `.dockerignore`; local
`.env` files, secrets, tests, dependencies, and build output are excluded.
The final image contains nginx and the built static assets, without Node/npm.

The React/Vite application uses plain CSS, local component state, and the existing session-cookie/CSRF API helper. Authentication retains its forest-green visual theme. After login, select or create a portfolio, then continue into its dashboard. Use **Switch portfolio** to return to selection; this does not sign out.

## Run and test

From the repository root:

```sh
npm --prefix frontend ci
npm --prefix frontend run dev
npm --prefix frontend test
npm --prefix frontend run test:e2e
npm --prefix frontend run build
```

Start the backend separately for real API usage. The Vite dev server proxies `/api` to port 8080 and uses port 5173. Browser tests automatically start an isolated Vite server on port 5175 and use installed Google Chrome. They stub APIs, so they need neither MySQL nor a Massive key and never alter real portfolios. No lint script is configured. Test reports/screenshots are ignored by Git.

## Dashboard data

`PortfolioActivity` publishes a complete refresh after all six requests settle. Requests from superseded refreshes are aborted. Each response has its own failure state, so market failures do not hide history or share quantities. Transaction creation, target saves, target clearing, and manual refresh reload all six views:

- `GET /api/portfolios/{id}/transactions`
- `GET /api/portfolios/{id}/holdings`
- `GET /api/portfolios/{id}/holdings/valuation`
- `GET /api/portfolios/{id}/analytics`
- `GET /api/portfolios/{id}/allocations`
- `GET /api/portfolios/{id}/allocations/drift`

Selection uses `GET /api/portfolios/{id}` and `POST /api/portfolios`. Trades use `POST /api/portfolios/{id}/transactions`; targets use `PUT /api/portfolios/{id}/allocations`. Authentication endpoints (`me`, `csrf`, `register`, `login`, `logout`) are unchanged.

The SVG donut switches between backend asset weights and saved target percentages. A normalized ticker hash selects a stable color from a fixed palette; collisions are possible with many assets, so labels and percentages always accompany colors. Hover or focus a legend entry for details. Financial calculations remain on the backend.

Targets use sliders and numeric inputs, with integer units representing 0.0001 percentage points. Save requires exactly 100%; below/above totals and malformed precision block submission. Existing backend support for unheld tickers is preserved through an optional ticker field. Clearing saved targets requires confirmation and sends an empty target list.

## Manual regression checklist

1. Sign in, find an owned ID, and confirm a missing ID gives a friendly error.
2. Create a portfolio; verify its returned ID and explicitly continue to the dashboard.
3. Review real summary values, switch Current/Target, and inspect drift.
4. Edit targets using mouse sliders and keyboard numeric inputs; test below/above/exactly 100%.
5. Save targets and confirm both chart and drift refresh. Cancel the clear confirmation, then test a confirmed clear if desired.
6. Submit a BUY, a valid SELL, and an excessive SELL. Successful trades refresh all views; excessive sales retain the form and show an error.
7. Check History and Holdings using arrow-key tab navigation. Verify values against backend responses.
8. Test mobile, tablet, and desktop widths; test logout and session expiry.

No checkpoint commits are created automatically. Review `git diff` and untracked files before staging anything yourself.
