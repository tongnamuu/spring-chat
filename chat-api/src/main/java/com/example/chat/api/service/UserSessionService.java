package com.example.chat.api.service;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

@Service
public class UserSessionService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public UserSessionService(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public void invalidateAll(String principalName) {
        sessionRepository.findByPrincipalName(principalName)
                .keySet()
                .forEach(sessionRepository::deleteById);
    }
}
