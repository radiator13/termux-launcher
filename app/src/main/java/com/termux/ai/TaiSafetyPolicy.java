package com.termux.ai;

import androidx.annotation.NonNull;

import java.util.Locale;

public final class TaiSafetyPolicy {
    private TaiSafetyPolicy() {
    }

    public static boolean isDestructiveCommand(@NonNull String command) {
        String normalized = command.toLowerCase(Locale.US);
        return normalized.contains("rm -rf")
            || normalized.contains(" find ") && normalized.contains(" -delete")
            || normalized.startsWith("find ") && normalized.contains(" -delete")
            || normalized.contains(" mkfs")
            || normalized.startsWith("mkfs")
            || normalized.contains(" dd ")
            || normalized.startsWith("dd ")
            || normalized.contains(" chmod -r")
            || normalized.contains(" chown -r");
    }

    public static boolean requiresConfirmation(@NonNull String command) {
        String normalized = command.toLowerCase(Locale.US);
        return isDestructiveCommand(command)
            || normalized.contains("pkg install")
            || normalized.contains("apt install")
            || normalized.contains("pacman -s")
            || normalized.contains("pkg upgrade")
            || normalized.contains("apt upgrade")
            || normalized.contains("pacman -syu");
    }
}
