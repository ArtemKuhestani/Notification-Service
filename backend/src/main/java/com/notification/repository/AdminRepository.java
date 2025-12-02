package com.notification.repository;

import com.notification.domain.entity.Admin;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с администраторами.
 */
@Repository
public class AdminRepository {

    private final JdbcClient jdbcClient;

    public AdminRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Находит администратора по email.
     */
    public Optional<Admin> findByEmail(String email) {
        return jdbcClient.sql("""
            SELECT * FROM admins WHERE email = :email
            """)
            .param("email", email.toLowerCase())
            .query((rs, rowNum) -> mapRowToAdmin(rs))
            .optional();
    }

    /**
     * Находит администратора по ID.
     */
    public Optional<Admin> findById(Integer adminId) {
        return jdbcClient.sql("""
            SELECT * FROM admins WHERE admin_id = :id
            """)
            .param("id", adminId)
            .query((rs, rowNum) -> mapRowToAdmin(rs))
            .optional();
    }

    /**
     * Получает всех администраторов.
     */
    public List<Admin> findAll() {
        return jdbcClient.sql("""
            SELECT * FROM admins ORDER BY full_name
            """)
            .query((rs, rowNum) -> mapRowToAdmin(rs))
            .list();
    }

    /**
     * Создаёт нового администратора.
     */
    public Admin save(Admin admin) {
        LocalDateTime now = LocalDateTime.now();

        Integer id = jdbcClient.sql("""
            INSERT INTO admins (
                email, password_hash, full_name, role, is_active,
                failed_login_attempts, created_at, updated_at
            ) VALUES (
                :email, :passwordHash, :fullName, :role, :isActive,
                0, :createdAt, :updatedAt
            ) RETURNING admin_id
            """)
            .param("email", admin.email().toLowerCase())
            .param("passwordHash", admin.passwordHash())
            .param("fullName", admin.fullName())
            .param("role", admin.role())
            .param("isActive", admin.isActive())
            .param("createdAt", now)
            .param("updatedAt", now)
            .query(Integer.class)
            .single();

        return new Admin(
            id, admin.email().toLowerCase(), admin.passwordHash(),
            admin.fullName(), admin.role(), admin.isActive(),
            0, null, null, null, now, now
        );
    }

    /**
     * Обновляет счётчик неудачных попыток входа.
     */
    public void incrementFailedAttempts(Integer adminId) {
        jdbcClient.sql("""
            UPDATE admins 
            SET failed_login_attempts = failed_login_attempts + 1 
            WHERE admin_id = :id
            """)
            .param("id", adminId)
            .update();
    }

    /**
     * Сбрасывает счётчик неудачных попыток и обновляет время входа.
     */
    public void resetFailedAttemptsAndUpdateLogin(Integer adminId, String ipAddress) {
        jdbcClient.sql("""
            UPDATE admins 
            SET failed_login_attempts = 0, 
                locked_until = NULL,
                last_login_at = NOW(),
                last_login_ip = CAST(:ip AS INET)
            WHERE admin_id = :id
            """)
            .param("id", adminId)
            .param("ip", ipAddress)
            .update();
    }

    /**
     * Блокирует аккаунт на указанное время.
     */
    public void lockAccount(Integer adminId, LocalDateTime lockedUntil) {
        jdbcClient.sql("""
            UPDATE admins SET locked_until = :lockedUntil WHERE admin_id = :id
            """)
            .param("id", adminId)
            .param("lockedUntil", lockedUntil)
            .update();
    }

    /**
     * Обновляет время последнего входа и сбрасывает счётчик попыток.
     */
    public void updateLastLogin(Integer adminId, LocalDateTime loginTime, String ipAddress) {
        jdbcClient.sql("""
            UPDATE admins 
            SET last_login_at = :loginTime,
                last_login_ip = CAST(:ip AS INET),
                failed_login_attempts = 0,
                locked_until = NULL,
                updated_at = NOW()
            WHERE admin_id = :id
            """)
            .param("id", adminId)
            .param("loginTime", loginTime)
            .param("ip", ipAddress)
            .update();
    }

    /**
     * Проверяет существование email.
     */
    public boolean existsByEmail(String email) {
        Long count = jdbcClient.sql("""
            SELECT COUNT(*) FROM admins WHERE email = :email
            """)
            .param("email", email.toLowerCase())
            .query(Long.class)
            .single();
        return count > 0;
    }

    /**
     * Обновляет пароль администратора.
     */
    public void updatePassword(Integer adminId, String newPasswordHash) {
        jdbcClient.sql("""
            UPDATE admins SET password_hash = :hash WHERE admin_id = :id
            """)
            .param("id", adminId)
            .param("hash", newPasswordHash)
            .update();
    }

    private Admin mapRowToAdmin(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp lockedUntil = rs.getTimestamp("locked_until");
        Timestamp lastLoginAt = rs.getTimestamp("last_login_at");

        return new Admin(
            rs.getInt("admin_id"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("full_name"),
            rs.getString("role"),
            rs.getBoolean("is_active"),
            rs.getInt("failed_login_attempts"),
            lockedUntil != null ? lockedUntil.toLocalDateTime() : null,
            lastLoginAt != null ? lastLoginAt.toLocalDateTime() : null,
            rs.getString("last_login_ip"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
