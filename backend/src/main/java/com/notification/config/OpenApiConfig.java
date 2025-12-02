package com.notification.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация OpenAPI/Swagger документации.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .components(new Components()
                .addSecuritySchemes("apiKey", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-API-Key")
                    .description("API ключ для аутентификации внешних систем"))
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT токен для аутентификации администраторов")))
            .info(new Info()
                .title("Notification Service API")
                .version("1.0.0")
                .description("""
                    # Сервис Уведомлений (Notification Service)
                    
                    API для централизованной отправки уведомлений по различным каналам:
                    - **Email** (SMTP)
                    - **Telegram** (Bot API)
                    - **WhatsApp** (Business API)
                    - **SMS** (через агрегаторы)
                    
                    ## Аутентификация
                    
                    Для внешних систем используйте заголовок `X-API-Key` с вашим API-ключом.
                    
                    Для административной панели используйте Bearer JWT токен.
                    
                    ## Коды ошибок
                    
                    | Код | Описание |
                    |-----|----------|
                    | 400 | Невалидные данные в запросе |
                    | 401 | Не авторизован |
                    | 403 | Доступ запрещён |
                    | 404 | Ресурс не найден |
                    | 429 | Превышен лимит запросов |
                    | 500 | Внутренняя ошибка сервера |
                    """)
                .contact(new Contact()
                    .name("Notification Service Team")
                    .email("support@notification-service.com"))
                .license(new License()
                    .name("MIT")
                    .url("https://opensource.org/licenses/MIT")));
    }
}
