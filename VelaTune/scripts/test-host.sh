#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/host evidence
g++ -std=c++17 -O3 -Wall -Wextra -Wpedantic -I dsp dsp/TuneEngine.cpp tests/dsp_tests.cpp -o build/host/dsp_tests
build/host/dsp_tests "$@" > evidence/dsp-tests.txt
