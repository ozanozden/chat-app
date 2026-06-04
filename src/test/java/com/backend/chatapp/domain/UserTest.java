package com.backend.chatapp.domain;

import com.backend.chatapp.domain.exception.InvalidUserException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void shouldCreateValidUser() {
        UUID id = UUID.randomUUID();
        String name = "John Doe";
        String email = "john@example.com";

        User user = new User(id, name, email);

        assertEquals(id, user.getId());
        assertEquals(name, user.getName());
        assertEquals(email, user.getEmail());
    }

    @Test
    void shouldRejectEmptyName() {
        UUID id = UUID.randomUUID();
        String email = "john@example.com";

        InvalidUserException exception = assertThrows(InvalidUserException.class, () -> {
            new User(id, "", email);
        });

        assertEquals("Name cannot be empty", exception.getMessage());
    }

    @Test
    void shouldRejectNullName() {
        UUID id = UUID.randomUUID();
        String email = "john@example.com";

        InvalidUserException exception = assertThrows(InvalidUserException.class, () -> {
            new User(id, null, email);
        });

        assertEquals("Name cannot be empty", exception.getMessage());
    }

    @Test
    void shouldRejectWhitespaceName() {
        UUID id = UUID.randomUUID();
        String email = "john@example.com";

        InvalidUserException exception = assertThrows(InvalidUserException.class, () -> {
            new User(id, "   ", email);
        });

        assertEquals("Name cannot be empty", exception.getMessage());
    }

    @Test
    void shouldRejectInvalidEmail() {
        UUID id = UUID.randomUUID();
        String name = "John Doe";

        InvalidUserException exception = assertThrows(InvalidUserException.class, () -> {
            new User(id, name, "not-an-email");
        });

        assertEquals("Invalid email", exception.getMessage());
    }

    @Test
    void shouldRejectNullEmail() {
        UUID id = UUID.randomUUID();
        String name = "John Doe";

        InvalidUserException exception = assertThrows(InvalidUserException.class, () -> {
            new User(id, name, null);
        });

        assertEquals("Invalid email", exception.getMessage());
    }

    @Test
    void shouldAcceptSimpleEmailValidation() {
        UUID id = UUID.randomUUID();
        String name = "John Doe";
        String email = "john@example.com";

        User user = new User(id, name, email);

        assertNotNull(user);
        assertEquals(email, user.getEmail());
    }
}
