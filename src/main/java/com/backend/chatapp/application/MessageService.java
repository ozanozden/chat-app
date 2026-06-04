package com.backend.chatapp.application;

import com.backend.chatapp.application.port.MessageRepository;
import com.backend.chatapp.domain.ConversationSummary;
import com.backend.chatapp.domain.Message;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class MessageService {
    private final MessageRepository messageRepository;

    public Message saveMessage(Message message, List<UUID> participantUserIds) {
        return messageRepository.save(message, participantUserIds);
    }

    public List<Message> findByConversationId(UUID conversationId, int limit) {
        return messageRepository.findByConversationId(conversationId, limit);
    }

    public List<UUID> findConversationIdsByUserId(UUID userId, int limit) {
        return messageRepository.findConversationIdsByUserId(userId, limit);
    }

    public List<ConversationSummary> findConversationSummariesByUserId(UUID userId, int limit) {
        return messageRepository.findConversationSummariesByUserId(userId, limit);
    }
}
