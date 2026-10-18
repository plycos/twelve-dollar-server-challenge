#!/usr/bin/env bash
cd "$(dirname "$0")"
ulimit -n 65535 2>/dev/null || true
exec java \
  -Xms800m \
  -Xmx800m \
  -Xmn600m \
  -XX:+UseParallelGC \
  -XX:+TieredCompilation \
  -Djdk.virtualThreadScheduler.parallelism=4 \
  -Djdk.virtualThreadScheduler.maxPoolSize=8 \
  -jar /opt/server-runner.jar
