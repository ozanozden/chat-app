package com.backend.chatapp.web.dto;

public record CreateUserRequest(
    String name,
    String email
) {}
