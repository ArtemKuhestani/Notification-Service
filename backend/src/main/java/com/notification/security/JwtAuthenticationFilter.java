package com.notification.security;

import com.notification.repository.AdminRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Фильтр для проверки JWT токенов администраторов.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    private final JwtService jwtService;
    private final AdminRepository adminRepository;
    
    public JwtAuthenticationFilter(JwtService jwtService, AdminRepository adminRepository) {
        this.jwtService = jwtService;
        this.adminRepository = adminRepository;
    }
    
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        
        // Если нет Bearer токена, продолжаем цепочку фильтров
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            final String jwt = authHeader.substring(7);
            final String email = jwtService.extractEmail(jwt);
            
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Проверяем что это access токен
                if (!jwtService.isAccessToken(jwt)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                
                // Проверяем существование и активность администратора
                var adminOpt = adminRepository.findByEmail(email);
                if (adminOpt.isPresent() && adminOpt.get().isActive()) {
                    var admin = adminOpt.get();
                    
                    if (jwtService.isTokenValid(jwt, email)) {
                        String role = jwtService.extractRole(jwt);
                        
                        var authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + role)
                        );
                        
                        var authToken = new UsernamePasswordAuthenticationToken(
                                email,
                                null,
                                authorities
                        );
                        
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        
                        // Добавляем adminId в атрибуты запроса для использования в контроллерах
                        request.setAttribute("adminId", admin.adminId());
                        request.setAttribute("adminEmail", email);
                        request.setAttribute("adminRole", role);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("JWT authentication failed: {}", e.getMessage());
        }
        
        filterChain.doFilter(request, response);
    }
}
