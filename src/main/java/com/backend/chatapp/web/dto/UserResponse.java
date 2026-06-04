package com.backend.chatapp.web.dto;

import java.util.UUID;

public record UserResponse(
    UUID id,
    String name,
    String email
) {}
