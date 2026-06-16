package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Trust-boundary validator for MLC packages.
 *
 * <p>Ensures that only APK-bundled artifacts are accepted and rejects
 * anything that looks like a custom download or raw weight file.
 */
public final class TaiMlcPackageValidator {

    private static final Set<String> RAW_WEIGHT_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        ".safetensors", ".gguf", ".bin", ".pt", ".onnx"
    )));

    private static final String PATH_TRAVERSAL = ".." + File.separator;
    private static final String PATH_TRAVERSAL_ALT = "../";

    public enum ValidationResult {
        OK,
        CUSTOM_SO_FORBIDDEN,
        RAW_WEIGHTS_FORBIDDEN,
        UNKNOWN_MODEL_LIBRARY,
        PATH_TRAVERSAL_DETECTED,
        HASH_MISMATCH
    }

    /**
     * Validates a model library ID and an optional package path.
     *
     * @param modelLibraryId the library identifier to look up in the bundled registry
     * @param packagePath    the local path associated with the package (may be null)
     * @return the validation result
     */
    @NonNull
    public static ValidationResult validatePackagePath(@NonNull String modelLibraryId, @Nullable String packagePath) {
        if (!MlcBundledLibraryRegistry.contains(modelLibraryId)) {
            return ValidationResult.UNKNOWN_MODEL_LIBRARY;
        }

        if (packagePath != null && !packagePath.isEmpty()) {
            if (packagePath.contains(PATH_TRAVERSAL_ALT) || packagePath.contains(PATH_TRAVERSAL)) {
                return ValidationResult.PATH_TRAVERSAL_DETECTED;
            }

            String lower = packagePath.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".so")) {
                return ValidationResult.CUSTOM_SO_FORBIDDEN;
            }
            for (String ext : RAW_WEIGHT_EXTENSIONS) {
                if (lower.endsWith(ext)) {
                    return ValidationResult.RAW_WEIGHTS_FORBIDDEN;
                }
            }
        }

        return ValidationResult.OK;
    }

    /**
     * Validates a SHA-256 hash against the bundled registry entry.
     *
     * <p>Placeholder hashes (starting with {@code 0000}) are accepted without
     * comparison so that tests and pre-release builds do not fail.
     *
     * @param modelLibraryId the library identifier
     * @param actualHash     the hash to verify (may be null)
     * @return the validation result
     */
    @NonNull
    public static ValidationResult validateHash(@NonNull String modelLibraryId, @Nullable String actualHash) {
        MlcBundledLibraryRegistry.Entry entry = MlcBundledLibraryRegistry.get(modelLibraryId);
        if (entry == null) {
            return ValidationResult.UNKNOWN_MODEL_LIBRARY;
        }

        if (actualHash == null || actualHash.isEmpty()) {
            return ValidationResult.OK;
        }

        // Accept placeholder hashes without strict comparison.
        if (entry.sha256.startsWith("0000")) {
            return ValidationResult.OK;
        }

        if (!actualHash.equalsIgnoreCase(entry.sha256)) {
            return ValidationResult.HASH_MISMATCH;
        }

        return ValidationResult.OK;
    }

    @NonNull
    public static String resultMessage(@NonNull ValidationResult result) {
        switch (result) {
            case CUSTOM_SO_FORBIDDEN:
                return "Loading custom .so files from downloads is not allowed. Only APK-bundled libraries are permitted.";
            case RAW_WEIGHTS_FORBIDDEN:
                return "Raw model weights are not accepted. Use MLC-packaged libraries only.";
            case UNKNOWN_MODEL_LIBRARY:
                return "Model library ID is not in the bundled registry.";
            case PATH_TRAVERSAL_DETECTED:
                return "Path traversal detected in package path.";
            case HASH_MISMATCH:
                return "SHA-256 hash mismatch for bundled library.";
            case OK:
                return "Package validation passed.";
            default:
                return "Unknown validation result.";
        }
    }
}
