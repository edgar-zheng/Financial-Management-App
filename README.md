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
