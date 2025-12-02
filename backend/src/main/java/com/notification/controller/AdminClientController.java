package com.notification.controller;

import com.notification.domain.entity.ApiClient;
import com.notification.dto.*;
import com.notification.repository.ApiClientRepository;
import com.notification.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Административный контроллер для управления API-клиентами.
 */
@RestController
@RequestMapping("/api/v1/admin/clients")
@Tag(name = "Admin - API Clients", description = "Административные API для управления API-клиентами")
@SecurityRequirement(name = "bearerAuth")
public class AdminClientController {
    
    private final ApiClientRepository apiClientRepository;
    private final AuditLogService auditLogService;
    
    public AdminClientController(
            ApiClientRepository apiClientRepository,
            AuditLogService auditLogService
    ) {
        this.apiClientRepository = apiClientRepository;
        this.auditLogService = auditLogService;
    }
    
    @GetMapping
    @Operation(summary = "Список клиентов", description = "Получение списка всех API-клиентов")
    public ResponseEntity<List<ApiClientDto>> getClients() {
        List<ApiClient> clients = apiClientRepository.findAll();
        List<ApiClientDto> dtos = clients.stream()
            .map(this::toDto)
            .toList();
        return ResponseEntity.ok(dtos);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Детали клиента", description = "Получение информации об API-клиенте")
    public ResponseEntity<ApiClientDto> getClient(@PathVariable Integer id) {
        return apiClientRepository.findById(id)
            .map(this::toDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Создание клиента", description = "Создание нового API-клиента")
    public ResponseEntity<ApiClientCreateResponse> createClient(
            @Valid @RequestBody CreateClientRequest request,
            HttpServletRequest httpRequest
    ) {
        Integer adminId = (Integer) httpRequest.getAttribute("adminId");
        String ipAddress = getClientIp(httpRequest);
        
        // Генерируем API-ключ
        String apiKey = generateApiKey(request.name());
        String apiKeyHash = hashApiKey(apiKey);
        String apiKeyPrefix = apiKey.substring(0, 8);
        
        ApiClient client = new ApiClient(
            null,
            request.name(),
            request.description(),
            apiKeyHash,
            apiKeyPrefix,
            true,
            request.rateLimit() != null ? request.rateLimit() : 100,
            null,
            null,
            request.callbackUrl(),
            null,
            null,
            adminId,
            null
        );
        
        ApiClient saved = apiClientRepository.save(client);
        
        auditLogService.logAction(
            adminId,
            "CREATE",
            "api_client",
            String.valueOf(saved.clientId()),
            null,
            Map.of("name", saved.clientName()),
            ipAddress,
            null
        );
        
        return ResponseEntity.ok(new ApiClientCreateResponse(
            saved.clientId(),
            saved.clientName(),
            apiKey, // Возвращаем ключ только при создании!
            "Сохраните этот ключ, он больше не будет показан"
        ));
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Обновление клиента", description = "Обновление информации API-клиента")
    public ResponseEntity<ApiClientDto> updateClient(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateClientRequest request,
            HttpServletRequest httpRequest
    ) {
        Integer adminId = (Integer) httpRequest.getAttribute("adminId");
        String ipAddress = getClientIp(httpRequest);
        
        var existingOpt = apiClientRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        ApiClient existing = existingOpt.get();
        
        apiClientRepository.update(
            id,
            existing.clientName(), // name не изменяется
            request.description() != null ? request.description() : existing.clientDescription(),
            request.rateLimit() != null ? request.rateLimit() : existing.rateLimit(),
            request.callbackUrl() != null ? request.callbackUrl() : existing.callbackUrlDefault()
        );
        
        auditLogService.logAction(
            adminId,
            "UPDATE",
            "api_client",
            String.valueOf(id),
            Map.of("name", existing.clientName()),
            Map.of("description", request.description()),
            ipAddress,
            null
        );
        
        return apiClientRepository.findById(id)
            .map(this::toDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/{id}/regenerate-key")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Перегенерация ключа", description = "Генерация нового API-ключа для клиента")
    public ResponseEntity<ApiClientCreateResponse> regenerateKey(
            @PathVariable Integer id,
            HttpServletRequest httpRequest
    ) {
        Integer adminId = (Integer) httpRequest.getAttribute("adminId");
        String ipAddress = getClientIp(httpRequest);
        
        var existingOpt = apiClientRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        ApiClient existing = existingOpt.get();
        
        // Генерируем новый ключ
        String apiKey = generateApiKey(existing.clientName());
        String apiKeyHash = hashApiKey(apiKey);
        String apiKeyPrefix = apiKey.substring(0, 8);
        
        apiClientRepository.updateApiKey(id, apiKeyHash, apiKeyPrefix);
        
        auditLogService.logAction(
            adminId,
            "REGENERATE_KEY",
            "api_client",
            String.valueOf(id),
            null,
            Map.of("name", existing.clientName()),
            ipAddress,
            null
        );
        
        return ResponseEntity.ok(new ApiClientCreateResponse(
            id,
            existing.clientName(),
            apiKey,
            "Новый ключ сгенерирован. Сохраните его, он больше не будет показан"
        ));
    }
    
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Активация клиента", description = "Активация API-клиента")
    public ResponseEntity<Map<String, String>> activateClient(
            @PathVariable Integer id,
            HttpServletRequest httpRequest
    ) {
        Integer adminId = (Integer) httpRequest.getAttribute("adminId");
        String ipAddress = getClientIp(httpRequest);
        
        apiClientRepository.updateActive(id, true);
        
        auditLogService.logAction(adminId, "ACTIVATE", "api_client", String.valueOf(id), null, null, ipAddress, null);
        
        return ResponseEntity.ok(Map.of("message", "Клиент активирован"));
    }
    
    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Деактивация клиента", description = "Деактивация API-клиента")
    public ResponseEntity<Map<String, String>> deactivateClient(
            @PathVariable Integer id,
            HttpServletRequest httpRequest
    ) {
        Integer adminId = (Integer) httpRequest.getAttribute("adminId");
        String ipAddress = getClientIp(httpRequest);
        
        apiClientRepository.updateActive(id, false);
        
        auditLogService.logAction(adminId, "DEACTIVATE", "api_client", String.valueOf(id), null, null, ipAddress, null);
        
        return ResponseEntity.ok(Map.of("message", "Клиент деактивирован"));
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Удаление клиента", description = "Удаление API-клиента")
    public ResponseEntity<Map<String, String>> deleteClient(
            @PathVariable Integer id,
            HttpServletRequest httpRequest
    ) {
        Integer adminId = (Integer) httpRequest.getAttribute("adminId");
        String ipAddress = getClientIp(httpRequest);
        
        var existingOpt = apiClientRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        ApiClient existing = existingOpt.get();
        apiClientRepository.deleteById(id);
        
        auditLogService.logAction(
            adminId,
            "DELETE",
            "api_client",
            String.valueOf(id),
            Map.of("name", existing.clientName()),
            null,
            ipAddress,
            null
        );
        
        return ResponseEntity.ok(Map.of("message", "Клиент удалён"));
    }
    
    private ApiClientDto toDto(ApiClient c) {
        return new ApiClientDto(
            c.clientId(),
            c.clientName(),
            c.clientDescription(),
            c.apiKeyPrefix() + "***",
            c.isActive(),
            c.rateLimit(),
            c.createdAt(),
            c.lastUsedAt()
        );
    }
    
    private String generateApiKey(String clientName) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        
        String prefix = "ns_" + clientName.toLowerCase().replaceAll("[^a-z0-9]", "").substring(0, Math.min(4, clientName.length())) + "_";
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash API key", e);
        }
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    
    // DTOs
    public record CreateClientRequest(
        @NotBlank(message = "Название клиента обязательно")
        @Size(max = 100, message = "Название не должно превышать 100 символов")
        String name,
        
        String description,
        Integer rateLimit,
        String callbackUrl
    ) {}
    
    public record UpdateClientRequest(
        String description,
        Integer rateLimit,
        String callbackUrl
    ) {}
    
    public record ApiClientCreateResponse(
        Integer id,
        String name,
        String apiKey,
        String message
    ) {}
}
