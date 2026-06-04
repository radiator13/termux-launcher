package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class TaiNotificationSummarizer {
    @NonNull
    public JSONObject summarize(@NonNull JSONObject snapshot, @NonNull String range) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("range", range);
        data.put("privacy", "Notification text is sensitive and stays inside the authenticated local API response.");

        if (!snapshot.optBoolean("listenerConnected", false)) {
            data.put("available", false);
            data.put("summary", "Notification listener access is not connected.");
            data.put("hint", snapshot.optString("hint", "Enable notification listener access for Termux Launcher."));
            data.put("settingsAction", snapshot.optString("settingsAction", ""));
            return data;
        }

        JSONArray notifications = snapshot.optJSONArray("notifications");
        if (notifications == null) notifications = new JSONArray();
        data.put("available", true);
        data.put("notificationCount", notifications.length());
        data.put("summary", "Stub summarizer found " + notifications.length() + " active notifications. Model-based ranking is TODO.");
        data.put("important", new JSONArray());
        data.put("promotionalOrLowPriority", new JSONArray());
        data.put("todo", "Use a capable local model to classify actionable versus promotional notifications.");
        return data;
    }
}
