package com.notification.controller;

import com.notification.dto.DashboardStats;
import com.notification.repository.NotificationRepository;
import com.notification.repository.ApiClientRepository;
import com.notification.repository.ChannelConfigRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Контроллер для получения статистики дашборда.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private final JdbcClient jdbcClient;
    private final NotificationRepository notificationRepository;
    private final ApiClientRepository apiClientRepository;
    private final ChannelConfigRepository channelConfigRepository;

    public AdminDashboardController(JdbcClient jdbcClient,
                                     NotificationRepository notificationRepository,
                                     ApiClientRepository apiClientRepository,
                                     ChannelConfigRepository channelConfigRepository) {
        this.jdbcClient = jdbcClient;
        this.notificationRepository = notificationRepository;
        this.apiClientRepository = apiClientRepository;
        this.channelConfigRepository = channelConfigRepository;
    }

    /**
     * Получить общую статистику для дашборда.
     */
    @GetMapping("/stats")
    public DashboardStats getStats() {
        // Общее количество уведомлений
        int totalNotifications = jdbcClient.sql("SELECT COUNT(*) FROM notifications")
            .query(Integer.class)
            .single();

        // Статистика по статусам
        Map<String, Integer> statusCounts = getStatusCounts();

        // Статистика за сегодня
        int todayTotal = getTodayCount();
        int todaySent = getTodaySentCount();
        int todayFailed = getTodayFailedCount();

        // Активные API-клиенты
        int activeClients = jdbcClient.sql("SELECT COUNT(*) FROM api_clients WHERE is_active = true")
            .query(Integer.class)
            .single();

        // Активные каналы
        int activeChannels = jdbcClient.sql("SELECT COUNT(*) FROM channel_configs WHERE is_enabled = true")
            .query(Integer.class)
            .single();

        // Статистика по каналам
        List<ChannelStat> channelStats = getChannelStats();

        // Последние 7 дней
        List<DailyStat> dailyStats = getDailyStats(7);

        return new DashboardStats(
            totalNotifications,
            statusCounts.getOrDefault("SENT", 0),
            statusCounts.getOrDefault("FAILED", 0),
            statusCounts.getOrDefault("PENDING", 0),
            statusCounts.getOrDefault("PROCESSING", 0),
            todayTotal,
            todaySent,
            todayFailed,
            activeClients,
            activeChannels,
            channelStats,
            dailyStats
        );
    }

    /**
     * Получить статистику по каналам за период.
     */
    @GetMapping("/stats/channels")
    public List<ChannelStat> getChannelStatsForPeriod(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        
        LocalDateTime fromDate = from != null ? LocalDate.parse(from).atStartOfDay() : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime toDate = to != null ? LocalDate.parse(to).atTime(23, 59, 59) : LocalDateTime.now();

        return jdbcClient.sql("""
            SELECT channel_type as channel, 
                   COUNT(*) as total,
                   COUNT(*) FILTER (WHERE status = 'SENT') as sent,
                   COUNT(*) FILTER (WHERE status = 'FAILED') as failed
            FROM notifications
            WHERE created_at BETWEEN :from AND :to
            GROUP BY channel_type
            ORDER BY total DESC
            """)
            .param("from", fromDate)
            .param("to", toDate)
            .query((rs, rowNum) -> new ChannelStat(
                rs.getString("channel"),
                rs.getInt("total"),
                rs.getInt("sent"),
                rs.getInt("failed")
            ))
            .list();
    }

    /**
     * Получить статистику по клиентам.
     */
    @GetMapping("/stats/clients")
    public List<ClientStat> getClientStats(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        
        LocalDateTime fromDate = from != null ? LocalDate.parse(from).atStartOfDay() : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime toDate = to != null ? LocalDate.parse(to).atTime(23, 59, 59) : LocalDateTime.now();

        return jdbcClient.sql("""
            SELECT c.client_name as client_name,
                   COUNT(n.notification_id) as total,
                   COUNT(n.notification_id) FILTER (WHERE n.status = 'SENT') as sent,
                   COUNT(n.notification_id) FILTER (WHERE n.status = 'FAILED') as failed
            FROM api_clients c
            LEFT JOIN notifications n ON c.client_id = n.client_id 
                AND n.created_at BETWEEN :from AND :to
            GROUP BY c.client_id, c.client_name
            ORDER BY total DESC
            """)
            .param("from", fromDate)
            .param("to", toDate)
            .query((rs, rowNum) -> new ClientStat(
                rs.getString("client_name"),
                rs.getInt("total"),
                rs.getInt("sent"),
                rs.getInt("failed")
            ))
            .list();
    }

    /**
     * Получить почасовую статистику за день.
     */
    @GetMapping("/stats/hourly")
    public List<HourlyStat> getHourlyStats(@RequestParam(required = false) String date) {
        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime endOfDay = targetDate.atTime(23, 59, 59);

        return jdbcClient.sql("""
            SELECT EXTRACT(HOUR FROM created_at) as hour,
                   COUNT(*) as total,
                   COUNT(*) FILTER (WHERE status = 'SENT') as sent,
                   COUNT(*) FILTER (WHERE status = 'FAILED') as failed
            FROM notifications
            WHERE created_at BETWEEN :start AND :end
            GROUP BY EXTRACT(HOUR FROM created_at)
            ORDER BY hour
            """)
            .param("start", startOfDay)
            .param("end", endOfDay)
            .query((rs, rowNum) -> new HourlyStat(
                rs.getInt("hour"),
                rs.getInt("total"),
                rs.getInt("sent"),
                rs.getInt("failed")
            ))
            .list();
    }

    private Map<String, Integer> getStatusCounts() {
        List<StatusCount> counts = jdbcClient.sql("""
            SELECT status, COUNT(*) as count
            FROM notifications
            GROUP BY status
            """)
            .query((rs, rowNum) -> new StatusCount(
                rs.getString("status"),
                rs.getInt("count")
            ))
            .list();

        return counts.stream()
            .collect(java.util.stream.Collectors.toMap(
                StatusCount::status,
                StatusCount::count
            ));
    }

    private int getTodayCount() {
        return jdbcClient.sql("""
            SELECT COUNT(*) FROM notifications
            WHERE created_at >= CURRENT_DATE
            """)
            .query(Integer.class)
            .single();
    }

    private int getTodaySentCount() {
        return jdbcClient.sql("""
            SELECT COUNT(*) FROM notifications
            WHERE created_at >= CURRENT_DATE AND status = 'SENT'
            """)
            .query(Integer.class)
            .single();
    }

    private int getTodayFailedCount() {
        return jdbcClient.sql("""
            SELECT COUNT(*) FROM notifications
            WHERE created_at >= CURRENT_DATE AND status = 'FAILED'
            """)
            .query(Integer.class)
            .single();
    }

    private List<ChannelStat> getChannelStats() {
        return jdbcClient.sql("""
            SELECT channel_type as channel, 
                   COUNT(*) as total,
                   COUNT(*) FILTER (WHERE status = 'SENT') as sent,
                   COUNT(*) FILTER (WHERE status = 'FAILED') as failed
            FROM notifications
            GROUP BY channel_type
            ORDER BY total DESC
            """)
            .query((rs, rowNum) -> new ChannelStat(
                rs.getString("channel"),
                rs.getInt("total"),
                rs.getInt("sent"),
                rs.getInt("failed")
            ))
            .list();
    }

    private List<DailyStat> getDailyStats(int days) {
        return jdbcClient.sql("""
            SELECT DATE(created_at) as date,
                   COUNT(*) as total,
                   COUNT(*) FILTER (WHERE status = 'SENT') as sent,
                   COUNT(*) FILTER (WHERE status = 'FAILED') as failed
            FROM notifications
            WHERE created_at >= CURRENT_DATE - :days
            GROUP BY DATE(created_at)
            ORDER BY date
            """)
            .param("days", days)
            .query((rs, rowNum) -> new DailyStat(
                rs.getDate("date").toLocalDate().toString(),
                rs.getInt("total"),
                rs.getInt("sent"),
                rs.getInt("failed")
            ))
            .list();
    }

    // Внутренние record-классы для статистики
    private record StatusCount(String status, int count) {}

    public record ChannelStat(String channel, int total, int sent, int failed) {}
    
    public record ClientStat(String clientName, int total, int sent, int failed) {}
    
    public record DailyStat(String date, int total, int sent, int failed) {}
    
    public record HourlyStat(int hour, int total, int sent, int failed) {}
}
