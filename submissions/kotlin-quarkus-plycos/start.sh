#!/usr/bin/env bash
cd "$(dirname "$0")"
ulimit -n "$(ulimit -Hn)" 2>/dev/null || true
exec java \
  -Xms1G \
  -Xmx1G \
  -XX:+UseSerialGC \
  -XX:+UseCompactObjectHeaders \
  -jar /opt/server-runner.jar
