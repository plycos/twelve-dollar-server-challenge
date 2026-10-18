#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./mvnw package -DskipTests -q
cp target/server-runner.jar /opt/server-runner.jar
rm -rf target/
