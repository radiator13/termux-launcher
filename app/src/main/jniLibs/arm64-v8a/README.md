MNN native runtime libraries for the arm64-v8a MNN backend.

These binaries were taken from the upstream MNN Chat Android 0.8.3 release APK:
https://meta.alicdn.com/data/mnn/apks/mnn_chat_0_8_3.apk

Included libraries:
- libMNN.so
- libmnnllmapp.so

The Java shim in `com.alibaba.mnnllm.android.llm` preserves the JNI package and
method names exported by `libmnnllmapp.so`.
