#!/usr/bin/env sh
set -eu
exec docker run --rm --add-host=localhost.floci.io:host-gateway -v "$(pwd)/infrastructure:/workspace" -v "$(pwd)/backend:/backend:ro" -w /workspace hashicorp/terraform:1.12.0 "$@"
