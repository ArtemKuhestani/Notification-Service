# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Создаём пользователя для запуска приложения
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Копируем JAR файл
COPY --from=build /app/target/*.jar app.jar

# Создаём директорию для логов
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

USER appuser

# Настройки JVM
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+UseContainerSupport"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
