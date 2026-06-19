package com.termux.launcherctl;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A single notification event persisted from {@link LauncherCtlNotificationListener}.
 */
public final class LauncherCtlNotificationEvent {
    public final long id;
    public final String eventType;
    public final long eventTime;
    public final String key;
    public final String packageName;
    public final String title;
    public final String text;
    public final String subText;
    public final String bigText;
    public final String category;
    public final long postTime;
    public final boolean isOngoing;
    public final boolean isClearable;
    public final JSONObject notification;

    public LauncherCtlNotificationEvent(String eventType, long eventTime, JSONObject notification) {
        this(-1, eventType, eventTime, notification);
    }

    public LauncherCtlNotificationEvent(long id, String eventType, long eventTime, JSONObject notification) {
        this.id = id;
        this.eventType = eventType;
        this.eventTime = eventTime;
        this.notification = notification != null ? notification : new JSONObject();
        this.key = jsonStringOrNull(this.notification, "key");
        this.packageName = jsonStringOrNull(this.notification, "packageName");
        this.title = jsonStringOrNull(this.notification, "title");
        this.text = jsonStringOrNull(this.notification, "text");
        this.subText = jsonStringOrNull(this.notification, "subText");
        this.bigText = jsonStringOrNull(this.notification, "bigText");
        this.category = jsonStringOrNull(this.notification, "category");
        this.postTime = this.notification.optLong("postTime", 0);
        this.isOngoing = this.notification.optBoolean("isOngoing", false);
        this.isClearable = this.notification.optBoolean("isClearable", false);
    }

    private static String jsonStringOrNull(JSONObject json, String key) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return null;
        }
        return json.optString(key, null);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject data = new JSONObject();
        if (id >= 0) {
            data.put("id", id);
        }
        data.put("eventType", eventType);
        data.put("eventTime", eventTime);
        data.put("notification", new JSONObject(notification.toString()));
        return data;
    }
}
