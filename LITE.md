# Termux Launcher Lite

Product cut: **terminal + dock + sessions + minimal settings**. No on-device AI control plane.

## Removed

| Area | What |
|------|------|
| TAI / AI | Entire `com.termux.ai`, MNN JNI, LiteRT / sentencepiece deps |
| Native | `libMNN.so`, `libmnnllmapp.so` (~14 MB) |
| LauncherCtl | HTTP/MCP API server, CLI install, AI-coupled endpoints |
| Settings | TAI preferences screens and preference XML |

## Kept (lite)

- Terminal (`TerminalView` / emulator modules)
- Dock (`SuggestionBarView`) with notification badges via slim `LauncherNotificationListener`
- Sessions drawer, extra keys, style/launcher settings
- Optional Shizuku lock path
- Compose shell host (`TermuxActivityContent` + `AndroidView` for existing layout)
- Optional Rust `launcher_perf` focus outline (built from source in CI)

## Build

```bash
./native/launcher-perf/build-android.sh   # if packaging focus outline
./gradlew :app:assembleDebug
```

VAJ CI still builds `launcher_perf` from source; it no longer packages MNN.
