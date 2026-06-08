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

host_marker="$source_dir/build/.tai-full-tvm"
if [[ ! -f "$host_marker" ]]; then
  tokenizer_rs="$source_dir/3rdparty/tokenizers-cpp/rust/src/lib.rs"
  mlc_cmake="$source_dir/CMakeLists.txt"
  sed -i \
    -e 's/(\*handle)\.decode_str\.len()/(\&(*handle).decode_str).len()/g' \
    -e 's/(\*handle)\.id_to_token_result\.len()/(\&(*handle).id_to_token_result).len()/g' \
    "$tokenizer_rs"
  sed -i 's/set(BUILD_DUMMY_LIBTVM ON)/set(BUILD_DUMMY_LIBTVM OFF)/' "$mlc_cmake"
  export RUSTFLAGS="${RUSTFLAGS:-} -A dangerous_implicit_autorefs"
  mkdir -p "$source_dir/build"
  (cd "$source_dir/build" && printf '\nn\nn\nn\nn\nn\n' | python ../cmake/gen_cmake_config.py)
  cmake -S "$source_dir" -B "$source_dir/build" -G Ninja \
    -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
    -DTOKENIZERS_CPP_RUST_FLAGS="-A dangerous_implicit_autorefs"
  cmake --build "$source_dir/build" --parallel 2
  touch "$host_marker"
fi
if [[ "${MLC_HOST_ONLY:-0}" == "1" ]]; then
  exit 0
fi
python -m pip install \
  attrs cloudpickle decorator ml_dtypes numpy packaging psutil scipy tornado typing_extensions
CONDA_BUILD=1 python -m pip install -e "$source_dir/python"
export PYTHONPATH="$source_dir/python:$source_dir/3rdparty/tvm/python${PYTHONPATH:+:$PYTHONPATH}"
export MLC_LIBRARY_PATH="$source_dir/build"
export TVM_LIBRARY_PATH="$source_dir/build/tvm"
export LD_LIBRARY_PATH="$source_dir/build:$source_dir/build/tvm${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

cd "$package_dir"
python -m mlc_llm package
test -f dist/lib/mlc4j/output/arm64-v8a/libtvm4j_runtime_packed.so
