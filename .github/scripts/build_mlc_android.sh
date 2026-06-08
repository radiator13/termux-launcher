#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
source_dir="$root/third-party/mlc-llm"
package_dir="$root/mlc-package"

export MLC_LLM_SOURCE_DIR="$source_dir"
export TVM_SOURCE_DIR="$source_dir/3rdparty/tvm"
export ANDROID_NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$ANDROID_NDK" ]]; then
  echo "ANDROID_NDK_HOME or ANDROID_NDK_ROOT is required" >&2
  exit 1
fi
export TVM_NDK_CC="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang"

cd "$package_dir"
python -m mlc_llm package
test -f dist/lib/mlc4j/output/arm64-v8a/libtvm4j_runtime_packed.so
