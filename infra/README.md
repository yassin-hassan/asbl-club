# Infrastructure as code (Terraform)

What the platforms around the app enforce, declared here and reviewed like code: the GitHub repository (settings,
the `main` branch rules, the `production` environment) and the Neon database project.

Not here, on purpose:

- **Render** (the API server): its API, and so its Terraform provider, can't manage free-plan services.
- **The Cloudflare Worker**: already declared in `frontend/wrangler.jsonc` and deployed by CI.
- **Secrets**: they stay in each platform's dashboard.

## Run it

```bash
cd infra
./tf init          # once: downloads the providers (versions pinned in .terraform.lock.hcl)
./tf plan          # what would change; nothing is touched
./tf apply         # shows the plan again and asks before changing anything
```

`./tf` passes the tokens: GitHub's from your `gh` login, Neon's from `infra/.env` (gitignored), which you create
yourself:

```bash
NEON_API_KEY=...   # Neon console → Account settings → API keys
```

## State

`terraform.tfstate` is a local file, gitignored: free, and enough for one maintainer (no locking; back it up). It
contains the database password, as the Neon provider stores it, so it must never be committed or shared.
