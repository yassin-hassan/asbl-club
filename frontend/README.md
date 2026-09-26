# frontend — the asbl.club web app and edge Worker

The Angular 22 app, and the Cloudflare Worker that serves it and forwards `/api/*` to the Spring API. See the
[main README](../README.md) for the architecture and how to run everything.

```
src/app/
  pages/           one folder per page (lazy-loaded routes, see app.routes.ts)
  components/      shared pieces (confirm dialog, logo, event search)
  services/        auth (session, refresh, guard, interceptor), Problem Details helpers, new-version recovery
  i18n/            language handling (Transloco; texts in public/i18n/{fr,nl,en}.json)
  api/generated/   the API client, generated from openapi/asbl-club-api.json (don't edit by hand)
worker/            the Cloudflare Worker: API proxy, link previews, RSS relay
e2e/               Playwright end-to-end tests
public/_headers    security headers (CSP) and caching for the static site
```

```bash
npm install --legacy-peer-deps   # npm 10's arborist trips on the optional test dependencies otherwise
npm start                        # http://localhost:4200, proxies /api to the API on :8080
npm test                         # unit tests (Vitest)
npm run e2e                      # end-to-end tests (the API must be running)
npm run build                    # production build in dist/frontend
npm run api:generate             # regenerate the API client after the contract changes
npm run worker:dev               # the built app through the Worker, as in production (http://localhost:8788)
```

The contract (`openapi/asbl-club-api.json`) is produced by the backend: after changing the API, run
`./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true` in `backend/`, then `npm run api:generate` here.
CI fails if the two drift apart.
