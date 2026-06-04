package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public final class TaiActionRouter {
    @NonNull
    public JSONObject route(@NonNull String request) throws JSONException {
        String normalized = request.toLowerCase(Locale.US);
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("request", request);
        data.put("autoExecute", false);

        if (normalized.contains("flashlight") || normalized.contains("flash light") || normalized.contains("torch")) {
            data.put("action", "flashlight");
            if (normalized.contains("off")) {
                data.put("value", "off");
            } else if (normalized.contains("toggle")) {
                data.put("value", "toggle");
            } else {
                data.put("value", "on");
            }
            data.put("confirmationRequired", false);
            data.put("status", "planned");
            data.put("todo", "Wire safe execution through Android CameraManager with user-visible failure handling.");
            return data;
        }

        if (normalized.startsWith("launch ") || normalized.contains("open ")) {
            data.put("action", "launch_app");
            data.put("query", request.replaceFirst("(?i)^(launch|open)\\s+", "").trim());
            data.put("confirmationRequired", false);
            data.put("status", "planned");
            data.put("executeVia", "/v1/apps/launch");
            return data;
        }

        data.put("action", "unknown");
        data.put("confirmationRequired", true);
        data.put("status", "needs_model_router");
        data.put("todo", "Route through MobileActions-270M or another local action-router model.");
        return data;
    }
}
