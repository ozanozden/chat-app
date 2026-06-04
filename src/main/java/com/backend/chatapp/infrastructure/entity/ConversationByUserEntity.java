package com.backend.chatapp.infrastructure.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("conversations_by_user")
@Getter
@AllArgsConstructor
public class ConversationByUserEntity {
    @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED)
    private final UUID userId;

    @PrimaryKeyColumn(name = "conversation_id", ordinal = 1)
    private final UUID conversationId;

    @Column("last_message_time")
    private Instant lastMessageTime;

    @Column("last_message_text")
    private String lastMessageText;

    @Column("other_user_id")
    private UUID otherUserId;
}
