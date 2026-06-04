package com.backend.chatapp.infrastructure.dao;

import com.backend.chatapp.infrastructure.entity.MessageEntity;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MessageRepositoryDAO extends CassandraRepository<MessageEntity, UUID> {
    @Query(value = "" +
            "SELECT * FROM messages_by_conversation " +
            "           WHERE conversation_id = ?0 " +
            "           LIMIT ?1" +
            "")
    List<MessageEntity> findByConversationId(UUID conversationId, int limit);
}
