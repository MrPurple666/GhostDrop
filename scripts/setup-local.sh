#!/usr/bin/env sh
set -eu
[ -f .env ] || cp .env.example .env
cd frontend && npm ci --ignore-scripts
floci doctor
