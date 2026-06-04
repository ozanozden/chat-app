package com.backend.chatapp.infrastructure.entity;

import lombok.Getter;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("messages_by_conversation")
@Getter
public class MessageEntity {

    @PrimaryKeyColumn(name = "conversation_id", type = PrimaryKeyType.PARTITIONED)
    private UUID conversationId;

    @PrimaryKeyColumn(name = "created_at", ordinal = 1, ordering = Ordering.DESCENDING)
    private Instant createdAt;

    @Column("message_text")
    private String messageText;

    @Column("sender_id")
    private UUID senderId;

    public MessageEntity(UUID conversationId, Instant createdAt, String messageText, UUID senderId) {
        this.conversationId = conversationId;
        this.createdAt = createdAt;
        this.messageText = messageText;
        this.senderId = senderId;
    }
}
