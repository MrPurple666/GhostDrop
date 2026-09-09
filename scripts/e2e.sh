#!/usr/bin/env sh
# E2E over real HTTP against Floci. Usage: ./scripts/e2e.sh [--provision]
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
FORCE=false
[ "${1:-}" = "--provision" ] && FORCE=true

say() { printf '\033[1;32me2e\033[0m %s\n' "$*"; }
die() { printf '\033[1;31me2e\033[0m %s\n' "$*" >&2; exit 1; }

command -v docker >/dev/null || die "docker is required"
command -v node >/dev/null || die "node is required"

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

bucket=$(node -e "const fs=require('fs');const s=JSON.parse(fs.readFileSync('$ROOT/infrastructure/terraform.tfstate','utf8'));process.stdout.write(s.outputs.files_bucket.value)")
scan_fn=$(node -e "const fs=require('fs');const s=JSON.parse(fs.readFileSync('$ROOT/infrastructure/terraform.tfstate','utf8'));const b=s.outputs.files_bucket.value;process.stdout.write(b.replace(/-files$/,'-scan-result'))")

api() { # method path [body] -> status, body left in /tmp/e2e-body
  method=$1 path=$2 body=${3-}
  if [ -n "$body" ]; then
    curl -s -o /tmp/e2e-body -w '%{http_code}' -H "Host: $gateway_host" -X "$method" "http://127.0.0.1:4566$path" -H 'content-type: application/json' -d "$body"
  else
    curl -s -o /tmp/e2e-body -w '%{http_code}' -H "Host: $gateway_host" -X "$method" "http://127.0.0.1:4566$path"
  fi
}

json_field() { # field -> value from /tmp/e2e-body
  node -e "const s=JSON.parse(require('fs').readFileSync('/tmp/e2e-body','utf8'));process.stdout.write(String(s.$1))"
}

simulate_scan() { # scan_status result_status -> fires the scan handler
  scan_status=$1 result_status=$2
  curl -s -o /dev/null -w '%{http_code}' -X POST "http://localhost.floci.io:4566/2015-03-31/functions/$scan_fn/invocations" \
    -H 'content-type: application/json' \
    -d "{\"detail\":{\"scanStatus\":\"$scan_status\",\"s3ObjectDetails\":{\"bucketName\":\"$bucket\",\"objectKey\":\"$storage_key\"},\"scanResultDetails\":{\"scanResultStatus\":\"$result_status\"}}}"
}

upload_and_put() { # name -> sets id, token, storage_key
  name=$1
  json=$(node -e "console.log(JSON.stringify({fileName:'$name',contentType:'text/plain',fileSize:11,expiresInSeconds:300,maxDownloads:1}))")
  code=$(api POST /api/v1/uploads "$json"); [ "$code" = 201 ] || die "create upload: got $code"
  id=$(json_field id)
  token=$(json_field deletionToken)
  upload_url=$(json_field uploadUrl)
  storage_key=$(node -e "const u=new URL(process.argv[1]);process.stdout.write(decodeURIComponent(u.pathname).split('/').slice(2).join('/'))" "$upload_url")
  code=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$upload_url" -H 'content-type: text/plain' --data-binary 'hello world')
  [ "$code" = 200 ] || die "presigned PUT: got $code"
  say "uploaded $id ($storage_key)"
}

NAME="ghostdrop-e2e.txt"
upload_and_put "$NAME"

sleep 2
code=$(api GET "/api/v1/files/$id"); [ "$code" = 404 ] || die "file available before scan: got $code"
say "not available before scan"

code=$(simulate_scan COMPLETED NO_THREATS_FOUND); [ "$code" = 200 ] || die "scan simulate call: got $code"
i=0
until [ "$(api GET "/api/v1/files/$id")" = 200 ]; do
  i=$((i + 1)); [ "$i" -gt 15 ] && die "file never became available after clean scan"
  sleep 1
done
say "confirmed available after clean scan"

code=$(api POST "/api/v1/files/$id/downloads" '{}'); [ "$code" = 200 ] || die "create download: got $code"
download_url=$(json_field downloadUrl)
headers=$(curl -s -D - -o /tmp/e2e-download "$download_url")
printf '%s' "$headers" | grep -qi 'HTTP/1.1 200' || die "download GET failed"
printf '%s' "$headers" | grep -qi "content-disposition: attachment; filename=\"$NAME\"" || die "download missing filename disposition"
[ "$(cat /tmp/e2e-download)" = "hello world" ] || die "downloaded bytes mismatch"
say "download preserved name and bytes"

code=$(api POST "/api/v1/files/$id/downloads" '{}'); [ "$code" = 404 ] || die "second download: got $code, want 404"

delete_with_token() {
  code=$(curl -s -o /tmp/e2e-body -w '%{http_code}' -H "Host: $gateway_host" -X DELETE "http://127.0.0.1:4566/api/v1/files/$id" -H "authorization: Bearer $token")
  [ "$code" = 204 ] || die "delete with token: got $code"
}
code=$(api DELETE "/api/v1/files/$id"); [ "$code" = 401 ] || die "delete without token: got $code"
delete_with_token
code=$(api GET "/api/v1/files/$id"); [ "$code" = 404 ] || die "file still visible after delete: got $code"
say "authorized delete ok"

upload_and_put "infected-e2e.txt"
code=$(simulate_scan COMPLETED THREATS_FOUND); [ "$code" = 200 ] || die "infected scan simulate: got $code"
sleep 1
code=$(api GET "/api/v1/files/$id"); [ "$code" = 404 ] || die "infected file downloadable: got $code"
code=$(api POST "/api/v1/files/$id/downloads" '{}'); [ "$code" = 404 ] || die "infected file got download URL: got $code"
say "infected file never available"
delete_with_token

upload_and_put "failed-e2e.txt"
code=$(simulate_scan FAILED FAILED); [ "$code" = 200 ] || die "failed scan simulate: got $code"
sleep 1
code=$(api GET "/api/v1/files/$id"); [ "$code" = 404 ] || die "unscannable file downloadable: got $code"
say "unscannable file fail-closed"
delete_with_token

say "PASS: clean path, infected quarantine, fail-closed scan, limits and deletion"
