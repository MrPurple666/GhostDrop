#!/usr/bin/env sh
set -eu
floci stop
rm -rf infrastructure/.terraform infrastructure/.terraform.tfstate.lock.info infrastructure/terraform.tfstate infrastructure/terraform.tfstate.backup
