package com.alibaba.mnnllm.android.llm;

import androidx.annotation.Nullable;

public interface GenerateProgressListener {
    boolean onProgress(@Nullable String progress);
}
