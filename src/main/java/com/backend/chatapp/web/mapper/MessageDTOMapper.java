package com.backend.chatapp.web.mapper;

import com.backend.chatapp.domain.Message;
import com.backend.chatapp.web.dto.MessageResponse;
import com.backend.chatapp.web.dto.SendMessageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MessageDTOMapper {

    public Message toDomain(SendMessageRequest request) {
        return new Message(
            request.conversationId(),
            request.senderId(),
            request.text(),
            Instant.now()
        );
    }

    public MessageResponse toResponse(Message message) {
        return new MessageResponse(
            message.getConversationId(),
            message.getSenderId(),
            message.getText(),
            message.getCreatedAt()
        );
    }
}
