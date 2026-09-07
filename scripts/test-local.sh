#!/usr/bin/env sh
set -eu
make test
./scripts/terraform.sh init -backend=false
./scripts/terraform.sh validate
