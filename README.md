# VocalForge

VocalForge is an offline Android vocal pitch-correction workstation for recorded audio. It keeps the original take untouched, performs F0 analysis locally, renders pitch correction with a native overlap-add/PSOLA engine, and exports lossless WAV files.

## Design goals

- Offline-first: no account, server, API key, or internet permission.
- Non-destructive projects with original audio, analysis, settings, edits, and renders separated.
- Native C++ DSP for analysis and rendering; Kotlin/Compose for the Android layer.
- Natural correction and hard-tune behavior are two parameterizations of the same processing path.
- WAV import/export, Android media decoding fallback, recording, A/B playback, piano-roll-style editing, and automated tests.

## Build

The GitHub Actions workflow installs the Android SDK and NDK, runs JVM tests, and builds a debug APK. The project targets arm64-capable modern Android devices while retaining a practical Android 10 minimum.
