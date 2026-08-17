package com.gurudutta.bank.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurudutta.bank.common.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Without this, an unauthenticated call to a protected endpoint returns Spring Security's default
 * HTML login redirect - useless to a React client. This makes 401/403 come back as the same JSON
 * {@link ApiError} shape as every other error.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, request, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                "AUTHENTICATION_REQUIRED",
                "A valid access token is required to call this endpoint.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       org.springframework.security.access.AccessDeniedException accessDeniedException)
            throws IOException {
        write(response, request, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                "ACCESS_DENIED",
                "You do not have permission to perform this action.");
    }

    private void write(HttpServletResponse response, HttpServletRequest request,
                       int status, String error, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(status, error, code, message, request.getRequestURI()));
    }
}
