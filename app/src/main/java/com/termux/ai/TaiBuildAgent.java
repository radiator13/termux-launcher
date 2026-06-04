package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public final class TaiBuildAgent {
    @NonNull
    public JSONObject plan(@NonNull String workingDirectory, @NonNull String mode) throws JSONException {
        File dir = new File(workingDirectory);
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("workingDirectory", workingDirectory);
        data.put("mode", mode == null || mode.isEmpty() ? "print_command" : mode);
        data.put("autoExecute", false);

        JSONArray systems = new JSONArray();
        JSONArray commands = new JSONArray();
        if (new File(dir, "settings.gradle").exists() || new File(dir, "settings.gradle.kts").exists() || new File(dir, "build.gradle").exists() || new File(dir, "build.gradle.kts").exists()) {
            systems.put("gradle");
            commands.put(TaiShellPlanner.command("Gradle debug build", "./gradlew assembleDebug", false, false));
        }
        if (new File(dir, "Makefile").exists() || new File(dir, "makefile").exists()) {
            systems.put("make");
            commands.put(TaiShellPlanner.command("Make", "make", false, false));
        }
        if (new File(dir, "CMakeLists.txt").exists()) {
            systems.put("cmake");
            commands.put(TaiShellPlanner.command("CMake configure/build", "cmake -S . -B build && cmake --build build", false, false));
        }
        if (new File(dir, "meson.build").exists()) {
            systems.put("meson");
            commands.put(TaiShellPlanner.command("Meson setup/build", "meson setup build && meson compile -C build", false, false));
        }
        if (new File(dir, "Cargo.toml").exists()) {
            systems.put("cargo");
            commands.put(TaiShellPlanner.command("Cargo build", "cargo build", false, false));
        }
        if (new File(dir, "go.mod").exists()) {
            systems.put("go");
            commands.put(TaiShellPlanner.command("Go build", "go build ./...", false, false));
        }
        if (new File(dir, "package.json").exists()) {
            systems.put("node");
            commands.put(TaiShellPlanner.command("Node package build", "npm install && npm run build", true, false));
        }
        if (new File(dir, "pyproject.toml").exists() || new File(dir, "setup.py").exists()) {
            systems.put("python");
            commands.put(TaiShellPlanner.command("Python test/build hint", "python -m build", false, false));
        }

        data.put("detectedBuildSystems", systems);
        data.put("commands", commands);
        if (commands.length() == 0) {
            data.put("summary", "No common build system was detected in the working directory.");
        } else {
            data.put("summary", "Detected build system candidates. Review commands before running.");
        }
        data.put("memoryPressureMode", "Use mode=print_command to unload the model before long builds once a real runtime is integrated.");
        data.put("todo", "Implement monitored build mode with explicit install prompts and model unload/reload policy.");
        return data;
    }
}
