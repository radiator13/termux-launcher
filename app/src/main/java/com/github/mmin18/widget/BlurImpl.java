/*
 * Copyright 2016 Tu Yimin
 * Licensed under the Apache License, Version 2.0.
 * Modified by Termux Launcher in 2026 for the Termux:Monet-derived blur implementation.
 */
package com.github.mmin18.widget;

import android.content.Context;
import android.graphics.Bitmap;

interface BlurImpl {
    boolean prepare(Context context, Bitmap buffer, float radius);
    void release();
    void blur(Bitmap input, Bitmap output);
}
