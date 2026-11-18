package com.company.itam.auth.security;

import java.io.IOException;
import java.util.ArrayList;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import com.company.itam.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Uses the shared error shape so the frontend handles authentication errors consistently. */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String ERROR_CODE = "UNAUTHORIZED";

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;

    @Autowired
    public JwtAuthenticationEntryPoint(
            ObjectMapper objectMapper, MessageSource messageSource, LocaleResolver localeResolver) {
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        String message =
                messageSource.getMessage(ERROR_CODE, null, "Unauthorized", localeResolver.resolveLocale(request));

        ErrorResponse body = new ErrorResponse(message, ERROR_CODE, new ArrayList<>());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
