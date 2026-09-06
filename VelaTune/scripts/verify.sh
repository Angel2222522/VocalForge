#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
scripts/test-host.sh
g++ -std=c++17 -O3 -fPIC -shared -I dsp dsp/TuneEngine.cpp tests/host_api.cpp -o build/host/libvela.so
python3 tests/evaluate.py > evidence/evaluate-console.txt
java com.sun.tools.javac.Main -d build/host/java app/src/main/java/gr/anelix/velatune/WaveFile.java tests/WaveFileTest.java tests/ParseJava.java
java -cp build/host/java WaveFileTest > evidence/wave-tests.txt
java -cp build/host/java ParseJava app/src > evidence/java-syntax.txt
g++ -std=c++17 -O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer -I dsp dsp/TuneEngine.cpp tests/stress.cpp -o build/host/stress
ASAN_OPTIONS=detect_leaks=0 build/host/stress > evidence/sanitizer.txt 2>&1
