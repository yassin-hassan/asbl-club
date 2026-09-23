# frontend — ASBL Club Angular slice (Phase 1)

A thin Angular 21 client for the public events feed. This is the "strangler fig"
frontend that lives beside the Spring Boot app and consumes its JSON API. See
`../../LEARNING-ROADMAP.md` for the overall plan.

## What's here

```
src/app/
  models/event.ts              TS interfaces mirroring the Java records
  services/event-api.ts        HttpClient calls to the backend
  pages/home/                  slug entry point
  pages/event-list/            GET /api/v1/asbls/:slug/events
  pages/event-detail/          GET /api/v1/events/:id  +  polled availability
  app.routes.ts                routes
  app.config.ts                provideHttpClient + provideRouter
proxy.conf.json                dev proxy → Spring on :8080
```

## Running it (dev)

1. Start the backend so it's listening on `http://localhost:8080`
   (from `../backend`, e.g. `./mvnw spring-boot:run` with the DB from the
   repo-root `compose.yaml`).
2. Start the frontend:

   ```
   npm start        # = ng serve, http://localhost:4200
   ```

3. Open http://localhost:4200 — it defaults to the seeded `club-demo` association.

`npm start` uses `proxy.conf.json`, which forwards `/api/*` and
`/events/**/availability` to :8080, so browser and API share one origin (no CORS
in dev). This mirrors the Phase 2 reverse-proxy setup.

## Build

```
npm run build      # outputs dist/frontend
```

## Install caveat

npm 10.9.8 has an arborist bug (`Cannot read properties of null (reading 'edgesOut')`)
triggered by Angular 21's default test stack (vitest → jsdom → optional canvas).
Install with:

```
npm install --legacy-peer-deps
```

Testing is out of scope for Phase 1, so this is harmless. Revisit if/when a test
setup is added.
