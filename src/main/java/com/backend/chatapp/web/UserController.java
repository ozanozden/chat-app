package com.backend.chatapp.web;

import com.backend.chatapp.application.UserService;
import com.backend.chatapp.domain.User;
import com.backend.chatapp.web.dto.CreateUserRequest;
import com.backend.chatapp.web.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@AllArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody @Valid CreateUserRequest request) {
        User user = userService.createUser(request.name(), request.email());
        UserResponse response = new UserResponse(user.getId(), user.getName(), user.getEmail());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers().stream()
            .map(u -> new UserResponse(u.getId(), u.getName(), u.getEmail()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }
}
