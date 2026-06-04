package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public final class TaiShellPlanner {
    @NonNull
    public JSONObject plan(@NonNull String task, boolean unattendedMode) throws JSONException {
        String normalized = task.trim().toLowerCase(Locale.US);
        JSONObject data = basePlan(task);

        if (normalized.contains("update") && (normalized.contains("package") || normalized.contains("pkg") || normalized.contains("apt") || normalized.contains("pacman"))) {
            data.put("summary", "Detect the active Termux package manager and run the matching interactive update command.");
            JSONArray commands = new JSONArray();
            commands.put(command("Detect package manager",
                "if command -v pkg >/dev/null 2>&1; then echo pkg; elif command -v pacman >/dev/null 2>&1; then echo pacman; elif command -v apt >/dev/null 2>&1; then echo apt; else echo unknown; fi",
                false,
                false));
            commands.put(command("Apt/pkg update path",
                "pkg update && pkg upgrade",
                true,
                false));
            commands.put(command("Pacman update path",
                "pacman -Syu",
                true,
                false));
            data.put("commands", commands);
            data.put("safety", safety("confirmation_required", unattendedMode
                ? "Unattended mode is enabled, but package updates still should be reviewed before execution."
                : "Package updates are not run automatically. Review and execute the matching command yourself."));
            return data;
        }

        if (normalized.contains("neovim") || normalized.contains("nvim")) {
            data.put("summary", "Search common Neovim configuration locations without modifying files.");
            JSONArray commands = new JSONArray();
            commands.put(command("Check standard config directories",
                "find \"$HOME/.config/nvim\" \"$HOME/.local/share/nvim\" \"$HOME/.local/state/nvim\" -maxdepth 3 -type f 2>/dev/null",
                false,
                false));
            data.put("commands", commands);
            data.put("safety", safety("safe", "Read-only find command."));
            return data;
        }

        if (normalized.contains("fish.config") || normalized.contains("config.fish") || normalized.contains("fish config")) {
            data.put("summary", "Search likely fish shell config locations, including the common config.fish filename.");
            JSONArray commands = new JSONArray();
            commands.put(command("Find fish config",
                "find \"$HOME\" \"$PREFIX/etc\" -path '*/.git' -prune -o \\( -name 'config.fish' -o -name 'fish.config' \\) -type f -print 2>/dev/null",
                false,
                false));
            data.put("commands", commands);
            data.put("safety", safety("safe", "Read-only find command with .git pruning."));
            return data;
        }

        data.put("summary", "No exact built-in planner matched. Ask the local model runtime for a plan when LiteRT-LM is enabled.");
        JSONArray commands = new JSONArray();
        commands.put(command("Manual review",
            "printf '%s\\n' 'TAI needs a loaded local model for this terminal plan.'",
            false,
            false));
        data.put("commands", commands);
        data.put("safety", safety("review_required", "Stub planner did not generate an executable command."));
        data.put("todo", "Route unmatched terminal tasks through a loaded model and validate with TaiSafetyPolicy.");
        return data;
    }

    @NonNull
    static JSONObject basePlan(@NonNull String task) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("task", task);
        data.put("mode", "plan_only");
        data.put("autoExecute", false);
        return data;
    }

    @NonNull
    static JSONObject command(String title, String command, boolean confirmationRequired, boolean destructive) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("title", title);
        item.put("command", command);
        item.put("confirmationRequired", confirmationRequired || TaiSafetyPolicy.requiresConfirmation(command));
        item.put("destructive", destructive || TaiSafetyPolicy.isDestructiveCommand(command));
        return item;
    }

    @NonNull
    static JSONObject safety(String level, String note) throws JSONException {
        JSONObject safety = new JSONObject();
        safety.put("level", level);
        safety.put("note", note);
        return safety;
    }
}
