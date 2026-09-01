#!/usr/bin/env bash
#
# Runs once per created container (postCreateCommand). Everything here must be
# idempotent: a `Rebuild Container` replays it against a /root/.claude that the
# named volume has already populated from the previous container.
set -euo pipefail

# --- project bootstrap (was the inline postCreateCommand) ------------------

chmod +x gradlew
./gradlew --version
npm ci --prefix AppClient

# --- language server -------------------------------------------------------

# The typescript-lsp plugin configures a language server, it does not ship one.
# Without these two binaries on PATH the plugin loads and then reports no
# diagnostics, which looks like the plugin being broken rather than missing.
npm i -g typescript typescript-language-server

# --- Claude Code plugins ---------------------------------------------------

# The official marketplace is normally registered on the first interactive
# start. Post-create is not interactive, so register it explicitly; `|| true`
# because a re-run against the volume-persisted state exits non-zero on
# "already added".
claude plugin marketplace add anthropics/claude-plugins-official || true

# --scope project writes the activation into .claude/settings.json (committed),
# so a fresh clone on another machine converges on the same set. Already
# installed plugins produce no diff.
claude plugin install typescript-lsp@claude-plugins-official --scope project

# frontend-design is a skill-only plugin (no MCP server), so it adds nothing to
# per-turn context - safe to activate for everyone. Registering the marketplace
# above only clones the catalog; the skill does not load until the plugin is
# actually installed.
claude plugin install frontend-design@claude-plugins-official --scope project

# chrome-devtools is deliberately NOT installed here: an MCP plugin keeps its
# tool definitions in context every turn, used or not. Install it for the
# session that needs it (`/plugin install chrome-devtools@claude-plugins-official`)
# and `/plugin disable` it afterwards - the cache stays in the volume.
