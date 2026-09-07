# Local development

## Requirements

- Java 26 with `jlink`
- Maven 3.9+
- Node.js 26+
- Docker
- Floci

Run `make setup`, `make start`, then `make infrastructure`.

## Java 26 Lambda runtime

AWS does not offer a managed Java 26 Lambda runtime. GhostDrop packages a custom `provided.al2023` runtime with a minimized Java 26 image, the Lambda Runtime Interface Client, and a `bootstrap` executable:

```sh
./scripts/package-lambda.sh
```

The resulting deployment archive is `backend/target/ghostdrop-lambda.zip`.

### Floci behavior

Floci 2.0.1 provisions S3, DynamoDB, IAM, EventBridge, API Gateway, and the six GhostDrop Lambda functions. Its custom-runtime function creation validates `handler` as a file name. When `AWS_ENDPOINT_URL` is set, Terraform configures `handler = "bootstrap"` and passes the Java handler through `GHOSTDROP_HANDLER`; production keeps the Java handler declaration. This is the documented custom-runtime entrypoint shape.

The next invocation reaches the bootstrap but fails because this workstation's Java 26 `jlink` runtime requires `GLIBC_2.38` and Floci runs `public.ecr.aws/lambda/provided:al2023`, which provides GLIBC 2.34:

```text
/var/task/runtime/bin/java: /lib64/libc.so.6: version `GLIBC_2.38' not found
```

### Local workaround

Build the Java 26 runtime inside an Amazon Linux 2023-compatible build image before creating the ZIP. The custom runtime must be linked against the same GLIBC baseline as `provided.al2023`. Do not replace the production Lambda with Node.js or Java 21: that would test a different architecture.

Until that image is available, Floci remains usable for Terraform, S3, DynamoDB, IAM, EventBridge, API Gateway, Lambda deployment, and adapter tests. Invocation-level tests require the AL2023-compatible Java 26 runtime.
