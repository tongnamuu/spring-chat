package com.example.chat.core.service;

import com.example.chat.common.exception.ChatException;
import com.example.chat.common.exception.ErrorCode;
import com.example.chat.core.entity.UserEntity;
import com.example.chat.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRegistrationServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserRegistrationService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new UserRegistrationService(userRepository, passwordEncoder);
    }

    @Test
    void hashesPasswordBeforeSavingUser() {
        when(passwordEncoder.encode("Secure123")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity registered = service.register("Alice@Example.COM", "Secure123", "앨리스");

        ArgumentCaptor<UserEntity> savedUser = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(savedUser.capture());
        verify(passwordEncoder).encode("Secure123");
        assertThat(registered).isSameAs(savedUser.getValue());
        assertThat(savedUser.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(savedUser.getValue().getNickname()).isEqualTo("앨리스");
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getValue().getPasswordHash()).isNotEqualTo("Secure123");
    }

    @Test
    void rejectsKnownDuplicateBeforeEncodingPassword() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("Alice@Example.COM", "Secure123", "앨리스"))
                .isInstanceOfSatisfying(ChatException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void mapsConcurrentUniqueConstraintViolationToDuplicateDomainError() {
        when(passwordEncoder.encode("Secure123")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(UserEntity.class)))
                .thenThrow(new DataIntegrityViolationException("users_email_key"));

        assertThatThrownBy(() -> service.register("Alice@Example.COM", "Secure123", "앨리스"))
                .isInstanceOfSatisfying(ChatException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }
}
