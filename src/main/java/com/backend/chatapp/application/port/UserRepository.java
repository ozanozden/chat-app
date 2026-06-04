package com.backend.chatapp.application.port;

import com.backend.chatapp.domain.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(UUID id);
    List<User> findAll();
    List<User> findAllById(List<UUID> ids);
    boolean existsByEmail(String email);
}
