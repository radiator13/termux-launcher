package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registry of MLC native libraries that are bundled inside the APK.
 *
 * <p>This registry is the trust boundary for executable MLC artifacts.
 * Only library IDs present here may be loaded, and only from the app's
 * JNI libs directory ({@code Context.getApplicationInfo().nativeLibraryDir}).
 *
 * <p>Expected integration path for real MLC artifacts (documented because
 * no published Maven AAR is available yet):
 * <ol>
 *   <li>Build the MLC Android runtime via {@code mlc_llm package}.
 *   <li>Copy the generated {@code .so} files into {@code app/src/main/jniLibs/&lt;abi>/}.</li>
 *   <li>Add an entry to this registry with the correct ABI, library names,
 *       capabilities, and SHA-256 of each bundled {@code .so}.</li>
 *   <li>At runtime {@link MlcTaiRuntime#load()} verifies the device ABI,
 *       looks up the registry entry, and confirms every listed native library
 *       exists in the APK-bundled JNI directory.</li>
 * </ol>
 */
public final class MlcBundledLibraryRegistry {
    public static final String ABI_ARM64_V8A = "arm64-v8a";

    public static final class Entry {
        public final String modelLibraryId;
        public final String requiredAbi;
        public final Set<String> nativeLibraryNames;
        public final Set<String> capabilities;
        public final String sha256;

        Entry(
            @NonNull String modelLibraryId,
            @NonNull String requiredAbi,
            @NonNull Set<String> nativeLibraryNames,
            @NonNull Set<String> capabilities,
            @NonNull String sha256
        ) {
            this.modelLibraryId = modelLibraryId;
            this.requiredAbi = requiredAbi;
            this.nativeLibraryNames = Collections.unmodifiableSet(new LinkedHashSet<>(nativeLibraryNames));
            this.capabilities = Collections.unmodifiableSet(new LinkedHashSet<>(capabilities));
            this.sha256 = sha256;
        }
    }

    private static final Map<String, Entry> REGISTRY = new LinkedHashMap<>();

    static {
        // Placeholder entry for arm64-v8a.
        // The SHA-256 is a placeholder (all zeros). Replace with the real hash
        // of the bundled library after running the MLC packaging pipeline.
        REGISTRY.put("phi-3-mini-mlc", new Entry(
            "phi-3-mini-mlc",
            ABI_ARM64_V8A,
            setOf("libtvm4j_runtime_packed.so", "libmlc_runtime.so", "libmodel_android.so"),
            setOf(TaiModelSpec.CAPABILITY_TEXT_CHAT),
            "0000000000000000000000000000000000000000000000000000000000000000"
        ));
    }

    @SafeVarargs
    private static <T> Set<T> setOf(T... items) {
        LinkedHashSet<T> set = new LinkedHashSet<>();
        Collections.addAll(set, items);
        return set;
    }

    @Nullable
    public static Entry get(@NonNull String modelLibraryId) {
        return REGISTRY.get(modelLibraryId);
    }

    public static boolean contains(@NonNull String modelLibraryId) {
        return REGISTRY.containsKey(modelLibraryId);
    }

    @NonNull
    public static Set<String> supportedAbis() {
        Set<String> abis = new LinkedHashSet<>();
        for (Entry entry : REGISTRY.values()) {
            abis.add(entry.requiredAbi);
        }
        return Collections.unmodifiableSet(abis);
    }

    public static boolean isAbiSupported(@NonNull String abi) {
        for (Entry entry : REGISTRY.values()) {
            if (entry.requiredAbi.equals(abi)) {
                return true;
            }
        }
        return false;
    }
}
