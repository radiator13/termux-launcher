#!/usr/bin/env bash
# Build liblauncher_perf.so for Android ABIs and copy into app jniLibs.
# Requires: cargo, and either:
#   - cargo-ndk + ANDROID_NDK_HOME, or
#   - a host aarch64-linux-android target (Termux) for arm64-only packaging.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CRATE="$(cd "$(dirname "$0")" && pwd)"
JNI_LIBS="$ROOT/app/src/main/jniLibs"
LIB_NAME="liblauncher_perf.so"

cd "$CRATE"

build_and_copy() {
  local target="$1"
  local abi="$2"
  echo "Building $target -> jniLibs/$abi"
  cargo build --release --target "$target" --features jni
  mkdir -p "$JNI_LIBS/$abi"
  local src="$CRATE/target/$target/release/$LIB_NAME"
  if [[ ! -f "$src" ]]; then
    # Some targets emit under target/release when --target matches host
    src="$CRATE/target/release/$LIB_NAME"
  fi
  if [[ ! -f "$src" ]]; then
    echo "error: missing $LIB_NAME after build for $target" >&2
    find "$CRATE/target" -name "$LIB_NAME" 2>/dev/null | head
    exit 1
  fi
  cp -f "$src" "$JNI_LIBS/$abi/$LIB_NAME"
  echo "  wrote $JNI_LIBS/$abi/$LIB_NAME ($(wc -c <"$src") bytes)"
}

if command -v cargo-ndk >/dev/null 2>&1 && [[ -n "${ANDROID_NDK_HOME:-}${NDK_HOME:-}" ]]; then
  echo "Using cargo-ndk"
  cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -t x86 -o "$JNI_LIBS" build --release --features jni
  exit 0
fi

HOST="$(rustc -vV | sed -n 's/^host: //p')"
echo "Host triple: $HOST"

# Termux / native aarch64: package arm64-v8a from host build (cdylib for Android needs android target).
# Prefer explicit android targets if installed.
if rustup target list --installed 2>/dev/null | grep -qx 'aarch64-linux-android'; then
  build_and_copy aarch64-linux-android arm64-v8a
elif [[ "$HOST" == aarch64-*-android* ]] || [[ "$HOST" == aarch64-unknown-linux-gnu ]] || [[ "$HOST" == aarch64-* ]]; then
  echo "No aarch64-linux-android target; building host cdylib for arm64-v8a packaging (device/Termux only)"
  cargo build --release --features jni
  mkdir -p "$JNI_LIBS/arm64-v8a"
  SRC=$(find "$CRATE/target/release" -maxdepth 1 -name "$LIB_NAME" | head -1)
  if [[ -z "$SRC" ]]; then
    # Linux may name liblauncher_perf.so
    SRC=$(find "$CRATE/target/release" -maxdepth 1 \( -name "$LIB_NAME" -o -name 'liblauncher_perf.so' \) | head -1)
  fi
  cp -f "$SRC" "$JNI_LIBS/arm64-v8a/$LIB_NAME"
  echo "  wrote $JNI_LIBS/arm64-v8a/$LIB_NAME"
else
  echo "Building host library for tests only; install cargo-ndk or android targets for APK packaging"
  cargo build --release --features jni
  mkdir -p "$JNI_LIBS/arm64-v8a"
  SRC=$(find "$CRATE/target/release" -maxdepth 1 -name 'liblauncher_perf.so' | head -1 || true)
  if [[ -n "$SRC" ]]; then
    cp -f "$SRC" "$JNI_LIBS/arm64-v8a/$LIB_NAME"
  fi
fi

echo "Done."
