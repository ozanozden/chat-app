package com.backend.chatapp.application;

import com.backend.chatapp.application.exception.UserAlreadyExistsException;
import com.backend.chatapp.application.exception.UserNotFoundException;
import com.backend.chatapp.application.port.UserRepository;
import com.backend.chatapp.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        userService = new UserService(userRepository, redisTemplate, objectMapper);
    }

    @Test
    void shouldCreateUserSuccessfully() throws Exception {
        String name = "Alice";
        String email = "alice@example.com";
        User expectedUser = new User(UUID.randomUUID(), name, email);

        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(expectedUser);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":\"...\"}");

        User createdUser = userService.createUser(name, email);

        assertNotNull(createdUser);
        assertEquals(name, createdUser.getName());
        assertEquals(email, createdUser.getEmail());
        verify(userRepository).existsByEmail(email);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() throws Exception {
        String name = "Alice";
        String email = "alice@example.com";

        when(userRepository.existsByEmail(email)).thenReturn(true);

        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class, () -> {
            userService.createUser(name, email);
        });

        assertTrue(exception.getMessage().contains(email));
        verify(userRepository).existsByEmail(email);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldGetUserByIdFromCache() throws Exception {
        UUID userId = UUID.randomUUID();
        String cachedJson = "{\"id\":\"" + userId + "\",\"name\":\"Alice\",\"email\":\"alice@example.com\"}";
        User user = new User(userId, "Alice", "alice@example.com");

        when(valueOperations.get("user:" + userId)).thenReturn(cachedJson);
        when(objectMapper.readValue(cachedJson, User.class)).thenReturn(user);

        String userName = userService.getUserName(userId);

        assertEquals("Alice", userName);
        verify(valueOperations).get("user:" + userId);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void shouldGetUserByIdFromDatabaseWhenCacheMiss() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "Alice", "alice@example.com");

        when(valueOperations.get("user:" + userId)).thenReturn(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":\"...\"}");

        String userName = userService.getUserName(userId);

        assertEquals("Alice", userName);
        verify(valueOperations).get("user:" + userId);
        verify(userRepository).findById(userId);
    }

    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        UUID userId = UUID.randomUUID();

        when(valueOperations.get("user:" + userId)).thenReturn(null);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> {
            userService.getUserName(userId);
        });

        assertTrue(exception.getMessage().contains(userId.toString()));
        verify(userRepository).findById(userId);
    }
}
