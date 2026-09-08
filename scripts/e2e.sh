#!/usr/bin/env sh
# End-to-end check against the Floci-emulated stack: real API Gateway -> Lambda
# handlers -> DynamoDB/S3, including the S3 event that confirms an upload.
# Usage:  ./scripts/e2e.sh [--provision]
#   --provision   force a full repackage + terraform apply first (slow)
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
FORCE=false
[ "${1:-}" = "--provision" ] && FORCE=true

say() { printf '\033[1;32me2e\033[0m %s\n' "$*"; }
die() { printf '\033[1;31me2e\033[0m %s\n' "$*" >&2; exit 1; }

command -v docker >/dev/null || die "docker is required"
command -v node >/dev/null || die "node is required"

# 1. Floci must be up and healthy.
if ! curl -fsS "http://localhost.floci.io:4566/_localstack/health" >/dev/null 2>&1; then
  if command -v floci >/dev/null 2>&1; then
    say "starting floci…"
    floci start
  else
    say "starting floci via docker compose…"
    docker compose up -d floci
  fi
  say "waiting for floci…"
  i=0
  until curl -fsS "http://localhost.floci.io:4566/_localstack/health" >/dev/null 2>&1; do
    i=$((i + 1)); [ "$i" -gt 60 ] && die "floci did not become healthy"
    sleep 2
  done
fi
say "floci healthy"

# 2. Provision when forced or when the API gateway is unreachable.
gateway_host=$(node -e "const fs=require('fs');try{const s=JSON.parse(fs.readFileSync('$ROOT/infrastructure/terraform.tfstate','utf8'));process.stdout.write(new URL(s.outputs.api_url.value).hostname)}catch(e){}")
provisioned=false
if [ -n "$gateway_host" ]; then
  code=$(curl -s -o /dev/null -w '%{http_code}' -m 4 -H "Host: $gateway_host" -X POST "http://127.0.0.1:4566/api/v1/uploads" -H 'content-type: application/json' -d '{}' 2>/dev/null || true)
  case "$code" in 400|401|404|500) provisioned=true ;; esac
fi
if [ "$FORCE" = true ] || [ "$provisioned" = false ]; then
  say "provisioning infrastructure…"
  make infrastructure
  gateway_host=$(node -e "const fs=require('fs');const s=JSON.parse(fs.readFileSync('$ROOT/infrastructure/terraform.tfstate','utf8'));process.stdout.write(new URL(s.outputs.api_url.value).hostname)")
fi
[ -n "$gateway_host" ] || die "gateway host not found in terraform.tfstate"
say "gateway $gateway_host"

# 3. API round trip.
api() { # method path [body] -> status + body via stdout
  method=$1 path=$2 body=${3-}
  if [ -n "$body" ]; then
    curl -s -o /tmp/e2e-body -w '%{http_code}' -H "Host: $gateway_host" -X "$method" "http://127.0.0.1:4566$path" -H 'content-type: application/json' -d "$body"
  else
    curl -s -o /tmp/e2e-body -w '%{http_code}' -H "Host: $gateway_host" -X "$method" "http://127.0.0.1:4566$path"
  fi
}

json_field() { # field -> value from /tmp/e2e-body
  node -e "const s=JSON.parse(require('fs').readFileSync('/tmp/e2e-body','utf8'));process.stdout.write(s.$1)"
}

NAME="ghostdrop-e2e.txt"
json=$(node -e "console.log(JSON.stringify({fileName:'$NAME',contentType:'text/plain',fileSize:11,expiresInSeconds:300,maxDownloads:1}))")
code=$(api POST /api/v1/uploads "$json"); [ "$code" = 201 ] || die "create upload: got $code"
id=$(json_field id)
token=$(json_field deletionToken)
upload_url=$(json_field uploadUrl)

code=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$upload_url" -H 'content-type: text/plain' --data-binary 'hello world')
[ "$code" = 200 ] || die "presigned PUT: got $code"
say "uploaded $id"

# The S3 event flips PENDING_UPLOAD -> AVAILABLE asynchronously; poll for it.
i=0
until [ "$(api GET "/api/v1/files/$id")" = 200 ]; do
  i=$((i + 1)); [ "$i" -gt 15 ] && die "file never became available after S3 event"
  sleep 1
done
say "confirmed available"

code=$(api POST "/api/v1/files/$id/downloads" '{}'); [ "$code" = 200 ] || die "create download: got $code"
download_url=$(json_field downloadUrl)
headers=$(curl -s -D - -o /tmp/e2e-download "$download_url")
grep -qi 'HTTP/1.1 200' <<<"$headers" || die "download GET failed"
grep -qi "content-disposition: attachment; filename=\"$NAME\"" <<<"$headers" || die "download missing filename disposition"
[ "$(cat /tmp/e2e-download)" = "hello world" ] || die "downloaded bytes mismatch"
say "download preserved name and bytes"

# maxDownloads=1: a second reservation must be refused.
code=$(api POST "/api/v1/files/$id/downloads" '{}'); [ "$code" = 404 ] || die "second download: got $code, want 404"

code=$(api DELETE "/api/v1/files/$id"); [ "$code" = 401 ] || die "delete without token: got $code"
code=$(curl -s -o /tmp/e2e-body -w '%{http_code}' -H "Host: $gateway_host" -X DELETE "http://127.0.0.1:4566/api/v1/files/$id" -H "authorization: Bearer $token")
[ "$code" = 204 ] || die "delete with token: got $code"
code=$(api GET "/api/v1/files/$id"); [ "$code" = 404 ] || die "file still visible after delete: got $code"

say "PASS: upload -> S3 confirm -> download (name+bytes) -> limit -> authorized delete"
