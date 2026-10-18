#!/usr/bin/env bash
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive
SUDO=""
if [ "$(id -u)" -ne 0 ]; then SUDO="sudo"; fi

$SUDO apt-get update && $SUDO apt-get install -y \
    ca-certificates \
    curl \
    zip \
    unzip \
    && $SUDO rm -rf /var/lib/apt/lists/*

if [ ! -d "${HOME}/.sdkman" ]; then
  curl -s "https://get.sdkman.io?rcupdate=false" | bash
fi

set +u
source "${HOME}/.sdkman/bin/sdkman-init.sh"
sdk install java 25-tem < /dev/null || true
set -u

JAVA_HOME="${HOME}/.sdkman/candidates/java/current"
$SUDO ln -sf "${JAVA_HOME}/bin/java" /usr/local/bin/java
$SUDO ln -sf "${JAVA_HOME}/bin/javac" /usr/local/bin/javac
java -version
