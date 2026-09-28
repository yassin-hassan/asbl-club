# The existing resources, adopted into the state (Terraform 1.5+ import blocks). Harmless once imported.
import {
  to = github_repository.app
  id = "asbl-club"
}

import {
  to = github_repository_ruleset.protect_main
  id = "asbl-club:23880093"
}

import {
  to = github_repository_environment.production
  id = "asbl-club:production"
}
