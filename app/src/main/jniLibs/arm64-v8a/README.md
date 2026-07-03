MNN native runtime libraries for the arm64-v8a MNN backend.

MNN is Copyright 2018 Alibaba Group and licensed under Apache-2.0. These are modified object-code
builds; the corresponding upstream source, local patch, and build recipe are identified below.
See the repository's `THIRD_PARTY_NOTICES.md` and `LICENSE-TERMINAL-EMULATOR` (Apache-2.0 text).

These binaries are built from upstream MNN **3.6.0** (tag `3.6.0`) by the
`.github/workflows/build_mnn_native.yml` GitHub Actions workflow:

- `libMNN.so` — MNN core with the LLM engine bundled (`MNN_BUILD_LLM=ON`,
  `MNN_SEP_BUILD=OFF`, vision + OpenCL + audio), built via `project/android/build_64.sh`.
- `libmnnllmapp.so` — the MnnLlmChat JNI bridge (`apps/Android/MnnLlmChat/app/src/main/cpp`),
  which at 3.6.0 includes `utf8_stream_processor.hpp`, fixing the streaming UTF-8 (emoji)
  `NewStringUTF` crash present in the previous 0.8.3 binaries.

Built with NDK r27c, `ANDROID_STL=c++_static`, min API 30. The Java shim
`com.alibaba.mnnllm.android.llm.LlmSession` matches the JNI method names and signatures
exported by this `libmnnllmapp.so`; only `submitStructuredChatNative` was removed upstream,
and the runtime falls back (UnsatisfiedLinkError) when it is absent.
