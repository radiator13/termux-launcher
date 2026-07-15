#!/usr/bin/env bash
# Build liblauncher_perf.so FROM SOURCE for Android ABIs and install into app jniLibs.
# Never commits prebuilts — output is always local/CI-generated.
#
# Requires:
#   - cargo + rustc
#   - Prefer: cargo-ndk + ANDROID_NDK_HOME (or NDK_HOME) for multi-ABI
#   - Or: rustup target aarch64-linux-android (+ linker from NDK)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CRATE="$(cd "$(dirname "$0")" && pwd)"
JNI_LIBS="$ROOT/app/src/main/jniLibs"
LIB_NAME="liblauncher_perf.so"
# Drop any stale prebuilt so packaging never picks up an old binary by accident
rm -f "$JNI_LIBS"/arm64-v8a/"$LIB_NAME" \
      "$JNI_LIBS"/armeabi-v7a/"$LIB_NAME" \
      "$JNI_LIBS"/x86_64/"$LIB_NAME" \
      "$JNI_LIBS"/x86/"$LIB_NAME"

cd "$CRATE"

if ! command -v cargo >/dev/null 2>&1; then
  echo "error: cargo not found; install Rust to build launcher_perf from source" >&2
  exit 1
fi

build_and_copy() {
  local target="$1"
  local abi="$2"
  echo "Building $target -> jniLibs/$abi (from source)"
  cargo build --release --target "$target" --features jni
  mkdir -p "$JNI_LIBS/$abi"
  local src="$CRATE/target/$target/release/$LIB_NAME"
  if [[ ! -f "$src" ]]; then
    echo "error: missing $src" >&2
    find "$CRATE/target" -name "$LIB_NAME" 2>/dev/null | head -20
    exit 1
  fi
  cp -f "$src" "$JNI_LIBS/$abi/$LIB_NAME"
  echo "  wrote $JNI_LIBS/$abi/$LIB_NAME ($(wc -c <"$src") bytes)"
}

NDK_ROOT="${ANDROID_NDK_HOME:-${NDK_HOME:-${ANDROID_NDK_ROOT:-}}}"

if command -v cargo-ndk >/dev/null 2>&1 && [[ -n "$NDK_ROOT" ]]; then
  echo "Using cargo-ndk (NDK=$NDK_ROOT)"
  export ANDROID_NDK_HOME="$NDK_ROOT"
  ABIS=(arm64-v8a)
  if [[ "${LAUNCHER_PERF_MULTI_ABI:-0}" == "1" ]]; then
    ABIS=(arm64-v8a armeabi-v7a x86_64 x86)
  fi
  cargo ndk $(printf -- '-t %s ' "${ABIS[@]}") -o "$JNI_LIBS" build --release --features jni
  # cargo-ndk names output liblauncher_perf.so under each abi
  for abi in "${ABIS[@]}"; do
    if [[ ! -f "$JNI_LIBS/$abi/$LIB_NAME" ]]; then
      echo "error: cargo-ndk did not produce $JNI_LIBS/$abi/$LIB_NAME" >&2
      exit 1
    fi
  done
  echo "Done (cargo-ndk)."
  exit 0
fi

# Manual NDK link for aarch64-linux-android when cargo-ndk is unavailable
if [[ -n "$NDK_ROOT" ]]; then
  HOST_TAG="$(uname -s | tr '[:upper:]' '[:lower:]')-x86_64"
  if [[ "$(uname -m)" == "aarch64" ]] || [[ "$(uname -m)" == "arm64" ]]; then
    # Apple Silicon / some hosts
    if [[ -d "$NDK_ROOT/toolchains/llvm/prebuilt/linux-aarch64" ]]; then
      HOST_TAG="linux-aarch64"
    elif [[ -d "$NDK_ROOT/toolchains/llvm/prebuilt/darwin-aarch64" ]]; then
      HOST_TAG="darwin-aarch64"
    fi
  fi
  PREBUILT="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG"
  API="${LAUNCHER_PERF_API_LEVEL:-24}"
  if [[ -x "$PREBUILT/bin/aarch64-linux-android${API}-clang" ]]; then
    rustup target add aarch64-linux-android 2>/dev/null || true
    export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$PREBUILT/bin/aarch64-linux-android${API}-clang"
    export CC_aarch64_linux_android="$PREBUILT/bin/aarch64-linux-android${API}-clang"
    export AR_aarch64_linux_android="$PREBUILT/bin/llvm-ar"
    build_and_copy aarch64-linux-android arm64-v8a
    echo "Done (NDK clang linker)."
    exit 0
  fi
fi

# Termux: host is often aarch64-linux-android — still build from source, never check in.
HOST="$(rustc -vV | sed -n 's/^host: //p')"
echo "Host triple: $HOST (NDK not fully configured; building host target from source)"
if [[ "$HOST" == aarch64-linux-android ]]; then
  cargo build --release --features jni
  mkdir -p "$JNI_LIBS/arm64-v8a"
  cp -f "$CRATE/target/release/$LIB_NAME" "$JNI_LIBS/arm64-v8a/$LIB_NAME"
  echo "  wrote $JNI_LIBS/arm64-v8a/$LIB_NAME (host aarch64-linux-android, from source)"
  echo "Done."
  exit 0
fi

echo "error: cannot build Android liblauncher_perf from source." >&2
echo "Install cargo-ndk and set ANDROID_NDK_HOME, or run on aarch64-linux-android with cargo." >&2
exit 1
