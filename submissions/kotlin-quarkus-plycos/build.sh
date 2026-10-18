#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./mvnw package -DskipTests -q
