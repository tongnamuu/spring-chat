package com.example.chat.core.service;

import com.example.chat.common.exception.ChatException;
import com.example.chat.common.exception.ErrorCode;
import com.example.chat.core.entity.UserEntity;
import com.example.chat.core.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

public class UserRegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserEntity register(String email, String password, String nickname) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ChatException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        UserEntity user = UserEntity.builder()
                .email(normalizedEmail)
                .nickname(nickname)
                .passwordHash(passwordEncoder.encode(password))
                .build();
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Normalize a concurrent unique-key race to the same domain response.
            throw new ChatException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }
}
