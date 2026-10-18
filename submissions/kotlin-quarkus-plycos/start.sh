#!/usr/bin/env bash
cd "$(dirname "$0")"
exec java \
  -Xms1200m \
  -Xmx1200m \
  -XX:+UseParallelGC \
  -XX:+TieredCompilation \
  -Djdk.virtualThreadScheduler.parallelism=4 \
  -Djdk.virtualThreadScheduler.maxPoolSize=8 \
  -jar /opt/server-runner.jar
