package com.backend.chatapp.infrastructure.mapper;

import com.backend.chatapp.domain.Message;
import com.backend.chatapp.infrastructure.entity.MessageEntity;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class MessageMapper {

    public Message toMessage(MessageEntity messageEntity) {
        return new Message(
                messageEntity.getConversationId(),
                messageEntity.getSenderId(),
                messageEntity.getMessageText(),
                messageEntity.getCreatedAt()
        );
    }

    public MessageEntity toMessageEntity(Message message) {
        return new MessageEntity(
                message.getConversationId(),
                message.getCreatedAt(),
                message.getText(),
                message.getSenderId()
        );
    }
}
