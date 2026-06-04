package com.backend.chatapp.domain;

import com.backend.chatapp.domain.exception.InvalidUserException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class User {
    private final UUID id;
    private final String name;
    private final String email;

    @JsonCreator
    public User(
            @JsonProperty("id") UUID id,
            @JsonProperty("name") String name,
            @JsonProperty("email") String email) {
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidUserException("Name cannot be empty");
        }
        if (email == null || !email.contains("@")) {
            throw new InvalidUserException("Invalid email");
        }

        this.id = id;
        this.name = name;
        this.email = email;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
}
