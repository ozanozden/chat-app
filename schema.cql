-- ============================================
-- Chat App Cassandra Schema
-- ============================================

-- Create keyspace (use SimpleStrategy for local dev, NetworkTopologyStrategy for production)
CREATE KEYSPACE IF NOT EXISTS chat_app
WITH replication = {
    'class': 'SimpleStrategy',
    'replication_factor': 1
};

USE chat_app;

-- ============================================
-- Table 1: Messages by Conversation
-- Query: "Get last N messages for conversation X"
-- ============================================
CREATE TABLE IF NOT EXISTS messages_by_conversation (
    conversation_id UUID,
    created_at      TIMESTAMP,
    message_text    TEXT,
    sender_id       UUID,
    PRIMARY KEY (conversation_id, created_at)
) WITH CLUSTERING ORDER BY (created_at DESC)
  AND comment = 'Messages partitioned by conversation, sorted by time descending';

-- ============================================
-- Table 2: Conversations by User
-- Query: "Get all conversations for user X, ordered by recent activity"
-- ============================================
CREATE TABLE IF NOT EXISTS conversations_by_user (
    user_id           UUID,
    conversation_id   UUID,
    last_message_time TIMESTAMP,
    last_message_text TEXT,
    other_user_id     UUID,
    PRIMARY KEY (user_id, conversation_id)
) WITH comment = 'User inbox - all conversations denormalized';