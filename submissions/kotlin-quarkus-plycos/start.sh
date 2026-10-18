#!/usr/bin/env bash
cd "$(dirname "$0")"
exec java -jar target/server-runner.jar
