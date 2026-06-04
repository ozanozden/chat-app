package com.backend.chatapp.infrastructure.adapter;

import com.backend.chatapp.application.port.MessageRepository;
import com.backend.chatapp.domain.ConversationSummary;
import com.backend.chatapp.domain.Message;
import com.backend.chatapp.infrastructure.entity.ConversationByUserEntity;
import com.backend.chatapp.infrastructure.entity.MessageEntity;
import com.backend.chatapp.infrastructure.mapper.MessageMapper;
import com.backend.chatapp.infrastructure.dao.ConversationByUserRepositoryDAO;
import com.backend.chatapp.infrastructure.dao.MessageRepositoryDAO;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Cassandra repository adapter implementing message persistence with denormalization.
 *
 * Consistency Configuration (application.yml):
 * - Read: LOCAL_QUORUM (majority of replicas in local datacenter)
 * - Write: LOCAL_QUORUM (implicitly set by Spring Data Cassandra)
 *
 * This ensures read-your-own-write consistency - when a user sends a message,
 * they're guaranteed to see it immediately on refresh (2/3 replica overlap).
 *
 * Denormalization Strategy:
 * - Writes to messages_by_conversation (message storage)
 * - Writes to conversations_by_user for EACH participant (inbox updates)
 * - Trade-off: Write amplification vs instant conversation list queries
 */
@Repository
@AllArgsConstructor
public class MessageRepositoryImpl implements MessageRepository {
    private final MessageRepositoryDAO messageDAO;
    private final ConversationByUserRepositoryDAO conversationDAO;
    private final MessageMapper messageMapper;

    @Override
    public Message save(Message message, List<UUID> participantUserIds) {
        MessageEntity messageEntity = messageMapper.toMessageEntity(message);
        MessageEntity savedMessageEntity = messageDAO.save(messageEntity);

        for (UUID userId : participantUserIds) {
            UUID otherUserId = participantUserIds.stream()
                .filter(id -> !id.equals(userId))
                .findFirst()
                .orElse(null);

            ConversationByUserEntity conversationEntity = new ConversationByUserEntity(
                userId,
                message.getConversationId(),
                message.getCreatedAt(),
                message.getText(),
                otherUserId
            );
            conversationDAO.save(conversationEntity);
        }

        return messageMapper.toMessage(savedMessageEntity);
    }

    @Override
    public List<Message> findByConversationId(UUID conversationId, int limit) {
        List<MessageEntity> messageEntities = messageDAO.findByConversationId(conversationId, limit);
        return messageEntities.stream().map(messageMapper::toMessage).toList();
    }

    @Override
    public List<UUID> findConversationIdsByUserId(UUID userId, int limit) {
        List<ConversationByUserEntity> conversations = conversationDAO.findByUserId(userId, limit);
        return conversations.stream()
            .map(ConversationByUserEntity::getConversationId)
            .toList();
    }

    @Override
    public List<ConversationSummary> findConversationSummariesByUserId(UUID userId, int limit) {
        List<ConversationByUserEntity> conversations = conversationDAO.findByUserId(userId, limit);
        return conversations.stream()
            .sorted((a, b) -> b.getLastMessageTime().compareTo(a.getLastMessageTime())) // Sort by time DESC in Java
            .map(c -> new ConversationSummary(
                c.getConversationId(),
                c.getLastMessageText(),
                c.getLastMessageTime(),
                c.getOtherUserId()
            ))
            .toList();
    }
}
