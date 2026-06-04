package com.backend.chatapp.web;

import com.backend.chatapp.application.MessageService;
import com.backend.chatapp.application.UserService;
import com.backend.chatapp.domain.Message;
import com.backend.chatapp.web.dto.ConversationResponse;
import com.backend.chatapp.web.dto.MessageResponse;
import com.backend.chatapp.web.dto.SendMessageRequest;
import com.backend.chatapp.web.mapper.MessageDTOMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class MessageController {
    private final MessageService messageService;
    private final UserService userService;
    private final MessageDTOMapper dtoMapper;

    public MessageController(MessageService messageService, UserService userService, MessageDTOMapper dtoMapper) {
        this.messageService = messageService;
        this.userService = userService;
        this.dtoMapper = dtoMapper;
    }

    @PostMapping("/messages")
    public ResponseEntity<MessageResponse> sendMessage(@RequestBody @Valid SendMessageRequest messageRequest) {
        Message message = dtoMapper.toDomain(messageRequest);
        Message sentMessage = messageService.saveMessage(message, messageRequest.participantUserIds());
        return new ResponseEntity<>(dtoMapper.toResponse(sentMessage), HttpStatus.CREATED);
    }

    @GetMapping("/users/{userId}/conversations")
    public ResponseEntity<List<ConversationResponse>> getUserConversations(@PathVariable UUID userId,
                                                                             @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        List<ConversationResponse> conversations = messageService.findConversationSummariesByUserId(userId, limit).stream()
            .map(summary -> {
                UUID otherUserId = summary.getOtherUserId();

                if (otherUserId == null) {
                    return null;  // Self-conversation or invalid data
                }

                // Fetch user name from cache
                String otherUserName = userService.getUserName(otherUserId);

                return new ConversationResponse(
                    summary.getConversationId(),
                    otherUserId,
                    otherUserName,
                    summary.getLastMessageText(),
                    summary.getLastMessageTime()
                );
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        return ResponseEntity.ok(conversations);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<MessageResponse>> getLastMessagesOfConversation(@PathVariable UUID conversationId,
                                                                               @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        List<Message> conversationMessages = messageService.findByConversationId(conversationId, limit);
        List<MessageResponse> response = conversationMessages.stream().map(dtoMapper::toResponse).toList();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}
