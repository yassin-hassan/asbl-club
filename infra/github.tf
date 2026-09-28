# The repository and what GitHub enforces on it.

resource "github_repository" "app" {
  name         = "asbl-club"
  description  = "Free management platform for Belgian non-profits (ASBL/VZW): members, events, ticketing and Stripe payments. Java 21, Spring Boot 4, Angular 22, PostgreSQL, Cloudflare Workers."
  homepage_url = "https://asbl-club.ynhassan22.workers.dev"
  visibility   = "public"
  topics = ["angular", "cloudflare-workers", "java", "openapi", "owasp", "playwright", "postgresql", "spring-boot",
  "stripe", "stripe-connect", "testcontainers", "typescript"]

  has_issues      = true
  has_projects    = true
  has_wiki        = true
  has_discussions = false

  allow_merge_commit     = true
  allow_squash_merge     = true
  allow_rebase_merge     = true
  allow_auto_merge       = false
  delete_branch_on_merge = false

  # Secrets pushed by mistake are blocked (push protection) and reported (scanning).
  security_and_analysis {
    secret_scanning {
      status = "enabled"
    }
    secret_scanning_push_protection {
      status = "enabled"
    }
  }

  lifecycle {
    prevent_destroy = true # a typo here must never delete the repository
  }
}

# main only changes through a pull request whose checks passed; never force-pushed or deleted.
resource "github_repository_ruleset" "protect_main" {
  name        = "protect-main"
  repository  = github_repository.app.name
  target      = "branch"
  enforcement = "active"

  conditions {
    ref_name {
      include = ["~DEFAULT_BRANCH"]
      exclude = []
    }
  }

  rules {
    deletion         = true
    non_fast_forward = true

    pull_request {
      allowed_merge_methods           = ["merge", "squash", "rebase"]
      required_approving_review_count = 0 # one maintainer: the checks are the review
    }

    required_status_checks {
      # The CI jobs' names (.github/workflows/ci.yml); 15368 is GitHub Actions.
      required_check {
        context        = "Backend build and test"
        integration_id = 15368
      }
      required_check {
        context        = "Frontend build"
        integration_id = 15368
      }
      required_check {
        context        = "Dependency review"
        integration_id = 15368
      }
      required_check {
        context        = "End-to-end tests" # the whole path through the Worker; wasn't required before
        integration_id = 15368
      }
    }
  }
}

# Where the deploy job's secrets live (Cloudflare token, Sentry token). Only workflows running on main may use it:
# a job on any other branch that asks for this environment is refused, so it never sees the secrets.
resource "github_repository_environment" "production" {
  repository  = github_repository.app.name
  environment = "production"

  deployment_branch_policy {
    protected_branches     = false
    custom_branch_policies = true
  }
}

resource "github_repository_environment_deployment_policy" "production_main_only" {
  repository     = github_repository.app.name
  environment    = github_repository_environment.production.environment
  branch_pattern = "main"
}
