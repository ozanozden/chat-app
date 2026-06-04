package com.backend.chatapp.web.dto;

import java.util.List;
import java.util.UUID;

public record SendMessageRequest(
    UUID conversationId,
    UUID senderId,
    String text,
    List<UUID> participantUserIds  // All users in the conversation (including sender)
) {}
