package com.example.chat.api.config;

import com.example.chat.core.repository.UserRepository;
import com.example.chat.core.service.UserRegistrationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class UserRegistrationConfig {

    @Bean
    public UserRegistrationService userRegistrationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        return new UserRegistrationService(userRepository, passwordEncoder);
    }
}
