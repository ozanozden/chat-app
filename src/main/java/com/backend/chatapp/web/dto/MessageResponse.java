package com.backend.chatapp.web.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
    UUID conversationId,
    UUID senderId,
    String text,
    Instant createdAt
) {}
