# Contributing

## Ground rules

- Java 26; no Spring or HTTP framework inside Lambda handlers.
- English identifiers, comments, messages in code, tests, and docs.
- Backend is layered: `api` (parsing/HTTP) → `application` (service) → `domain`
  (pure model + ports). AWS SDK calls live only in `infrastructure/`.
- Every security-relevant decision is recorded in [SECURITY.md](SECURITY.md);
  do not weaken a control there without updating that file.
- Frontend user-facing strings live in `frontend/src/messages.ts` for both
  `en-US` (default) and `pt-BR`.
- No logging of passwords, deletion tokens, hashes, or full presigned URLs.

## Development loop

```sh
make setup
floci start
make infrastructure   # package Lambdas, apply Terraform to Floci
make serve            # http://localhost:5173
```

See [docs/local-development.md](docs/local-development.md) before assuming the
emulator matches AWS: three deviations are documented there.

## Verification

Run the full suite before opening a pull request:

```sh
make test            # backend (Maven) + frontend (Vitest)
./scripts/terraform.sh validate
make frontend        # type-check + production build
```

Add a test only where a plausible regression would fail it: the conditional
download reservation, expiry handling, password hashing, and the request-level
validation all have unit tests already. Do not pad with mock-echo assertions.

## Pull requests

- Prefer many small commits with concrete messages over one large change.
- Keep the S3 store private; never introduce server-side proxying of file
  bytes as a convenience.
- If you change the API contract or the security model, update README.md and
  SECURITY.md in the same pull request.

## Reporting a vulnerability

Do not open a public issue for a security defect. Email the repository
maintainers directly with the impact and a minimal reproduction. See
[SECURITY.md](SECURITY.md) for the threat model and accepted risks.
