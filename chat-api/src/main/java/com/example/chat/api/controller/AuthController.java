package com.example.chat.api.controller;

import com.example.chat.api.dto.SignupRequest;
import com.example.chat.api.dto.SignupResponse;
import com.example.chat.api.security.AuthenticationFailureResponse;
import com.example.chat.common.dto.UserDto;
import com.example.chat.core.entity.UserEntity;
import com.example.chat.core.repository.UserRepository;
import com.example.chat.core.security.ChatPrincipal;
import com.example.chat.core.service.UserRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserRepository userRepository;
    private final UserRegistrationService userRegistrationService;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            UserRepository userRepository,
            UserRegistrationService userRegistrationService
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.userRepository = userRepository;
        this.userRegistrationService = userRegistrationService;
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest signupRequest) {
        UserEntity user = userRegistrationService.register(
                signupRequest.username(),
                signupRequest.password(),
                signupRequest.nickname());
        return ResponseEntity.status(HttpStatus.CREATED).body(SignupResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            Authentication verified = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            loginRequest.username(), loginRequest.password()));
            UserEntity user = userRepository.findByUsername(verified.getName()).orElseThrow();
            ChatPrincipal principal = new ChatPrincipal(user.getUserId(), user.getUsername(), user.getNickname());
            Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, verified.getAuthorities());

            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) {
                existingSession.invalidate();
            }

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authenticated);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            return ResponseEntity.ok(toDto(principal));
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", AuthenticationFailureResponse.CODE,
                    "message", AuthenticationFailureResponse.MESSAGE));
        }
    }

    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        return toDto(requirePrincipal(authentication));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    private ChatPrincipal requirePrincipal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof ChatPrincipal principal) {
            return principal;
        }
        throw new IllegalStateException("Authenticated chat principal is missing");
    }

    private UserDto toDto(ChatPrincipal principal) {
        return UserDto.builder()
                .userId(principal.userId())
                .username(principal.username())
                .nickname(principal.nickname())
                .build();
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required") String username,
            @NotBlank(message = "Password is required") String password
    ) {
    }
}
