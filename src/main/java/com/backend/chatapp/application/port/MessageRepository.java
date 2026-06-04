package com.backend.chatapp.application.port;

import com.backend.chatapp.domain.ConversationSummary;
import com.backend.chatapp.domain.Message;
import java.util.List;
import java.util.UUID;

public interface MessageRepository {

    Message save(Message message, List<UUID> participantUserIds);

    List<Message> findByConversationId(UUID conversationId, int limit);

    List<UUID> findConversationIdsByUserId(UUID userId, int limit);

    List<ConversationSummary> findConversationSummariesByUserId(UUID userId, int limit);
}