package com.backend.chatapp.infrastructure.dao;

import com.backend.chatapp.infrastructure.entity.ConversationByUserEntity;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ConversationByUserRepositoryDAO extends CassandraRepository<ConversationByUserEntity, UUID> {
    @Query("SELECT * FROM conversations_by_user WHERE user_id = ?0 LIMIT ?1")
    List<ConversationByUserEntity> findByUserId(UUID userId, int limit);
}
