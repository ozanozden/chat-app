package com.backend.chatapp.domain;

import com.backend.chatapp.domain.exception.InvalidMessageException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void shouldCreateValidMessage() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        String text = "Hello, this is a valid message";
        Instant createdAt = Instant.now();

        Message message = new Message(conversationId, senderId, text, createdAt);

        assertEquals(conversationId, message.getConversationId());
        assertEquals(senderId, message.getSenderId());
        assertEquals(text, message.getText());
        assertEquals(createdAt, message.getCreatedAt());
    }

    @Test
    void shouldRejectEmptyMessage() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        InvalidMessageException exception = assertThrows(InvalidMessageException.class, () -> {
            new Message(conversationId, senderId, "", createdAt);
        });

        assertEquals("Message text cannot be empty", exception.getMessage());
    }

    @Test
    void shouldRejectNullMessage() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        InvalidMessageException exception = assertThrows(InvalidMessageException.class, () -> {
            new Message(conversationId, senderId, null, createdAt);
        });

        assertEquals("Message text cannot be empty", exception.getMessage());
    }

    @Test
    void shouldRejectMessageThatIsTooLong() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        String tooLongText = "a".repeat(5001); // Max is 5000 chars

        InvalidMessageException exception = assertThrows(InvalidMessageException.class, () -> {
            new Message(conversationId, senderId, tooLongText, createdAt);
        });

        assertEquals("Message text too long (max 5000 chars)", exception.getMessage());
    }

    @Test
    void shouldAcceptMessageAtMaxLength() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        String maxLengthText = "a".repeat(5000); // Exactly 5000 chars

        Message message = new Message(conversationId, senderId, maxLengthText, createdAt);

        assertNotNull(message);
        assertEquals(5000, message.getText().length());
    }

    @Test
    void shouldTrimWhitespaceAndRejectIfEmpty() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        InvalidMessageException exception = assertThrows(InvalidMessageException.class, () -> {
            new Message(conversationId, senderId, "   ", createdAt);
        });

        assertEquals("Message text cannot be empty", exception.getMessage());
    }
}
