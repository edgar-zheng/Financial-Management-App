# Portfolio Tracker

Spring Boot backend and React frontend in one repository. The backend is in `backend/` (`pom.xml` and `src/`); the React application is in
`frontend/`. The Maven wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`) stays at the repository root.

## Run locally

Prerequisites: Java 21, local MySQL with the `portfolio_tracker` database, and a
current Node.js LTS version supported by Vite (Node 22.12+ or newer).

1. Configure your local database password in
   `backend/src/main/resources/application-env.properties` using `spring.datasource.password`.
   Keep credentials out of frontend files; browser code is public.
2. From the repository root, start the backend:
   ```sh
   ./mvnw -f backend/pom.xml spring-boot:run
   ```
3. In a second terminal:
   ```sh
   cd frontend
   npm ci
   npm run dev
   ```
4. Open http://localhost:5173. The page loads portfolio 1 automatically. Enter
   another existing portfolio ID and click **Load portfolio** to look it up.

The frontend calls `GET /api/portfolios/{id}`. Vite proxies `/api` to the backend
at http://localhost:8080 during development, so no backend CORS change is needed.
See [Vite server proxy documentation](https://vite.dev/config/server-options#server-proxy).
If port 8080 is occupied, stop the old backend instance before starting this one.

If you have no portfolios, create one with Postman: `POST http://localhost:8080/api/portfolios`,
Content-Type `application/json`, body `{"name":"Retirement Account"}`. Use the returned ID.

## Verify before committing

- Run `./mvnw -f backend/pom.xml test` from the repository root (requires MySQL).
- Run `npm run build` from `frontend/` to check the production build.
- Check the browser shows the same name and ID as the backend response.
- Try a nonexistent ID: the page should display **Portfolio not found.**
- Stop the backend and reload: the page should display an error rather than stale data.
- Check `git status --short` and `git diff`; inspect new files directly because
  untracked files do not appear in ordinary `git diff`.

The build output is `frontend/dist/`. Production deployment is not configured:
its web server must route `/api` to Spring Boot; the Vite development proxy is not
included in the static build.

## Market data and end-of-day valuation (Massive Stocks Basic)

Credentials still resolve through `market.api.key`; keep the actual value in the
existing ignored local configuration or environment. Do not put it in frontend
configuration. Optional `.env` imports use plain Java-properties syntax, without
shell `export` prefixes or surrounding quotes. No real key is in these examples.

Configuration:

- `market.api.base-url`: defaults to `https://api.massive.com`; can be supplied
  through `MASSIVE_BASE_URL`. Use HTTPS for any real credential-bearing server.
- `market.cache.ttl`: defaults to `6h`.

