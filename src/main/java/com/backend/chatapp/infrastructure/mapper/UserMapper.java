package com.backend.chatapp.infrastructure.mapper;

import com.backend.chatapp.domain.User;
import com.backend.chatapp.infrastructure.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toDomain(UserEntity entity) {
        return new User(
            entity.getId(),
            entity.getName(),
            entity.getEmail()
        );
    }

    public UserEntity toEntity(User domain) {
        return new UserEntity(
            domain.getId(),
            domain.getName(),
            domain.getEmail()
        );
    }
}
