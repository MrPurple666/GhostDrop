# GhostDrop

Temporary file sharing that makes files vanish. A file is uploaded, a link is
shared, and the file expires or is consumed — then it is destroyed.

Serverless on AWS: API Gateway invokes Java 26 Lambda handlers; DynamoDB stores
metadata; S3 transfers bytes only through presigned URLs. A React + TypeScript
client is served separately.

## How it works

1. The sender selects a file and options (lifetime, optional password, download
   limit) in the browser.
2. `POST /api/v1/uploads` returns a short-lived **presigned upload URL** and a
   random id. The browser streams the file straight to S3 — bytes never pass
   through GhostDrop.
3. An S3 object-created event moves the record from `PENDING_UPLOAD` to
   `AVAILABLE`. Until then the share link reports the file as unavailable.
4. The recipient opens `/d/{id}`. If password-protected, the correct password
   produces a short-lived **presigned download URL**; the browser downloads the
   bytes directly from S3.
5. Every download is a single atomic DynamoDB reservation that enforces the
   expiry and remaining-download budget. A scheduled cleanup deletes the object
   and the metadata; DynamoDB TTL is the physical safety net.

```
Browser ── presigned S3 PUT/GET ─────────────► S3 (private bucket)
   │                                            ▲
   └─ API Gateway ──► Java Lambda handlers       │
                       ├─ DynamoDB (metadata + atomic reservations)
                       ├─ S3 event ─► confirm handler
                       └─ EventBridge every 5 min ─► cleanup handler
```

## Stack

- Backend: Java 26, Maven, AWS SDK v2, custom `provided.al2023` runtime, JUnit 5
- Frontend: React 19, TypeScript, Vite, Vitest
- Infrastructure: Terraform + HashiCorp AWS provider
- Local emulation: Floci

## Run locally

The whole serverless path is emulated locally with **Floci** (S3, DynamoDB, API
Gateway, EventBridge, Lambda all run in Docker), so you can exercise upload →
confirm → download → delete before deploying.

### Prerequisites

- Java 26 (with `jlink`)
- Maven 3.9+
- Node.js 26+
- Docker
- Floci (`floci --version` to check)

### Steps

Fastest path — starts Floci if needed, installs deps, provisions infrastructure
**only on the first run**, then serves:

```sh
./scripts/dev.sh          # or: make dev
./scripts/dev.sh --provision   # force a full repackage + terraform apply
```

Or step by step:

```sh
make setup          # install frontend dependencies (npm ci)
make start          # start the Floci emulator (alias for: floci start)
make infrastructure # package the Lambdas + apply Terraform to Floci
make serve          # build the SPA and serve http://localhost:5173
```

The first `make infrastructure` is the slow step: it downloads the
`amazonlinux:2023` build image, installs Corretto, and provisions ~30 resources.
Later runs are incremental.

### Verify it works

- `make test` runs backend (Maven) and frontend (Vitest) suites.
- Open `http://localhost:5173`, drag a file onto the folder, set options, and
  create a GhostDrop. Copy the share link, open `/d/{id}` in a new tab, download.
- Tip: switch the top bar between **OLED** (dark) and **PAPER** (light) themes.

### Troubleshooting

| Symptom | Cause / fix |
| ------- | ----------- |
| `make serve` says "Gateway host unknown" | Run `make infrastructure` first — the dev server reads the gateway id from `infrastructure/terraform.tfstate`. |
| API calls work but S3 uploads 404 | The dev server must be used for the browser (it fixes the gateway `Host` header). Don't open `dist` directly. |
| First build is very slow | Expected: `amazonlinux:2023` is pulled and the Java runtime is `jlink`-ed inside it. |
| Cleanup Lambda times out in logs | It only happens before the endpoint env vars are wired; rerun `make infrastructure`. |

More emulator quirks and the exact deviations from AWS live in
[docs/local-development.md](docs/local-development.md).

## API

All routes are under `/api/v1`. Errors are JSON `{"code","message"}` with an
appropriate status.

