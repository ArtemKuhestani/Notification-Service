package com.notification.service;

import com.notification.domain.entity.Admin;
import com.notification.dto.*;
import com.notification.repository.AdminRepository;
import com.notification.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Сервис аутентификации администраторов.
 */
@Service
public class AuthService {
    
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;
    
    private final AdminRepository adminRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    
    public AuthService(
            AdminRepository adminRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.adminRepository = adminRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }
    
    /**
     * Аутентификация администратора.
     */
    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress) {
        log.info("Login attempt for email: {}", request.email());
        
        Optional<Admin> adminOpt = adminRepository.findByEmail(request.email());
        
        if (adminOpt.isEmpty()) {
            log.warn("Login failed: user not found for email {}", request.email());
            throw new AuthenticationException("Неверный email или пароль");
        }
        
        Admin admin = adminOpt.get();
        
        // Проверка блокировки
        if (admin.lockedUntil() != null && admin.lockedUntil().isAfter(LocalDateTime.now())) {
            log.warn("Login failed: account locked for email {}", request.email());
            throw new AuthenticationException(
                "Аккаунт заблокирован. Попробуйте через " + 
                java.time.Duration.between(LocalDateTime.now(), admin.lockedUntil()).toMinutes() + " минут"
            );
        }
        
        // Проверка активности
        if (!admin.isActive()) {
            log.warn("Login failed: account inactive for email {}", request.email());
            throw new AuthenticationException("Аккаунт деактивирован");
        }
        
        // Проверка пароля
        if (!passwordEncoder.matches(request.password(), admin.passwordHash())) {
            int attempts = admin.failedLoginAttempts() + 1;
            
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                adminRepository.lockAccount(admin.adminId(), LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
                log.warn("Account locked after {} failed attempts for email {}", attempts, request.email());
                throw new AuthenticationException("Аккаунт заблокирован на " + LOCK_DURATION_MINUTES + " минут");
            }
            
            adminRepository.incrementFailedAttempts(admin.adminId());
            log.warn("Login failed: invalid password for email {} (attempt {})", request.email(), attempts);
            throw new AuthenticationException("Неверный email или пароль");
        }
        
        // Успешная аутентификация
        adminRepository.updateLastLogin(admin.adminId(), LocalDateTime.now(), ipAddress);
        
        String accessToken = jwtService.generateToken(admin.email(), admin.role(), admin.adminId());
        String refreshToken = jwtService.generateRefreshToken(admin.email());
        
        auditLogService.logAction(
            admin.adminId(),
            "LOGIN",
            "admin",
            String.valueOf(admin.adminId()),
            null,
            null,
            ipAddress,
            null
        );
        
        log.info("Login successful for email: {}", request.email());
        
        return new AuthResponse(
            accessToken,
            refreshToken,
            "Bearer",
            28800, // 8 hours in seconds
            new AdminInfo(admin.adminId(), admin.email(), admin.fullName(), admin.role())
        );
    }
    
    /**
     * Обновление access токена.
     */
    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();
        
        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new AuthenticationException("Недействительный refresh токен");
        }
        
        String email = jwtService.extractEmail(refreshToken);
        
        Optional<Admin> adminOpt = adminRepository.findByEmail(email);
        if (adminOpt.isEmpty() || !adminOpt.get().isActive()) {
            throw new AuthenticationException("Пользователь не найден или деактивирован");
        }
        
        if (!jwtService.isTokenValid(refreshToken, email)) {
            throw new AuthenticationException("Refresh токен истёк");
        }
        
        Admin admin = adminOpt.get();
        String newAccessToken = jwtService.generateToken(admin.email(), admin.role(), admin.adminId());
        String newRefreshToken = jwtService.generateRefreshToken(admin.email());
        
        log.info("Token refreshed for email: {}", email);
        
        return new AuthResponse(
            newAccessToken,
            newRefreshToken,
            "Bearer",
            28800,
            new AdminInfo(admin.adminId(), admin.email(), admin.fullName(), admin.role())
        );
    }
    
    /**
     * Выход (логаут). JWT stateless, но логируем действие.
     */
    public void logout(Integer adminId, String ipAddress) {
        auditLogService.logAction(
            adminId,
            "LOGOUT",
            "admin",
            String.valueOf(adminId),
            null,
            null,
            ipAddress,
            null
        );
        log.info("Logout for adminId: {}", adminId);
    }
    
    /**
     * Исключение аутентификации.
     */
    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
}
