#!/usr/bin/env bash
cd "$(dirname "$0")"
ulimit -n "$(ulimit -Hn)" 2>/dev/null || true
exec java \
  -Xms650m \
  -Xmx650m \
  -XX:+UseParallelGC \
  -XX:+AlwaysPreTouch \
  -XX:MaxMetaspaceSize=128m \
  -XX:ReservedCodeCacheSize=64m \
  -XX:MaxDirectMemorySize=64m \
  -Xss512k \
  -XX:+UseCompactObjectHeaders \
  -Djdk.virtualThreadScheduler.parallelism=8 \
  -Djdk.virtualThreadScheduler.maxPoolSize=32 \
  -jar /opt/server-runner.jar
