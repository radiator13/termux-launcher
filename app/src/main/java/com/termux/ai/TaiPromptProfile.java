package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class TaiPromptProfile {
    public static final String GENERAL_CHAT = "general_chat";
    public static final String TERMINAL_HELPER = "terminal_helper";
    public static final String CODING_ASSISTANT = "coding_assistant";
    public static final String BUILD_AGENT = "build_agent";
    public static final String NOTIFICATION_SUMMARIZER = "notification_summarizer";
    public static final String MOBILE_ACTION_ROUTER = "mobile_action_router";
    public static final String IMAGE_QUESTION = "image_question";
    public static final String AUDIO_SCRIBE = "audio_scribe";
    public static final String PROMPT_LAB = "prompt_lab";

    private TaiPromptProfile() {
    }

    @NonNull
    public static JSONArray builtInsJson() throws JSONException {
        JSONArray profiles = new JSONArray();
        profiles.put(profile(GENERAL_CHAT, "General chat", "Local assistant for direct questions."));
        profiles.put(profile(TERMINAL_HELPER, "Terminal helper", "Plan safe Termux/Linux commands and avoid destructive execution."));
        profiles.put(profile(CODING_ASSISTANT, "Coding assistant", "Explain code, builds, and repository tasks."));
        profiles.put(profile(BUILD_AGENT, "Build agent", "Detect build systems and plan monitored or memory-saving builds."));
        profiles.put(profile(NOTIFICATION_SUMMARIZER, "Notification summarizer", "Summarize sensitive local notification snapshots."));
        profiles.put(profile(MOBILE_ACTION_ROUTER, "Mobile action router", "Map natural language to safe launcher/device actions."));
        profiles.put(profile(IMAGE_QUESTION, "Image question", "Future multimodal image prompt profile."));
        profiles.put(profile(AUDIO_SCRIBE, "Audio scribe", "Future transcription and audio summary profile."));
        profiles.put(profile(PROMPT_LAB, "Prompt lab", "Debug raw prompts, options, requests, and responses."));
        return profiles;
    }

    private static JSONObject profile(String id, String title, String description) throws JSONException {
        JSONObject profile = new JSONObject();
        profile.put("id", id);
        profile.put("title", title);
        profile.put("description", description);
        return profile;
    }
}
