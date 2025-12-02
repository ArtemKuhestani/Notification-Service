package com.notification.controller;

import com.notification.repository.ChannelConfigRepository;
import com.notification.service.channel.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер для метрик и health-check.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final JdbcClient jdbcClient;
    private final ChannelConfigRepository channelConfigRepository;
    private final ChannelRouter channelRouter;

    public HealthController(JdbcClient jdbcClient,
                            ChannelConfigRepository channelConfigRepository,
                            ChannelRouter channelRouter) {
        this.jdbcClient = jdbcClient;
        this.channelConfigRepository = channelConfigRepository;
        this.channelRouter = channelRouter;
    }

    /**
     * Базовая проверка здоровья сервиса.
     */
    @GetMapping("/health")
    public HealthResponse health() {
        boolean dbHealthy = checkDatabase();
        String status = dbHealthy ? "UP" : "DOWN";
        
        return new HealthResponse(
            status,
            LocalDateTime.now(),
            Map.of(
                "database", dbHealthy ? "UP" : "DOWN"
            )
        );
    }

    /**
     * Расширенная проверка здоровья с проверкой всех каналов.
     */
    @GetMapping("/health/detailed")
    public DetailedHealthResponse healthDetailed() {
        boolean dbHealthy = checkDatabase();
        Map<String, ChannelHealth> channels = checkChannels();
        
        boolean allHealthy = dbHealthy && channels.values().stream()
            .allMatch(ch -> ch.status().equals("UP") || !ch.enabled());
        
        return new DetailedHealthResponse(
            allHealthy ? "UP" : "DEGRADED",
            LocalDateTime.now(),
            new ComponentsHealth(
                dbHealthy ? "UP" : "DOWN",
                channels
            )
        );
    }

    /**
     * Получить метрики системы.
     */
    @GetMapping("/metrics")
    public MetricsResponse metrics() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

        long uptime = runtime.getUptime();
        Duration uptimeDuration = Duration.ofMillis(uptime);

        // Метрики уведомлений
        int totalNotifications = jdbcClient.sql("SELECT COUNT(*) FROM notifications")
            .query(Integer.class).single();
        int pendingNotifications = jdbcClient.sql(
            "SELECT COUNT(*) FROM notifications WHERE status IN ('PENDING', 'PROCESSING')")
            .query(Integer.class).single();
        int failedNotifications = jdbcClient.sql(
            "SELECT COUNT(*) FROM notifications WHERE status = 'FAILED'")
            .query(Integer.class).single();

        // Метрики очереди retry
        int retryQueueSize = jdbcClient.sql(
            "SELECT COUNT(*) FROM retry_queue WHERE scheduled_at <= NOW() AND status = 'PENDING'")
            .query(Integer.class).single();

        return new MetricsResponse(
            new SystemMetrics(
                formatDuration(uptimeDuration),
                memory.getHeapMemoryUsage().getUsed() / 1024 / 1024,
                memory.getHeapMemoryUsage().getMax() / 1024 / 1024,
                Runtime.getRuntime().availableProcessors()
            ),
            new NotificationMetrics(
                totalNotifications,
                pendingNotifications,
                failedNotifications,
                retryQueueSize
            )
        );
    }

    /**
     * Получить информацию о приложении.
     */
    @GetMapping("/info")
    public AppInfo info() {
        return new AppInfo(
            "Notification Service",
            "1.0.0",
            "Сервис отправки уведомлений через различные каналы",
            LocalDateTime.now()
        );
    }

    private boolean checkDatabase() {
        try {
            jdbcClient.sql("SELECT 1").query(Integer.class).single();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, ChannelHealth> checkChannels() {
        Map<String, ChannelHealth> result = new HashMap<>();
        
        var configs = channelConfigRepository.findAll();
        for (var config : configs) {
            String channelType = config.channelName();
            boolean enabled = config.isEnabled();
            boolean configured = false;
            
            try {
                var adapterOpt = channelRouter.getAdapter(channelType);
                configured = adapterOpt.isPresent() && adapterOpt.get().isConfigured();
            } catch (Exception e) {
                // Канал не настроен
            }
            
            String status = enabled && configured ? "UP" : (enabled ? "MISCONFIGURED" : "DISABLED");
            result.put(channelType, new ChannelHealth(status, enabled, configured));
        }
        
        return result;
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        
        if (days > 0) {
            return String.format("%d д %d ч %d мин", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%d ч %d мин", hours, minutes);
        } else {
            return String.format("%d мин", minutes);
        }
    }

    // DTO классы
    public record HealthResponse(
        String status,
        LocalDateTime timestamp,
        Map<String, String> components
    ) {}

    public record DetailedHealthResponse(
        String status,
        LocalDateTime timestamp,
        ComponentsHealth components
    ) {}

    public record ComponentsHealth(
        String database,
        Map<String, ChannelHealth> channels
    ) {}

    public record ChannelHealth(
        String status,
        boolean enabled,
        boolean configured
    ) {}

    public record MetricsResponse(
        SystemMetrics system,
        NotificationMetrics notifications
    ) {}

    public record SystemMetrics(
        String uptime,
        long heapUsedMb,
        long heapMaxMb,
        int availableProcessors
    ) {}

    public record NotificationMetrics(
        int total,
        int pending,
        int failed,
        int retryQueueSize
    ) {}

    public record AppInfo(
        String name,
        String version,
        String description,
        LocalDateTime serverTime
    ) {}
}
