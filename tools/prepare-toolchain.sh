#!/usr/bin/env bash
set -euo pipefail
tools=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
mkdir -p "$tools/downloads" "$tools/jdk"
cd "$tools/downloads"
apt download openjdk-17-jdk-headless=17.0.11+9-1 openjdk-17-jre-headless=17.0.11+9-1 > "$tools/java-download.log" 2>&1
for package in openjdk-17-*.deb; do dpkg-deb -x "$package" "$tools/jdk"; done
"$tools/jdk/usr/lib/jvm/java-17-openjdk-amd64/bin/java" -version
curl --fail --location --connect-timeout 15 --max-time 60 --silent --show-error https://dl.google.com/android/repository/repository2-1.xml --output "$tools/repository.xml"
echo 'JDK and SDK metadata ready.'
