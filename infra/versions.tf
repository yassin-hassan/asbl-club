# The platform settings around the app, as code: what GitHub enforces on the repository and the database project.
# Deliberately NOT here:
#   - Render: its API (and so its Terraform provider) can't manage free-plan services, and anything else is paid.
#   - The Cloudflare Worker: already declared as code in frontend/wrangler.jsonc and deployed by CI (wrangler).
#   - Secrets: they stay in each platform's dashboard, never in this code or its state.
#
# State: a local file (terraform.tfstate, gitignored). Free and enough for one maintainer; the trade-offs are no
# locking and a file to back up. It holds the database password (the Neon provider stores it), so it's never
# committed. Run through ./tf (it loads the tokens).
terraform {
  required_version = ">= 1.16"

  required_providers {
    github = {
      source  = "integrations/github"
      version = "~> 6.6"
    }
  }
}

provider "github" {
  owner = "yassin-hassan" # token from GITHUB_TOKEN (./tf takes it from the gh CLI login)
}
