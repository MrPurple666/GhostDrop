# Security

GhostDrop holds user files, however briefly, so the threat model treats S3 as
the private store and the API as the only trusted path. This page records the
controls, the design intent, and the boundaries that must not be weakened.

## Threat model

- An anonymous internet caller can only reach the public API and, through
  presigned URLs, one S3 object they were explicitly authorized to touch.
- S3 is private. There is no policy, ACL, or public bucket access of any kind.
- No file content is ever proxied through GhostDrop compute; bytes travel only
  between the browser and S3.
- A file disappears on its expiry, when its download budget is consumed, or
  when its owner deletes it. This is enforced by application code, not by
  eventual TTL.

## Controls

### S3 (storage)

- `block_public_acls`, `block_public_policy`, `ignore_public_acls`,
  `restrict_public_buckets` are all `true`.
- Encryption at rest is **SSE-KMS** with a dedicated customer-managed key
  (`alias/ghostdrop-files`; Terraform-managed, auto-rotated yearly, 7-day
  deletion window) and S3 Bucket Keys. No generic AWS-managed key, and no
  client-side encryption keys stored by GhostDrop.
- The bucket default encryption is that KMS key; a bucket policy **denies**
  uploads that declare non-KMS encryption or SSE-C, so the encryption cannot be
  downgraded even by an explicit header.
- CORS is locked to a single `allowed_origin` and methods `PUT`/`GET` only.
- IAM is least-privilege: the Lambda role can touch only
  `arn:…:ghostdrop-{env}-files/uploads/*` and the file table/indexes. KMS key
  policy grants account-root administration plus a `ViaService = s3`
  statement restricted to this account (S3 and GuardDuty decrypt through it).

### Upload and download

- Storage keys and ids are 144–192 bits of `SecureRandom`, Base64url — not
  enumerable.
- The presigned upload URL signs the exact `content-length` declared at
  creation. AWS S3 rejects any body that does not match, so a caller cannot
  store more bytes than they declared (and declared size is capped by
  `GHOSTDROP_MAX_FILE_SIZE_BYTES`).
  > Floci's emulator does not enforce the signed content-length; see
  > [docs/local-development.md](docs/local-development.md).
- Upload and download URLs are short-lived (`900s` / `300s` by default).
- The download URL is presigned with the original file name in
  `response-content-disposition`, sanitized against header injection.
- The unauthenticated create route is rate-limited at the gateway (10 req/s
  burst 20, aggregate) and, in production, per client IP by a WAF rate rule
  (500 requests / 5 min). See [docs/under-the-hood.md](docs/under-the-hood.md).

### Password and limit enforcement

- Passwords are hashed with Argon2id (see
  `infrastructure/security/Argon2PasswordHasher`); plaintext is never stored or
  logged.
- Download count and expiry are enforced in a **single conditional DynamoDB
  update** (`reserveDownload`). There is no read-modify-write window, so a race
  cannot overspend the remaining-download budget. The same condition refuses
  expired files and files already at their limit.

### Deletion

- The owner's deletion token is returned once at creation. Only its SHA-256
  hash is stored, so a database leak does not leak usable tokens.
- `DELETE` requires `Authorization: Bearer <deletionToken>` and verifies the
  hash before removing the S3 object and the metadata row.

### Confirmation, scanning and cleanup

- A file becomes `AVAILABLE` only through an S3 `ObjectCreated` event (which
  moves it to `PENDING_SCAN`) followed by an explicitly clean **GuardDuty
  Malware Protection** verdict (`COMPLETED` + `NO_THREATS_FOUND`) processed by
  the scan-result handler. Every other verdict — infected, failed, skipped,
  unsupported, access denied, malformed, unknown — keeps the file unavailable
  (fail closed). Scan verdicts are never client-supplied; they arrive via
  EventBridge from the GuardDuty service role.
- `INFECTED` objects are deleted; metadata is marked `INFECTED` so no download
  URL can ever be minted. Rows that never reach `AVAILABLE` are removed by the
  scheduled cleanup at expiry, and pre-change `AVAILABLE` rows without
  `scannedAt` are treated as unscanned and non-downloadable.
- Confirmation is an async invocation, so repeated failures land on an SQS DLQ
  with an alarm, rather than disappearing after Lambda's retries.
- Cleanup runs on a schedule, deleting expired objects and rows. It is
  idempotent: failures are retried on the next pass, never double-deleted.
- Cleanup failures are visible: each run logs a structured summary and one
  line per failed object, and a metric-filter alarm fires on any failure.
  Malware verdicts and scan failures log structured lines feeding the
  `ScanInfected` and `ScanFailures` alarms.
- Physical backstops for deletion: DynamoDB TTL on `expiresAt` and an S3
  lifecycle rule that expires `uploads/*` after 31 days.

## Boundaries and accepted risks

- Content scanning runs only in production (GuardDuty Malware Protection for
  S3, priced per GB scanned) and only for new objects under `uploads/`. The
  local emulator simulates scan-result events; it does not detect malware.
- Malware scanning is a best-effort AV gate, not a guarantee: keep the
  `PENDING_SCAN` → clean-verdict-only invariant intact, but do not treat a
  clean scan as proof the file is harmless forever.
- The presigned download URL is shareable within its lifetime; treat download
  URLs like bearer tokens.
- An available-but-unclaimed password gate is deliberately indistinguishable
  from a consumed or expired file (both surface as `404`) so that a lockout
  does not reveal the file's existence to an unauthorized caller. Files still
  being scanned or quarantined also surface as `404`, revealing nothing about
  the scan state.

## Cost notes

- **GuardDuty Malware Protection for S3**: billed per GB of scanned objects
  plus per-object costs; only objects under `uploads/` are scanned, once each.
- **KMS**: S3 Bucket Keys reduce per-object `GenerateDataKey` calls to one per
  bucket key; key rotation is free. KMS costs are negligible at GhostDrop
  scale.
- **Lambda/EventBridge**: the scan-result handler runs once per uploaded
  object; cleanup stays on its 5-minute schedule.
- Nothing introduced here runs continuously; all components are event-driven
  or scheduled.

## Reporting

See [CONTRIBUTING.md](CONTRIBUTING.md) for the disclosure process.
