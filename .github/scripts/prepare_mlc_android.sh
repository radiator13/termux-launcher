#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
source_dir="$root/third-party/mlc-llm"
dist_dir="$root/mlc-package/dist/lib/mlc4j"
release_url="https://github.com/mlc-ai/binary-mlc-llm-libs/releases/download/Android-09262024/mlc-chat.apk"
release_sha256="277f1587b7260aeff3a072946a0004e0f0cc65fe6e7a8062e7728382509deb33"
runtime_sha256="5b6b3a27cbf17372c81c8f354b46cd8858354e613d36c12179df8905a6d22cc0"
archive="${RUNNER_TEMP:-$root/.artifacts}/mlc-chat-Android-09262024.apk"

mkdir -p "$(dirname "$archive")" "$root/mlc-package/dist/lib"
if ! printf '%s  %s\n' "$release_sha256" "$archive" | sha256sum --check --status 2>/dev/null; then
  curl --fail --location --retry 3 --output "$archive" "$release_url"
fi
printf '%s  %s\n' "$release_sha256" "$archive" | sha256sum --check --status

rm -rf "$dist_dir"
cp -R "$source_dir/android/mlc4j" "$dist_dir"
cp "$root/.github/templates/mlc4j.build.gradle" "$dist_dir/build.gradle"
rm -f "$dist_dir/src/main/java/ai/mlc/mlcllm/MLCEngine.kt" \
  "$dist_dir/src/main/java/ai/mlc/mlcllm/OpenAIProtocol.kt"
cp -R "$source_dir/3rdparty/tvm/jvm/core/src/main/java/org" "$dist_dir/src/main/java/"
mkdir -p "$dist_dir/output/arm64-v8a"
unzip -p "$archive" lib/arm64-v8a/libtvm4j_runtime_packed.so \
  > "$dist_dir/output/arm64-v8a/libtvm4j_runtime_packed.so"
printf '%s  %s\n' "$runtime_sha256" \
  "$dist_dir/output/arm64-v8a/libtvm4j_runtime_packed.so" | sha256sum --check --status
