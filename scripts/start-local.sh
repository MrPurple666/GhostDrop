#!/usr/bin/env sh
set -eu
floci start
make infrastructure
exec sh -c 'cd frontend && npm run dev -- --host 0.0.0.0'
