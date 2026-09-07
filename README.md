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

## Quick start (local)

Requirements: Java 26 + `jlink`, Maven, Node 26, Docker, and Floci.

```sh
make setup          # install frontend deps
floci start         # start the AWS emulator (also: make start)
make infrastructure # package Lambdas + apply Terraform to Floci
make serve          # build the SPA and serve http://localhost:5173
```

`make test` runs the backend and frontend suites. See
[docs/local-development.md](docs/local-development.md) for the emulator quirks
and a full browser walkthrough.

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

Environment variables (backend handlers): `FILES_TABLE`, `FILES_BUCKET`,
`GHOSTDROP_ALLOWED_ORIGIN`, and optional limits (`GHOSTDROP_MAX_FILE_SIZE_BYTES`,
`GHOSTDROP_MIN_LIFETIME_SECONDS`, `GHOSTDROP_MAX_LIFETIME_SECONDS`,
`GHOSTDROP_UPLOAD_URL_SECONDS`, `GHOSTDROP_DOWNLOAD_URL_SECONDS`). The frontend
reads `VITE_API_URL` (empty = same origin).

## Repo layout

```
backend/       Java 26 Lambda handlers, services, domain, adapters
frontend/      React SPA (upload + share/download views)
infrastructure/ Terraform (S3, DynamoDB, API Gateway, EventBridge, IAM, Lambda)
scripts/       packaging, Terraform wrapper, local dev server
docs/          architecture and local-development notes
```

## Documentation

- [Architecture](docs/architecture.md)
- [Local development](docs/local-development.md)
- [Security model](SECURITY.md)
- [Contributing](CONTRIBUTING.md)
