package com.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Главный класс приложения Notification Service.
 * 
 * Сервис уведомлений — микросервис для централизованной отправки сообщений
 * по различным каналам связи (Email, Telegram, WhatsApp, SMS).
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