The backend calls `GET /v2/aggs/ticker/{ticker}/prev?adjusted=true` using bearer
header authentication, and selects the first valid positive closing price (`c`).
Massive response types stay private to the client. The previous trading session
is determined by Massive, so weekends/holidays don't trigger a request for a
nonexistent calendar-day bar. There are no Last Trade, Snapshot, or live-quote calls.
See [Massive previous-day aggregate documentation](https://massive.com/docs/rest/stocks/aggregates/previous-day-bar).

Existing price routes are preserved:

- `GET /api/market/prices/AAPL`
- `GET /api/portfolios/1/prices`

Their JSON retains `symbol`, `price`, `asOf`, `currency`, `source`, and `priceType`.
The price is now a closing price and `priceType` is `PREVIOUS_CLOSE`. `asOf` is the
aggregate-window start timestamp (UTC, converted from provider milliseconds),
not the close instant or request time. It is null if the provider omits it.

New route: `GET /api/portfolios/1/holdings/valuation`

Each result includes `symbol`, `quantity`, `closingPrice`, `marketValue`, `asOf`,
`currency`, and `priceType`. `marketValue = quantity * closingPrice`, calculated
with BigDecimal without intermediate rounding. HoldingValuationService reuses
HoldingService's existing quantity calculation, then calls MarketDataService.
Database work completes before external HTTP requests. Existing portfolio,
transaction, and quantity-only holdings APIs do not require market prices.
No stock-split reconciliation or other corporate-action accounting is added.

### Cache and rate limits

A Spring-managed Caffeine cache holds up to 1,000 normalized ticker prices in
memory for six hours after a successful fetch. This TTL reduces repeated daily
price requests while allowing refreshed closing data within six hours. Concurrent
requests for one ticker share a single load; failed fetches are not cached. Cache
contents are lost on restart and are not shared between application instances.

The cache is not a global rate limiter. More than five distinct uncached symbols
in a minute can still exceed Basic-plan limits. Requests are sequential and have
no automatic retries. If a portfolio request fails partway, successful ticker
prices remain cached; wait at least a minute before retrying a rate-limited call.
A failed price fails the whole valuation response; no zero or fabricated values
are substituted. Empty portfolios return `[]` without contacting Massive.

### Errors and logging

The existing GlobalExceptionHandler returns the same `status`, `message`, and
`fieldErrors` structure:

- 400: invalid ticker format.
- 404: missing portfolio, provider ticker not found, or empty/missing results.
- 503: missing key, authentication failure (provider 401), subscription denial
  (provider 403), or rate limiting (provider 429), with distinct messages.
- 502: unexpected provider HTTP failure, invalid response, or transport failure.

Debug logging for `com.edgar.portfolio.service.MarketDataService` shows cache
misses; client debug logs show requested tickers. Warnings identify provider HTTP
status or transport/decoding failure. Keys, headers, and raw provider errors are
never logged. Connect timeout is five seconds; read timeout is ten seconds.

### Verify before committing

From the repository root:

```sh
./mvnw -f backend/pom.xml test
./mvnw -f backend/pom.xml clean package
./mvnw -f backend/pom.xml spring-boot:run
```

All market tests stub the HTTP boundary; they do not use a live Massive key.
In Postman, request an AAPL price and confirm a positive price and
`PREVIOUS_CLOSE`. Request a funded portfolio's holdings and valuation, and verify
quantity times closingPrice equals marketValue. Repeat the price request within
the TTL; with service debug logging enabled, it should not log another cache miss.
Check empty and missing portfolios. A Friday session returned on a weekend is
valid. Actual credential validity and provider access require this manual check.

Review `git status --short` and `git diff`. New files must be inspected directly
until staged. Do not stage `.env` or local credential files.

## Session authentication (backend)

Spring Security loads users from UserRepository and verifies BCrypt hashes.
All APIs except GET `/api/auth/csrf` and POST `/api/auth/register` and
`/api/auth/login` require a session. CSRF protection applies to all mutations,
including registration, login, and logout. Authentication errors return JSON
401; invalid/missing CSRF tokens return JSON 403. No JWT or HTTP Basic is enabled.

Postman workflow (keep its cookie jar enabled and use the same hostname):

1. GET `http://localhost:8080/api/auth/csrf`. Keep the session cookie and copy
   `token` into the header named by `headerName` for subsequent POST/PUT requests.
2. POST `/api/auth/register`, JSON body containing `email` and `password`.
   Password length: at least 12 characters and at most 72 UTF-8 bytes. Expect 201.
   Registration does not automatically log in. Duplicate emails return 409.
3. POST `/api/auth/login` using **x-www-form-urlencoded**, with fields `email`
   and `password` and the CSRF header. Expect 204 and an authenticated session.
4. GET `/api/auth/csrf` again after login; authentication rotates the token.
5. GET `/api/auth/me` should return the email. Portfolio GET requests now work;
   portfolio/transaction/allocation writes also need the new CSRF header.
6. POST `/api/auth/logout` with the current CSRF header. Expect 204. Subsequent
   protected GET requests return 401. Get a fresh CSRF token before logging in again.

The React UI still needs a login/logout screen and CSRF-aware fetch wiring.
Postman sessions are not shared with the browser. Portfolio ownership is NOT
implemented in this step: authenticated users currently share access to existing
portfolio resources. Do not treat this as user-level data isolation.
Use HTTPS for deployment; session transport security must be configured there.

SecurityIntegrationTests exercise the actual Spring Security filter chain with
local MySQL and rolled-back fixtures; older standalone controller tests remain
focused on business/API behavior.
