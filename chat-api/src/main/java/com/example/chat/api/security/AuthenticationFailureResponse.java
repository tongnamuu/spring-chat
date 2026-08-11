package com.example.chat.api.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class AuthenticationFailureResponse {

    public static final String CODE = "AUTHENTICATION_FAILED";
    public static final String MESSAGE = "Invalid email or password";

    private AuthenticationFailureResponse() {
    }

    public static void write(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(("{\"code\":\"" + CODE + "\",\"message\":\"" + MESSAGE + "\"}")
                .getBytes(StandardCharsets.UTF_8));
    }
}
