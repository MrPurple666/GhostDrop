# Architecture

GhostDrop is a temporary-file share: files live in S3 for a bounded time, the
metadata that grants access to them lives in DynamoDB, and Lambda handlers are
the only way to mint short-lived presigned S3 URLs.

## Components

```mermaid
flowchart LR
    subgraph Browser["Browser (React SPA)"]
        Upload["upload view"]
        Share["share / download view (/d/:id)"]
    end
    S3[("S3 · ghostdrop-*-files<br/>uploads/&lt;random key&gt;")]
    Api["API Gateway · HTTP API v2<br/>/api/v1/*"]
    Handlers["Java 26 Lambda handlers"]
    Dynamo[("DynamoDB · ghostdrop-*-files<br/>id (PK) · storageKey (GSI) · status<br/>expiresAt (TTL) + hour-bucket GSI · counts")]
    Confirm["confirm handler<br/>PENDING → AVAILABLE"]
    Cleanup["cleanup handler<br/>delete expired S3 object + metadata"]
    Clock["EventBridge · every 5 min"]

    Upload -- "presigned PUT (bytes)" --> S3
    Share -- "GET /files/{id} · POST /files/{id}/downloads" --> Api
    Api --> Handlers
    Handlers -- "create · reserve · delete" --> Dynamo
    Share -- "presigned GET (bytes)" --> S3
    S3 -- "ObjectCreated event" --> Confirm
    Confirm -- "flip status" --> Dynamo
    Clock --> Cleanup
    Cleanup --> S3
    Cleanup --> Dynamo
```

## Backend layers

- `dev.ghostdrop.api` — Lambda `RequestHandler`s. They parse the event, call a
  service, and map failures to stable JSON errors. No AWS business logic here.
- `dev.ghostdrop.application` — `UploadService`, `DownloadService`,
  `DeleteFileService`, `GhostDropSettings`. Orchestrate the domain against the
  `FileRepository`/`StorageService`/`PasswordHasher` ports.
- `dev.ghostdrop.domain` — `TemporaryFile` (immutable), `FileStatus`, and the
  repository/storage/hasher interfaces. No SDK imports.
- `dev.ghostdrop.infrastructure` — DynamoDB and S3 adapters, Argon2 hasher,
  AWS client construction.
- `dev.ghostdrop.configuration` — reads environment into SDK clients.

## Data model

`TemporaryFile` fields map to one DynamoDB row:

| Field | Notes |
| ----- | ----- |
| `id` | random key; public share id |
| `storageKey` | `uploads/<random>`; GSI for event confirmation |
| `status` | `PENDING_UPLOAD` → `AVAILABLE` |
| `expiresAt` | epoch seconds; TTL + numeric range on hour GSI |
| `expirationBucket` | `yyyy-MM-dd'T'HH` UTC; cleanup scans current + prior hour |
| `downloadCount`, `maxDownloads` | atomic reservation budget |
| `passwordHash` | Argon2id, present only when protected |
| `deletionTokenHash` | SHA-256 of the owner token |

### Atomic download reservation

`reserveDownload(id, now)` runs one conditional `UpdateItem`:

```
SET downloadCount = downloadCount + 1
WHERE status = 'AVAILABLE'
  AND expiresAt > :now
  AND (attribute_not_exists(maxDownloads) OR downloadCount < maxDownloads)
```

This is the single point that enforces expiry and the download budget. A
concurrent attacker cannot overspend because DynamoDB serializes the condition.

## S3 event confirmation

Files are created `PENDING_UPLOAD` so a share link never resolves before the
bytes exist. S3 emits `ObjectCreated` for `uploads/*`, the confirm handler
finds the row by `storageKey` and flips it `AVAILABLE`. The key is random, so a
spurious event cannot confirm another upload.

Confirmation is an async invocation; if the handler keeps failing, Lambda's
retries eventually land the event on an SQS DLQ (`confirm_dlq`) instead of
dropping it silently. An alarm fires when the queue is non-empty; recovery is
operator-driven (fix the cause and re-drive), while the S3 lifecycle rule and
DynamoDB TTL expire any upload that stays `PENDING_UPLOAD` regardless.

## Cleanup

`findExpired(now)` queries the hour-bucket GSI for the current and previous
hour with `expiresAt <= now`, then deletes each S3 object and row. EventBridge
invokes it every five minutes. It is idempotent: a failed delete is retried on
a later pass. DynamoDB TTL on `expiresAt` is the physical safety net, not the
access-control mechanism (downloads are always refused at reservation time).

Each run logs a structured summary and one line per failed object to stderr
(the Lambda runtime streams it to CloudWatch Logs); a metric filter counts
failures into `GhostDrop/CleanupFailures` and alarms after any failure in 10
minutes. A lifecycle rule additionally expires `uploads/*` after 31 days (the
maximum lifetime is 30), covering rows deleted before their object.

## API protection and observation

The gateway throttles `POST /api/v1/uploads` (10 req/s, burst 20) and, in
production, a WAF rate rule caps each client IP at 500 requests per 5 minutes
— the route throttle is aggregate, the WAF rule is per-IP. Gateway access logs
(JSON: request id, route, status, latency, source IP) stream to CloudWatch
with 7-day retention, and an alarm fires on any 5xx. All four are
production-only resources; the local emulator does not model them.

## Extension point: malware scanning

To add content inspection without proxying bytes:

1. Confirm-handler event (or a new scanning handler) pulls the object, inspects
   it, and only then flips `PENDING_UPLOAD → AVAILABLE`; or
2. delete/reject on a scan verdict.

The `PENDING_UPLOAD` state exists precisely so an un-scanned object is never
downloadable. Do not skip confirmation.

## Frontend

Two views route on the URL path (no router dependency): `/` is the upload
form; `/d/:id` is the share/download page. Both call `/api/v1/*`. Bytes never
touch the app — upload and download are `fetch` to presigned S3 URLs. Strings
are centralized per locale (`en-US`, `pt-BR`).
