#!/usr/bin/env bash
set -euo pipefail

mvn --batch-mode --no-transfer-progress \
  -Dtest=PublicContractCompatibilityIT \
  test
