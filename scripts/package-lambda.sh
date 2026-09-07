#!/usr/bin/env sh
set -eu

cd backend
mvn -q package
docker run --rm -v "$(pwd)":/workspace amazonlinux:2023 rm -rf /workspace/target/lambda
mkdir -p target/lambda/lib
docker run --rm -e HOST_UID="$(id -u)" -e HOST_GID="$(id -g)" -v "$(pwd)":/workspace -w /workspace amazonlinux:2023 bash -lc 'dnf install -y binutils java-26-amazon-corretto-devel java-26-amazon-corretto-jmods >/dev/null && java_home=$(dirname "$(dirname "$(readlink -f "$(command -v java)")")") && "$java_home/bin/jlink" --add-modules java.base,java.desktop,java.naming,java.security.jgss,java.sql,jdk.unsupported --strip-debug --no-header-files --no-man-pages --output target/lambda/runtime && chown -R "$HOST_UID:$HOST_GID" target/lambda'
cp target/ghostdrop-lambda.jar target/lambda/lib/
cat > target/lambda/bootstrap <<'BOOTSTRAP'
#!/bin/sh
set -eu
exec "$LAMBDA_TASK_ROOT/runtime/bin/java" -cp "$LAMBDA_TASK_ROOT/lib/ghostdrop-lambda.jar" com.amazonaws.services.lambda.runtime.api.client.AWSLambda "${GHOSTDROP_HANDLER:-$_HANDLER}"
BOOTSTRAP
chmod 755 target/lambda/bootstrap
(cd target/lambda && jar --create --file ../ghostdrop-lambda.zip .)
