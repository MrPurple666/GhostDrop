#!/usr/bin/env sh
# One-command local run: Floci -> deps -> infrastructure (once) -> dev server.
# Usage:  ./scripts/dev.sh [--provision]
#   --provision   force a full repackage + terraform apply (slow first run)
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PORT="${PORT:-5173}"
FORCE=false
[ "${1:-}" = "--provision" ] && FORCE=true

say() { printf '\033[1;32mdev\033[0m %s\n' "$*"; }
die() { printf '\033[1;31mdev\033[0m %s\n' "$*" >&2; exit 1; }

command -v docker >/dev/null || die "docker is required"
command -v floci >/dev/null || die "floci CLI not found on PATH"
command -v node >/dev/null || die "node is required"

# 1. Floci
if docker ps --format '{{.Names}}' | grep -qx floci; then
  say "floci already running"
else
  say "starting floci…"
  floci start
fi
say "waiting for floci…"
i=0
until curl -fsS "http://localhost.floci.io:4566/_localstack/health" >/dev/null 2>&1; do
  i=$((i + 1)); [ "$i" -gt 60 ] && die "floci did not become healthy"
  sleep 2
done
say "floci healthy"

# 2. Frontend dependencies
if [ ! -d frontend/node_modules ]; then
  say "installing frontend dependencies…"
  make setup
else
  say "frontend dependencies present"
fi

# 3. Infrastructure (only if not already provisioned and reachable)
gateway_host=$(node -e "const fs=require('fs');try{const s=JSON.parse(fs.readFileSync('$ROOT/infrastructure/terraform.tfstate','utf8'));process.stdout.write(new URL(s.outputs.api_url.value).hostname)}catch(e){}")
provisioned=false
if [ -n "$gateway_host" ]; then
  code=$(curl -s -o /dev/null -w '%{http_code}' -m 4 -H "Host: $gateway_host" -X POST "http://127.0.0.1:4566/api/v1/uploads" -H 'content-type: application/json' -d '{}' 2>/dev/null || true)
  case "$code" in 400|401|404|500) provisioned=true ;; esac
fi
if [ "$FORCE" = true ] || [ "$provisioned" = false ]; then
  say "provisioning infrastructure (first run can take a few minutes)…"
  make infrastructure
else
  say "infrastructure already provisioned"
fi

# 4. Dev server (foreground; Ctrl-C to stop)
say "serving at http://localhost:${PORT}"
node scripts/dev-server.mjs
