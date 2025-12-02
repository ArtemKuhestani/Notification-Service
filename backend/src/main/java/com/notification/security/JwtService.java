package com.notification.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Сервис для работы с JWT токенами.
 */
@Service
public class JwtService {
    
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    
    @Value("${jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String secretKey;
    
    @Value("${jwt.expiration:28800000}") // 8 часов по умолчанию
    private long jwtExpiration;
    
    @Value("${jwt.refresh-expiration:604800000}") // 7 дней
    private long refreshExpiration;
    
    /**
     * Извлекает email из токена.
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    /**
     * Извлекает конкретное значение из токена.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    /**
     * Генерирует access токен.
     */
    public String generateToken(String email, String role, Integer adminId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("adminId", adminId);
        claims.put("type", "access");
        return buildToken(claims, email, jwtExpiration);
    }
    
    /**
     * Генерирует refresh токен.
     */
    public String generateRefreshToken(String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return buildToken(claims, email, refreshExpiration);
    }
    
    /**
     * Проверяет валидность токена.
     */
    public boolean isTokenValid(String token, String email) {
        try {
            final String tokenEmail = extractEmail(token);
            return (tokenEmail.equals(email)) && !isTokenExpired(token);
        } catch (Exception e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Проверяет, что токен является access токеном.
     */
    public boolean isAccessToken(String token) {
        try {
            String type = extractClaim(token, claims -> claims.get("type", String.class));
            return "access".equals(type);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Проверяет, что токен является refresh токеном.
     */
    public boolean isRefreshToken(String token) {
        try {
            String type = extractClaim(token, claims -> claims.get("type", String.class));
            return "refresh".equals(type);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Извлекает роль из токена.
     */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }
    
    /**
     * Извлекает ID администратора из токена.
     */
    public Integer extractAdminId(String token) {
        return extractClaim(token, claims -> claims.get("adminId", Integer.class));
    }
    
    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }
    
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
    
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    private SecretKey getSignInKey() {
        // Конвертируем hex строку в bytes
        byte[] keyBytes = hexStringToByteArray(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
