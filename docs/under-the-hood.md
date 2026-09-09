# Under the hood

Short notes on the parts of GhostDrop that are worth reading before you change
them. Each entry says what it is, why it is built that way, and the failure mode
to respect.

## A Java 26 runtime where AWS ships none

AWS has no managed Java 26 Lambda runtime, so GhostDrop packs its own
`provided.al2023` custom runtime:

- `jlink` produces a minimized Java image (`java.base` + a few modules).
- The Lambda Runtime Interface Client (`aws-lambda-java-runtime-interface-client`)
  plus a `bootstrap` script launch it with the real handler in
  `GHOSTDROP_HANDLER`.
- The image is built **inside an `amazonlinux:2023` container** (see
  `scripts/package-lambda.sh`). A Java 26 image built on a host distro links
  against a newer glibc than `provided.al2023` provides, and the runtime fails
  at startup with `GLIBC_2.38 not found`. Building inside AL2023 keeps the
  glibc baseline identical to the target.

## Bytes never touch the compute

`POST /uploads` never receives the file. It returns a short-lived **presigned
S3 PUT URL**; the browser PUTs the bytes directly to S3. Downloads are the same
mirror image: a presigned GET URL. The Lambda functions only ever mint URLs and
move metadata. The most expensive thing they touch is a DynamoDB row.

The presigned PUT **signs the declared `content-length`**, so S3 rejects any
body that is not exactly the declared size. That is the real storage-abuse
control: you cannot store more bytes than you declared, and declared size is
capped. (Floci's emulator does not enforce signed content-length — documented
in [local-development.md](local-development.md).)

The presigned GET is signed with a `response-content-disposition` that carries
the original file name (sanitized of header-breaking bytes), so the browser
saves `report.pdf`, not the random `uploads/<key>`.

## Encryption at rest with a dedicated KMS key

Production objects use **SSE-KMS** with a dedicated customer-managed key
(`alias/ghostdrop-files`, created and rotated by Terraform with a 7-day
deletion window). The bucket sets it as the **default encryption** with S3
**Bucket Keys** enabled, which makes S3 use a short-lived bucket key for each
object and cuts KMS `GenerateDataKey` traffic — same crypto boundary, lower
cost.

The presigned PUT sends **no encryption headers on purpose**. With bucket
default encryption in place, a header-less PUT is encrypted by S3 with the
bucket key; requiring signed headers would add client complexity for no gain.
A bucket policy closes the downgrade paths: it denies any `uploads/*` PUT that
declares a non-KMS algorithm or any SSE-C header. Downloads decrypt through the
same default path — browsers never touch KMS.

The KMS key policy is least-privilege: account-root administration plus a
`ViaService = s3` statement so S3 (and GuardDuty, which reads objects to scan
them) may use the key, restricted to this account. Only production uses it;
the local emulator keeps AES256 because it does not emulate KMS.

## Expiry and limits enforced in one atomic write

`DynamoDbFileRepository.reserveDownload` runs a **single conditional
`UpdateItem`**:

```text
SET downloadCount = downloadCount + 1
WHERE status = 'AVAILABLE'
  AND attribute_exists(scannedAt)
  AND expiresAt > :now
  AND (attribute_not_exists(maxDownloads) OR downloadCount < maxDownloads)
```

There is no read-then-write counter, so two concurrent downloads cannot overspend
the remaining budget. This one write enforces *all three* gates: the clean-scan
requirement (`AVAILABLE` plus `scannedAt`), the expiry, and the limit.
DynamoDB TTL on `expiresAt` is only the physical cleanup net; it is never the
access-control mechanism — a download is refused here, at reservation time.

## A file is not downloadable until a malware scan says so

Files are created `PENDING_UPLOAD`. An S3 `ObjectCreated` event moves them to
`PENDING_SCAN` (`UploadConfirmationHandler.beginScan`) — **not** to
`AVAILABLE`. Because upload keys are random (`uploads/<24 random bytes>`), a
spurious event cannot confirm someone else's upload.

From `PENDING_SCAN`, only a trusted verdict from GuardDuty Malware Protection
for S3 moves the file: a clean result (`COMPLETED` + `NO_THREATS_FOUND`) makes
it `AVAILABLE` and records `scannedAt`; `THREATS_FOUND` makes it `INFECTED`
and deletes the object; anything else (`FAILED`, `SKIPPED`, `UNSUPPORTED`,
`ACCESS_DENIED`, or an unknown shape) makes it `SCAN_FAILED` — fail closed,
never downloadable. `ScanResultService` decides the outcome; each transition
is a conditional DynamoDB write, so duplicate or out-of-order events are
no-ops and an old `INFECTED` cannot be resurrected into `AVAILABLE` by a
delayed clean event.

The S3 event is an async invocation, so repeated handler failures end up on an
SQS DLQ instead of vanishing after Lambda's retries; an alarm fires when the
queue is non-empty.

## The deletion token is never stored

`POST /uploads` returns a `deletionToken` once. Only its **SHA-256 hash** is
saved, so a database leak does not leak usable tokens. `DELETE` requires
`Authorization: Bearer <deletionToken>` and the handler compares hashes.

## Cleanup that retries instead of double-deletes

Cleanup rows are partitioned by an hour bucket (`expirationBucket` GSI: current
and previous hour, `expiresAt <= now`). EventBridge invokes the cleanup Lambda
every 5 minutes. It is **idempotent**: a failed delete is simply retried on the
next pass, never executed twice against a half-gone object.

Each run logs a one-line summary (`cleanup summary expired=… deleted=… failed=…`)
and one line per failed object to stderr, which the Lambda runtime streams to
CloudWatch Logs. A metric filter counts those lines into
`GhostDrop/CleanupFailures` and an alarm fires after any failure in 10 minutes.
Two physical backstops cover what the schedule misses: DynamoDB TTL on
`expiresAt`, and an S3 lifecycle rule that expires `uploads/*` after 31 days
(the maximum lifetime is 30).

## API protection

`POST /uploads` is the only unauthenticated write, so it is the abuse surface.
The gateway throttles it (10 req/s burst 20 — aggregate across clients) and, in
production, a WAF rate rule blocks any client IP past 500 requests in 5
minutes. Gateway access logs (JSON) go to CloudWatch with 7-day retention; an
alarm fires on any 5xx. The WAF rule, the access-log alarm, and the DLQ are
production-only: the emulator does not model them.

## UI gotcha: don't put a base `translate` on a `motion` node

The dropzone folder is a `motion/react` component. Animated nodes have their
whole `transform` controlled by the animation, so a centering helper like
`translate(-50%, -50%)` on the same node is silently overwritten the moment it
animates `rotateX` — the flap visibly slides off-center. The fix is to position
such nodes by coordinates (or a static wrapper) and let `motion` own only the
transform. See `frontend/src/components/Folder.tsx`.
