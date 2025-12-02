package com.notification.controller;

import com.notification.dto.*;
import com.notification.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Контроллер аутентификации.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "API для аутентификации администраторов")
public class AuthController {
    
    private final AuthService authService;
    
    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    
    @PostMapping("/login")
    @Operation(summary = "Вход в систему", description = "Аутентификация администратора по email и паролю")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = getClientIp(httpRequest);
        AuthResponse response = authService.login(request, ipAddress);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/refresh")
    @Operation(summary = "Обновление токена", description = "Получение нового access токена по refresh токену")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/logout")
    @Operation(summary = "Выход из системы", description = "Логаут администратора")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest httpRequest) {
        Integer adminId = (Integer) httpRequest.getAttribute("adminId");
        String ipAddress = getClientIp(httpRequest);
        
        if (adminId != null) {
            authService.logout(adminId, ipAddress);
        }
        
        return ResponseEntity.ok(Map.of("message", "Успешный выход из системы"));
    }
    
    @GetMapping("/me")
    @Operation(summary = "Текущий пользователь", description = "Получение информации о текущем администраторе")
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpServletRequest httpRequest) {
        Integer adminId = (Integer) httpRequest.getAttribute("adminId");
        String email = (String) httpRequest.getAttribute("adminEmail");
        String role = (String) httpRequest.getAttribute("adminRole");
        
        if (adminId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Не авторизован"));
        }
        
        return ResponseEntity.ok(Map.of(
            "id", adminId,
            "email", email,
            "role", role
        ));
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
