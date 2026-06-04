package com.backend.chatapp.infrastructure.adapter;

import com.backend.chatapp.domain.ConversationSummary;
import com.backend.chatapp.domain.Message;
import com.backend.chatapp.infrastructure.dao.ConversationByUserRepositoryDAO;
import com.backend.chatapp.infrastructure.dao.MessageRepositoryDAO;
import com.backend.chatapp.infrastructure.mapper.MessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MessageRepositoryIntegrationTest {
    // Using the already-running docker-compose Cassandra instance
    // To run: docker-compose up -d cassandra

    @Autowired
    private MessageRepositoryDAO messageDAO;

    @Autowired
    private ConversationByUserRepositoryDAO conversationDAO;

    @Autowired
    private MessageMapper messageMapper;

    private MessageRepositoryImpl messageRepository;

    @BeforeEach
    void setUp() {
        messageRepository = new MessageRepositoryImpl(messageDAO, conversationDAO, messageMapper);
        // Clean up before each test
        messageDAO.deleteAll();
        conversationDAO.deleteAll();
    }

    @Test
    void shouldSaveMessageAndCreateConversationEntriesForBothParticipants() {
        UUID conversationId = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        String messageText = "Hello Bob!";
        Instant now = Instant.now();

        Message message = new Message(conversationId, alice, messageText, now);

        Message savedMessage = messageRepository.save(message, Arrays.asList(alice, bob));

        assertNotNull(savedMessage);
        assertEquals(messageText, savedMessage.getText());

        // Verify Alice's conversation entry
        List<ConversationSummary> aliceConversations = messageRepository.findConversationSummariesByUserId(alice, 10);
        assertEquals(1, aliceConversations.size());
        assertEquals(conversationId, aliceConversations.get(0).getConversationId());
        assertEquals(bob, aliceConversations.get(0).getOtherUserId());
        assertEquals(messageText, aliceConversations.get(0).getLastMessageText());

        // Verify Bob's conversation entry
        List<ConversationSummary> bobConversations = messageRepository.findConversationSummariesByUserId(bob, 10);
        assertEquals(1, bobConversations.size());
        assertEquals(conversationId, bobConversations.get(0).getConversationId());
        assertEquals(alice, bobConversations.get(0).getOtherUserId());
        assertEquals(messageText, bobConversations.get(0).getLastMessageText());
    }

    @Test
    void shouldUpdateConversationWhenNewMessageSent() throws InterruptedException {
        UUID conversationId = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        // First message
        Message message1 = new Message(conversationId, alice, "First message", Instant.now());
        messageRepository.save(message1, Arrays.asList(alice, bob));

        Thread.sleep(10); // Ensure different timestamp

        // Second message
        Message message2 = new Message(conversationId, bob, "Second message", Instant.now());
        messageRepository.save(message2, Arrays.asList(alice, bob));

        // Verify Alice only has ONE conversation (updated, not duplicated)
        List<ConversationSummary> aliceConversations = messageRepository.findConversationSummariesByUserId(alice, 10);
        assertEquals(1, aliceConversations.size());
        assertEquals("Second message", aliceConversations.get(0).getLastMessageText());

        // Verify Bob only has ONE conversation (updated, not duplicated)
        List<ConversationSummary> bobConversations = messageRepository.findConversationSummariesByUserId(bob, 10);
        assertEquals(1, bobConversations.size());
        assertEquals("Second message", bobConversations.get(0).getLastMessageText());
    }

    @Test
    void shouldRetrieveMessagesInDescendingOrderByTime() throws InterruptedException {
        UUID conversationId = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        Message message1 = new Message(conversationId, alice, "First", Instant.now());
        messageRepository.save(message1, Arrays.asList(alice, bob));

        Thread.sleep(10);

        Message message2 = new Message(conversationId, bob, "Second", Instant.now());
        messageRepository.save(message2, Arrays.asList(alice, bob));

        Thread.sleep(10);

        Message message3 = new Message(conversationId, alice, "Third", Instant.now());
        messageRepository.save(message3, Arrays.asList(alice, bob));

        List<Message> messages = messageRepository.findByConversationId(conversationId, 10);

        assertEquals(3, messages.size());
        assertEquals("Third", messages.get(0).getText());
        assertEquals("Second", messages.get(1).getText());
        assertEquals("First", messages.get(2).getText());
    }

    @Test
    void shouldSortConversationsByLastMessageTimeDescending() throws InterruptedException {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID charlie = UUID.randomUUID();

        UUID conv1 = UUID.randomUUID();
        UUID conv2 = UUID.randomUUID();

        // Conversation 1 (older)
        Message msg1 = new Message(conv1, alice, "Old message", Instant.now());
        messageRepository.save(msg1, Arrays.asList(alice, bob));

        Thread.sleep(100);

        // Conversation 2 (newer)
        Message msg2 = new Message(conv2, alice, "New message", Instant.now());
        messageRepository.save(msg2, Arrays.asList(alice, charlie));

        List<ConversationSummary> conversations = messageRepository.findConversationSummariesByUserId(alice, 10);

        assertEquals(2, conversations.size());
        // Should be sorted by time DESC (newer first)
        assertEquals(conv2, conversations.get(0).getConversationId());
        assertEquals("New message", conversations.get(0).getLastMessageText());
        assertEquals(conv1, conversations.get(1).getConversationId());
        assertEquals("Old message", conversations.get(1).getLastMessageText());
    }

    @Test
    void shouldRespectLimitParameter() throws InterruptedException {
        UUID conversationId = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            Message message = new Message(conversationId, alice, "Message " + i, Instant.now());
            messageRepository.save(message, Arrays.asList(alice, bob));
            Thread.sleep(10);
        }

        List<Message> messages = messageRepository.findByConversationId(conversationId, 3);

        assertEquals(3, messages.size());
    }
}
