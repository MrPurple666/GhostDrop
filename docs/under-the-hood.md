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

## Expiry and limits enforced in one atomic write

`DynamoDbFileRepository.reserveDownload` runs a **single conditional
`UpdateItem`**:

```text
SET downloadCount = downloadCount + 1
WHERE status = 'AVAILABLE'
  AND expiresAt > :now
  AND (attribute_not_exists(maxDownloads) OR downloadCount < maxDownloads)
```

There is no read-then-write counter, so two concurrent downloads cannot overspend
the remaining budget. This one write enforces *both* the expiry and the limit.
DynamoDB TTL on `expiresAt` is only the physical cleanup net; it is never the
access-control mechanism — a download is refused here, at reservation time.

## A file is not downloadable until its bytes exist

Files are created `PENDING_UPLOAD`. Only an S3 `ObjectCreated` event turns them
`AVAILABLE` (`UploadConfirmationHandler.markAvailable`). Because upload keys are
random (`uploads/<24 random bytes>`), a spurious event cannot confirm someone
else's upload. The share link returns "unavailable" until the bytes are real.

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
