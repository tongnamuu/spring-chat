package com.example.chat.api.controller;

import com.example.chat.common.dto.UserDto;
import com.example.chat.common.exception.ErrorCode;
import com.example.chat.common.exception.ChatException;
import com.example.chat.api.service.UserAccountService;
import com.example.chat.api.service.UserSessionService;
import com.example.chat.core.entity.UserEntity;
import com.example.chat.core.repository.UserRepository;
import com.example.chat.core.security.ChatPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserAccountService userAccountService;
    private final UserSessionService userSessionService;

    public UserController(
            UserRepository userRepository,
            UserAccountService userAccountService,
            UserSessionService userSessionService
    ) {
        this.userRepository = userRepository;
        this.userAccountService = userAccountService;
        this.userSessionService = userSessionService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserDto> getUser(@PathVariable("userId") Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ChatException(ErrorCode.USER_NOT_FOUND));
        return ResponseEntity.ok(convertToDto(user));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal ChatPrincipal principal,
            @Valid @RequestBody WithdrawRequest request,
            HttpServletRequest servletRequest
    ) {
        userAccountService.withdraw(principal.userId(), request.password());

        HttpSession currentSession = servletRequest.getSession(false);
        if (currentSession != null) {
            currentSession.invalidate();
        }
        userSessionService.invalidateAll(principal.getName());
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    private UserDto convertToDto(UserEntity user) {
        if (user.isWithdrawn()) {
            return UserDto.builder()
                    .userId(user.getUserId())
                    .nickname(UserEntity.WITHDRAWN_NICKNAME)
                    .build();
        }
        return UserDto.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .build();
    }

    public record WithdrawRequest(
            @NotBlank(message = "Password is required")
            @Size(max = 72, message = "Password must not exceed 72 characters")
            String password
    ) {
    }

}
