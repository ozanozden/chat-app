package com.backend.chatapp.domain;

import java.time.Instant;
import java.util.UUID;

public class ConversationSummary {
    private final UUID conversationId;
    private final String lastMessageText;
    private final Instant lastMessageTime;
    private final UUID otherUserId;

    public ConversationSummary(UUID conversationId, String lastMessageText, Instant lastMessageTime, UUID otherUserId) {
        this.conversationId = conversationId;
        this.lastMessageText = lastMessageText;
        this.lastMessageTime = lastMessageTime;
        this.otherUserId = otherUserId;
    }

    public UUID getConversationId() { return conversationId; }
    public String getLastMessageText() { return lastMessageText; }
    public Instant getLastMessageTime() { return lastMessageTime; }
    public UUID getOtherUserId() { return otherUserId; }
}
