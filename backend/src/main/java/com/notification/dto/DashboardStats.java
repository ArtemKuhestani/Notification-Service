package com.notification.dto;

import com.notification.controller.AdminDashboardController.ChannelStat;
import com.notification.controller.AdminDashboardController.DailyStat;

import java.util.List;

/**
 * DTO для статистики дашборда.
 */
public record DashboardStats(
    int totalNotifications,
    int sentCount,
    int failedCount,
    int pendingCount,
    int processingCount,
    int todayTotal,
    int todaySent,
    int todayFailed,
    int activeClients,
    int activeChannels,
    List<ChannelStat> channelStats,
    List<DailyStat> dailyStats
) {}
