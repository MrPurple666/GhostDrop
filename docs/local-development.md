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

Floci 2.0.1 provisions S3, DynamoDB, IAM, EventBridge, and API Gateway resources used by GhostDrop. It rejects Java custom runtimes during Lambda creation with `Handler file 'dev.ghostdrop.api' not found in deployment package`, even when the archive has `bootstrap`, `runtime/bin/java`, and the handler JAR.

### Local workaround

Keep the Terraform production configuration unchanged. Use Floci to test S3 and DynamoDB adapters, and invoke application/domain tests locally with Maven until Floci supports `provided.al2023` Java custom runtimes. Do not substitute a Node or Java 21 Lambda implementation: it would test a different production architecture.
