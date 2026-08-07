package com.example.chat.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.example.chat")
@EntityScan(basePackages = "com.example.chat.core.entity")
@EnableJpaRepositories(basePackages = "com.example.chat.core.repository")
public class ChatWsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatWsApplication.class, args);
    }
}
