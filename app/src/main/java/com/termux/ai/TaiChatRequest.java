package com.termux.ai;

import androidx.annotation.NonNull;

import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.ToolProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TaiChatRequest {
    public final String systemPrompt;
    public final List<Message> initialMessages;
    public final Message message;
    public final List<ToolProvider> tools;
    public final boolean reusableConversation;

    public TaiChatRequest(
        @NonNull String systemPrompt,
        @NonNull List<Message> initialMessages,
        @NonNull Message message,
        @NonNull List<ToolProvider> tools,
        boolean reusableConversation
    ) {
        this.systemPrompt = systemPrompt;
        this.initialMessages = Collections.unmodifiableList(new ArrayList<>(initialMessages));
        this.message = message;
        this.tools = Collections.unmodifiableList(new ArrayList<>(tools));
        this.reusableConversation = reusableConversation;
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
}
