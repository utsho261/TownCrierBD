# Current verification — offline voice

- 54 previous pure Java checks passed: keyword/selection ownership, name/phone validation, request lifecycle/filter/order, movement.
- 36 offline checks passed: PCM16/float conversion, channel mixing, offsets, invalid frames, sample rates, keyword/selection integration, manifest, binary SHA256, corrupt/truncated downloads, Unicode vocabulary IDs, RIFF padding and WAV boundaries.
- 42 Node tests passed: 35 workflow and 7 handler tests, including no exported paid voice endpoint. Retired cloud speech tests were removed with that implementation.
- All 59 Android Java sources parsed without syntax errors; all 53 XML files parsed.
- `java tools/PrepareOfflineVoice.java . --verify-manifest`: all 9 pinned entries valid.
- `bash gradlew :app:assembleDebug --offline`: Gradle Wrapper could not download missing gradle-8.13-bin.zip (`java.net.SocketException: Network is unreachable`).

No Android SDK/emulator, downloaded speech model or AAR is available here. Java syntax/core tests are not Android type compilation, native-library loading or real speech recognition. No updated APK was produced, and no recognition-accuracy claim is made. Models/runtime are fetched automatically by the first Android build on a connected machine. Included PREVIOUS_BUILD APKs are historical.

No Firebase deploy, billing setup or live account mutation was performed. Existing non-voice cloud features still require their backend. See DEPLOYMENT_BN.md for the offline-on-phone test and existing request workflow checks.

Verified upstream: sherpa-onnx v2.0.1 Android Kotlin API (used through its Java setters), official AAR checksum, Alpha Cephei Bengali revision dfabeea5eee1f33d81436826d0575d8cfd64bd1d and official sherpa Bengali configuration, English en-20M revision d42f2d9f7ca24806fb667456a18a9f1b60f70d16. Model/runtime sizes and SHA256 values are pinned in tools/offline-voice-manifest.tsv.
