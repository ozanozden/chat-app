package com.backend.chatapp.domain;

import com.backend.chatapp.domain.exception.InvalidMessageException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class Message {
    private UUID conversationId;
    private UUID senderId;
    private String text;
    private Instant createdAt;

    @JsonCreator
    public Message(
            @JsonProperty("conversationId") UUID conversationId,
            @JsonProperty("senderId") UUID senderId,
            @JsonProperty("text") String text,
            @JsonProperty("createdAt") Instant createdAt) {
        if (text == null || text.trim().isEmpty()) {
            throw new InvalidMessageException("Message text cannot be empty");
        }
        if (text.length() > 5000) {
            throw new InvalidMessageException("Message text too long (max 5000 chars)");
        }

        this.conversationId = conversationId;
        this.senderId = senderId;
        this.text = text;
        this.createdAt = createdAt;
    }
}
