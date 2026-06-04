package com.backend.chatapp.application;

import com.backend.chatapp.application.exception.UserAlreadyExistsException;
import com.backend.chatapp.application.exception.UserNotFoundException;
import com.backend.chatapp.application.port.UserRepository;
import com.backend.chatapp.domain.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final long CACHE_TTL_HOURS = 24;
    private static final String USERS_ALL_KEY = "users:all";

    public User createUser(String name, String email) {
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("User with email already exists: " + email);
        }

        User user = new User(UUID.randomUUID(), name, email);
        User savedUser = userRepository.save(user);

        // Invalidate users list cache
        redisTemplate.delete(USERS_ALL_KEY);

        // Cache individual user
        cacheUser(savedUser);

        return savedUser;
    }

    /**
     * Get all users with cache-aside pattern
     * Caches full user list and individual users
     */
    public List<User> getAllUsers() {
        // Try Redis first
        String cachedJson = redisTemplate.opsForValue().get(USERS_ALL_KEY);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, new TypeReference<List<User>>() {});
            } catch (JsonProcessingException e) {
                // Cache corrupted, fall through to DB
            }
        }

        List<User> users = userRepository.findAll();

        try {
            String userData = objectMapper.writeValueAsString(users);
            redisTemplate.opsForValue().set(USERS_ALL_KEY, userData, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            // Continue without caching
        }

        // Also cache each user individually
        users.forEach(this::cacheUser);

        return users;
    }

    /**
     * Get user by ID with cache-aside pattern
     */
    public User getUserById(UUID id) {
        String cacheKey = buildUserKey(id);

        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, User.class);
            } catch (JsonProcessingException e) {
                // Cache corrupted, fall through to DB
            }
        }

        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + id));

        cacheUser(user);

        return user;
    }

    /**
     * Get user name (uses getUserById which has caching)
     */
    public String getUserName(UUID userId) {
        return getUserById(userId).getName();
    }

    private void cacheUser(User user) {
        String cacheKey = buildUserKey(user.getId());
        try {
            String json = objectMapper.writeValueAsString(user);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            // Continue without caching
        }
    }

    private String buildUserKey(UUID userId) {
        return "user:" + userId;
    }
}
