package com.backend.chatapp.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
    UUID conversationId,
    UUID otherUserId,
    String otherUserName,
    String lastMessageText,
    Instant lastMessageTime
) {}