| Method | Route | Purpose |
| ------ | ----- | ------- |
| `POST` | `/uploads` | Create a file: returns `{id, uploadUrl, shareUrl, deletionToken, expiresAt}` |
| `GET` | `/files/{id}` | Public metadata: name, size, expiry, `passwordProtected`, remaining downloads |
| `POST` | `/files/{id}/downloads` | Body `{password?}`: reserve and return `{downloadUrl, expiresInSeconds}` |
| `DELETE` | `/files/{id}` | Header `Authorization: Bearer <deletionToken>`: destroy immediately |

The `deletionToken` is shown once, at creation; only its SHA-256 hash is stored.

## Configuration

Environment variables (read by the Lambda handlers / presigner unless noted):

| Variable | Required | Default | Purpose |
| -------- | :------: | ------- | ------- |
| `FILES_TABLE` | yes | — | DynamoDB table name |
| `FILES_BUCKET` | yes | — | S3 bucket name |
| `GHOSTDROP_ALLOWED_ORIGIN` | no | `http://localhost:5173` | CORS origin on API responses |
| `GHOSTDROP_ENVIRONMENT` | no | — | Runtime label (`dev`, …) |
| `GHOSTDROP_MAX_FILE_SIZE_BYTES` | no | `524288000` | Upper bound on declared size |
| `GHOSTDROP_MIN_LIFETIME_SECONDS` | no | `300` | Minimum expiry |
| `GHOSTDROP_MAX_LIFETIME_SECONDS` | no | `2592000` | Maximum expiry (30 d) |
| `GHOSTDROP_UPLOAD_URL_SECONDS` | no | `900` | Presigned upload URL lifetime |
| `GHOSTDROP_DOWNLOAD_URL_SECONDS` | no | `300` | Presigned download URL lifetime |
| `GHOSTDROP_AWS_ENDPOINT_URL` | no | — | Floci endpoint for SDK *clients* (local only) |
| `GHOSTDROP_PUBLIC_S3_ENDPOINT` | no | — | Floci endpoint the *presigner* signs (local only) |
| `VITE_API_URL` (frontend) | no | empty | API base URL; empty = same origin (`make serve`) |

Only the two endpoint variables are emulator-specific; in production they are
unset and the AWS SDK uses real endpoints. See
[docs/local-development.md](docs/local-development.md) for why clients and the
presigner use different hosts.

## Code highlights

The parts most worth reading (full detail in
[docs/under-the-hood.md](docs/under-the-hood.md)):

- **Java 26 custom runtime** — AWS has no managed Java 26 runtime, so one is
  `jlink`-minimized inside an `amazonlinux:2023` container to match the Lambda
  glibc exactly.
- **Bytes never touch the compute** — files move browser → S3 through presigned
  URLs; Lambdas only mint URLs and move metadata. The upload PUT signs the
  declared size, so S3 rejects bodies that exceed it.
- **One atomic write enforces expiry + download limit** — a single conditional
  DynamoDB `UpdateItem`; no race can overspend the budget.
- **`PENDING_UPLOAD` → `AVAILABLE`** via an S3 event on a random key, so an
  un-uploaded (or un-scanned) file is never downloadable.
- **Deletion token stored only as a SHA-256 hash.**
- **Nothing-style UI** — OLED/PAPER themes, Space Grotesk/Mono, and a `motion`
  folder dropzone.

## Repo layout

```
backend/       Java 26 Lambda handlers, services, domain, adapters
frontend/      React SPA (upload + share/download views)
infrastructure/ Terraform (S3, DynamoDB, API Gateway, EventBridge, IAM, Lambda)
scripts/       packaging, Terraform wrapper, local dev server
docs/          architecture, under-the-hood, and local-development notes
```

## Documentation

- [Architecture](docs/architecture.md)
- [Under the hood](docs/under-the-hood.md)
- [Local development](docs/local-development.md)
- [Security model](SECURITY.md)
- [Contributing](CONTRIBUTING.md)
