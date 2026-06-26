package it.unimi.dsi.fastutil.ints;

import androidx.annotation.Nullable;

import java.util.HashMap;

/**
 * Minimal adapter for sentencepiece4j's trie usage. Pulling full fastutil into
 * the debug APK is excessive; the tokenizer only calls the primitive get/put
 * overloads implemented here.
 */
public final class Int2ObjectOpenHashMap<V> {
    private final HashMap<Integer, V> values = new HashMap<>();

    @Nullable
    public V get(int key) {
        return values.get(key);
    }

    @Nullable
    public V put(int key, @Nullable V value) {
        return values.put(key, value);
    }
}
