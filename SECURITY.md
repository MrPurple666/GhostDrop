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
- Server-side encryption at rest (`AES256`).
- CORS is locked to a single `allowed_origin` and methods `PUT`/`GET` only.
- IAM is least-privilege: the Lambda role can touch only
  `arn:…:ghostdrop-{env}-files/uploads/*` and the file table/indexes.

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

### Confirmation and cleanup

- A file becomes available only through an S3 `ObjectCreated` event whose key
  matches the pending record's storage key (`markAvailable` flips
  `PENDING_UPLOAD → AVAILABLE` atomically).
- Cleanup runs on a schedule, deleting expired objects and rows. It is
  idempotent: failures are retried on the next pass, never double-deleted.

## Boundaries and accepted risks

- Content scanning is out of scope for this project. If malware scanning is
  required, add an inspect step on the `ObjectCreated` event before
  `markAvailable`; see [docs/architecture.md](docs/architecture.md).
- The presigned download URL is shareable within its lifetime; treat download
  URLs like bearer tokens.
- An available-but-unclaimed password gate is deliberately indistinguishable
  from a consumed or expired file (both surface as `404`) so that a lockout
  does not reveal the file's existence to an unauthorized caller.

## Reporting

See [CONTRIBUTING.md](CONTRIBUTING.md) for the disclosure process.
