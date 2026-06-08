#include <jni.h>

extern "C" JNIEXPORT jboolean JNICALL
Java_com_termux_ai_LlamaCppBridge_nativeAvailable(JNIEnv *, jclass) {
    return JNI_FALSE;
}
