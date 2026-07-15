# launcher_perf

Rust crate for Termux Launcher **performance-critical pixel work**.

## What ships

| API | Role |
|-----|------|
| `build_focus_outline_mask` | Alpha-threshold + disk dilate / hollow ring for AZ/icon focus outlines |
| JNI `LauncherPerfNative.buildFocusOutlineMask` | Android bridge used by `SuggestionBarView` |

Library name: **`liblauncher_perf.so`** (`System.loadLibrary("launcher_perf")`).

## Develop

```bash
cd native/launcher-perf
cargo test --no-default-features   # pure algorithm tests
cargo build --release --features jni
./build-android.sh                 # copy .so into app/src/main/jniLibs/
```

With NDK + `cargo-ndk`:

```bash
export ANDROID_NDK_HOME=...
cargo ndk -t arm64-v8a -o ../../app/src/main/jniLibs build --release --features jni
```

## Packaging

`build-android.sh` places `liblauncher_perf.so` under `app/src/main/jniLibs/<abi>/`.
The app module already packages `jniLibs` (see existing MNN `.so` files).
