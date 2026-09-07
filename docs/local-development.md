# Local development

GhostDrop runs against **Floci**, an AWS emulator, so the whole serverless path
(upload → confirm → download → delete → cleanup) is exercised locally before
deployment.

## Requirements

- Java 26 with `jlink`
- Maven 3.9+
- Node.js 26+
- Docker
- Floci (started with `floci start` or `make start`)

```sh
make setup          # frontend dependencies
make start          # start the Floci emulator
make infrastructure # package Lambdas + apply Terraform to Floci
make serve          # http://localhost:5173
```

## The custom Java 26 runtime

AWS offers no managed Java 26 Lambda runtime. GhostDrop ships a custom
`provided.al2023` runtime: a minimized Java 26 image, the Lambda Runtime
Interface Client, and a `bootstrap` that launches it with
`GHOSTDROP_HANDLER`.

A Java 26 `jlink` image built on this workstation links against a newer glibc
(`GLIBC_2.38`) than the Lambda base image provides (`GLIBC_2.34` on
`provided.al2023`), so the packaged runtime is built **inside** an
`amazonlinux:2023` container with the `java-26-amazon-corretto-devel` + `-jmods`
packages:

```sh
./scripts/package-lambda.sh   # outputs backend/target/ghostdrop-lambda.zip
```

This is what `make infrastructure` runs first. Do not swap the production
runtime for Node.js or Java 21 to make local testing easier — that tests a
different architecture than production.

## Terraform and Floci endpoints

Two endpoint variables keep Lambda calls and browser-facing S3 URLs apart when
Terraform points at Floci (both are unset in production):

- `aws_endpoint_url=http://host.docker.internal:4566` — used by Lambda SDK
  *clients* (delete, DynamoDB). `host.docker.internal` is reachable from inside
  the Lambda containers via the Docker bridge; `localhost.floci.io` is not
  (it resolves to `::1` inside the container).
- `public_endpoint_url=http://localhost.floci.io:4566` — used by the S3
  *presigner*, whose URLs are returned to the browser. `localhost.floci.io`
  resolves on this host, so presigned PUT/GET work in a normal browser.

The presigner therefore signs path-style URLs against `localhost.floci.io`;
server-side SDK clients still use `host.docker.internal`.

### Browser flow (`make serve`)

`scripts/dev-server.mjs` serves the built SPA at `http://localhost:5173` and
proxies `/api` to the emulator. It pins the API Gateway `Host` header because
**Floci keys API Gateway routing by the Host header**, and the emulated
gateway hostname (from `terraform.tfstate` output `api_url`) is not resolvable
on this host. Open the page, upload a file, copy the share link, open `/d/{id}`
in a new tab, and download.

## Documented Floci deviations

These emulator behaviors differ from AWS; production code is written to the AWS
behavior and verified there:

1. **Signed `content-length` is not enforced.** The presigned upload URL signs
   the declared file size. Real S3 rejects a body of any other length; Floci
   accepts whatever length you send. The signing is kept because it is the
   production storage-abuse control (see SECURITY.md).
2. **API Gateway routes by `Host` header**, and its public hostname is not on
   the host DNS. The dev server sets that header; production uses the real
   gateway URL.
3. **`localhost.floci.io` inside Lambda containers** resolves to `::1` (the
   container itself), which is why SDK clients target `host.docker.internal`
   instead.

## Notes

- The scheduled cleanup handler connects cleanly to Floci (it timed out only
  before the endpoint variables were wired) and stays warm between runs.
- `make test` runs `mvn test` and Vitest. Validate Terraform with
  `./scripts/terraform.sh validate`.
