package com.notification.controller;

import com.notification.dto.ApiErrorResponse;
import com.notification.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

/**
 * Глобальный обработчик исключений для REST API.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Обработка ошибок аутентификации.
     */
    @ExceptionHandler(AuthService.AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationError(
            AuthService.AuthenticationException ex,
            HttpServletRequest request) {
        
        log.warn("Authentication error: {}", ex.getMessage());

        ApiErrorResponse response = ApiErrorResponse.of(
            HttpStatus.UNAUTHORIZED.value(),
            "Authentication Failed",
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Обработка ошибок валидации.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        
        String errors = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));

        log.warn("Validation error: {}", errors);

        ApiErrorResponse response = ApiErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            "Validation Error",
            errors,
            request.getRequestURI()
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Обработка IllegalArgumentException.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        
        log.warn("Illegal argument: {}", ex.getMessage());

        ApiErrorResponse response = ApiErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Обработка всех остальных исключений.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {
        
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        ApiErrorResponse response = ApiErrorResponse.of(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "Произошла внутренняя ошибка сервера",
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
