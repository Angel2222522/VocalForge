#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version=8.11.1
if command -v gradle >/dev/null 2>&1; then exec gradle "$@"; fi
cache="${XDG_CACHE_HOME:-$HOME/.cache}/velatune-tooling"
mkdir -p "$cache"
if [ ! -x "$cache/gradle-$version/bin/gradle" ]; then
  archive="$cache/gradle-$version-bin.zip"
  curl --fail --location --connect-timeout 10 --max-time 180 "https://services.gradle.org/distributions/gradle-$version-bin.zip" -o "$archive.part"
  expected=$(curl --fail --location --connect-timeout 10 --max-time 30 "https://services.gradle.org/distributions/gradle-$version-bin.zip.sha256")
  actual=$(sha256sum "$archive.part" | cut -d ' ' -f 1)
  [ "$actual" = "$expected" ] || { echo 'Gradle SHA-256 mismatch' >&2; exit 2; }
  mv "$archive.part" "$archive"
  unzip -q "$archive" -d "$cache"
fi
exec "$cache/gradle-$version/bin/gradle" "$@"
