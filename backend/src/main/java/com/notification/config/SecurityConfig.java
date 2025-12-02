package com.notification.config;

import com.notification.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Конфигурация безопасности Spring Security.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Отключаем CSRF для REST API
            .csrf(csrf -> csrf.disable())
            
            // Включаем CORS (использует WebMvcConfigurer)
            .cors(Customizer.withDefaults())
            
            // Stateless сессии (используем JWT)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Настройка авторизации запросов
            .authorizeHttpRequests(auth -> auth
                // Preflight OPTIONS запросы
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                // Публичные эндпоинты
                .requestMatchers(
                    "/api/v1/health",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api-docs/**",
                    "/v3/api-docs/**",
                    "/actuator/health",
                    "/actuator/info",
                    "/actuator/metrics"
                ).permitAll()
                
                // API для отправки уведомлений (защищён API-ключом через фильтр)
                .requestMatchers("/api/v1/send/**", "/api/v1/status/**", "/api/v1/retry/**").permitAll()
                
                // Административные эндпоинты требуют аутентификации
                .requestMatchers("/api/v1/admin/**").authenticated()
                .requestMatchers("/api/v1/auth/logout", "/api/v1/auth/me").authenticated()
                
                // Все остальные запросы требуют аутентификации
                .anyRequest().authenticated()
            )
            
            // Добавляем JWT фильтр
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
