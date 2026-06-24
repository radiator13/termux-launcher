package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.ToolProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TaiChatRequest {
    public final String systemPrompt;
    public final List<Message> initialMessages;
    public final Message message;
    public final List<ToolProvider> tools;
    public final boolean reusableConversation;
    public final JSONArray openAiMessages;
    public final JSONArray openAiTools;
    public final Object openAiToolChoice;

    public TaiChatRequest(
        @NonNull String systemPrompt,
        @NonNull List<Message> initialMessages,
        @NonNull Message message,
        @NonNull List<ToolProvider> tools,
        boolean reusableConversation
    ) {
        this(systemPrompt, initialMessages, message, tools, reusableConversation, new JSONArray(), new JSONArray(), null);
    }

    public TaiChatRequest(
        @NonNull String systemPrompt,
        @NonNull List<Message> initialMessages,
        @NonNull Message message,
        @NonNull List<ToolProvider> tools,
        boolean reusableConversation,
        @NonNull JSONArray openAiMessages,
        @Nullable JSONArray openAiTools,
        @Nullable Object openAiToolChoice
    ) {
        this.systemPrompt = systemPrompt;
        this.initialMessages = Collections.unmodifiableList(new ArrayList<>(initialMessages));
        this.message = message;
        this.tools = Collections.unmodifiableList(new ArrayList<>(tools));
        this.reusableConversation = reusableConversation;
        this.openAiMessages = copyArray(openAiMessages);
        this.openAiTools = openAiTools == null ? new JSONArray() : copyArray(openAiTools);
        this.openAiToolChoice = copyJsonValue(openAiToolChoice);
    }

    @NonNull
    public static TaiChatRequest simple(@NonNull String systemPrompt, @NonNull String userPrompt) {
        return text(systemPrompt, userPrompt, true);
    }

    @NonNull
    public static TaiChatRequest oneShot(@NonNull String systemPrompt, @NonNull String userPrompt) {
        return text(systemPrompt, userPrompt, false);
    }

    @NonNull
    private static TaiChatRequest text(@NonNull String systemPrompt, @NonNull String userPrompt, boolean reusable) {
        return new TaiChatRequest(
            systemPrompt,
            Collections.emptyList(),
            Message.Companion.user(userPrompt),
            Collections.emptyList(),
            reusable
        );
    }

    @NonNull
    private static JSONArray copyArray(@NonNull JSONArray source) {
        try {
            return new JSONArray(source.toString());
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    @Nullable
    private static Object copyJsonValue(@Nullable Object value) {
        if (value == null || JSONObject.NULL.equals(value)) return null;
        try {
            if (value instanceof JSONObject) return new JSONObject(value.toString());
            if (value instanceof JSONArray) return new JSONArray(value.toString());
        } catch (JSONException ignored) {
        }
        return value;
    }
}
