package com.example.chat.api.controller;

import com.example.chat.common.dto.UserDto;
import com.example.chat.common.exception.ChatException;
import com.example.chat.common.exception.ErrorCode;
import com.example.chat.core.entity.UserEntity;
import com.example.chat.core.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ChatException(ErrorCode.ALREADY_JOINED, "Username already exists");
        }

        UserEntity user = UserEntity.builder()
                .username(request.getUsername())
                .nickname(request.getNickname())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        UserEntity saved = userRepository.save(user);
        return ResponseEntity.ok(convertToDto(saved));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserDto> getUser(@PathVariable("userId") Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ChatException(ErrorCode.USER_NOT_FOUND));
        return ResponseEntity.ok(convertToDto(user));
    }

    private UserDto convertToDto(UserEntity user) {
        return UserDto.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }

    public static class CreateUserRequest {
        @NotBlank(message = "Username is required")
        private String username;

        @NotBlank(message = "Nickname is required")
        private String nickname;

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        private String password;

        public CreateUserRequest() {}

        public CreateUserRequest(String username, String nickname, String password) {
            this.username = username;
            this.nickname = nickname;
            this.password = password;
        }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
