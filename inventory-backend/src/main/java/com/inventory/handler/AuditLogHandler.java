package com.inventory.handler;

import com.inventory.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for Audit Log operations
 * Tracks all user actions in the system
 */

public class AuditLogHandler {
    private static final Logger logger = LoggerFactory.getLogger(AuditLogHandler.class);

    /**
     * Log a user action to the audit_log table
     * @param userId User who performed the action
     * @param action Action description (e.g., "LOGIN", "ADD_PRODUCT", "DELETE_USER")
     * @param details Additional details about the action
     * @return true if logged successfully
     */

    public boolean logAction(int userId, String action, String details) {
        if (userId <= 0) {
            logger.error("Invalid user ID");
            return false;
        }
        if (action == null || action.trim().isEmpty()) {
            logger.error("Action cannot be empty");
            return false;
        }

        String sql = "INSERT INTO audit_log (user_id, action, details) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.setString(2, action.trim());
            pstmt.setString(3, details);

            int affected = pstmt.executeUpdate();

            if (affected > 0) {
                logger.debug("Action logged: User={}, Action={}", userId, action);
                return true;
            }
            return false;

        } catch (SQLException e) {
            logger.error("Error logging action: {}", action, e);
            return false;
        }
    }

    /**
     * Log action without details
     */

    public boolean logAction(int userId, String action) {
        return logAction(userId, action, null);
    }

    /**
     * Get all audit logs (Admin only)
     * @return List of audit log entries
     */

    public List<AuditLogEntry> getAllLogs() {
        List<AuditLogEntry> logs = new ArrayList<>();
        String sql = "SELECT al.*, u.username FROM audit_log al " +
                "LEFT JOIN users u ON al.user_id = u.user_id " +
                "ORDER BY al.timestamp DESC LIMIT 1000";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                logs.add(new AuditLogEntry(
                        rs.getInt("log_id"),
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("action"),
                        rs.getString("details"),
                        rs.getTimestamp("timestamp").toLocalDateTime()
                ));
            }

            logger.info("Retrieved {} audit log entries", logs.size());

        } catch (SQLException e) {
            logger.error("Error fetching audit logs", e);
        }

        return logs;
    }

    /**
     * Get logs for specific user
     */
    public List<AuditLogEntry> getLogsByUser(int userId) {
        if (userId <= 0) {
            logger.error("Invalid user ID");
            return new ArrayList<>();
        }

        List<AuditLogEntry> logs = new ArrayList<>();
        String sql = "SELECT al.*, u.username FROM audit_log al " +
                "LEFT JOIN users u ON al.user_id = u.user_id " +
                "WHERE al.user_id = ? ORDER BY al.timestamp DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                logs.add(new AuditLogEntry(
                        rs.getInt("log_id"),
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("action"),
                        rs.getString("details"),
                        rs.getTimestamp("timestamp").toLocalDateTime()
                ));
            }

        } catch (SQLException e) {
            logger.error("Error fetching logs for user {}", userId, e);
        }

        return logs;
    }

    /**
     * Get logs by date range
     */
    public List<AuditLogEntry> getLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            logger.error("Date range cannot be null");
            return new ArrayList<>();
        }
        if (startDate.isAfter(endDate)) {
            logger.error("Start date must be before end date");
            return new ArrayList<>();
        }

        List<AuditLogEntry> logs = new ArrayList<>();
        String sql = "SELECT al.*, u.username FROM audit_log al " +
                "LEFT JOIN users u ON al.user_id = u.user_id " +
                "WHERE al.timestamp BETWEEN ? AND ? " +
                "ORDER BY al.timestamp DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setTimestamp(1, Timestamp.valueOf(startDate));
            pstmt.setTimestamp(2, Timestamp.valueOf(endDate));
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                logs.add(new AuditLogEntry(
                        rs.getInt("log_id"),
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("action"),
                        rs.getString("details"),
                        rs.getTimestamp("timestamp").toLocalDateTime()
                ));
            }

        } catch (SQLException e) {
            logger.error("Error fetching logs by date range", e);
        }

        return logs;
    }

    /**
     * Clear old logs (older than specified days)
     */
    public int clearOldLogs(int daysToKeep) {
        if (daysToKeep < 1) {
            logger.error("Days to keep must be positive");
            return 0;
        }

        String sql = "DELETE FROM audit_log WHERE timestamp < NOW() - INTERVAL '1 day' * ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, daysToKeep);
            int deleted = pstmt.executeUpdate();

            logger.info("Cleared {} old audit log entries (older than {} days)", deleted, daysToKeep);
            return deleted;

        } catch (SQLException e) {
            logger.error("Error clearing old logs", e);
            return 0;
        }
    }

    /**
     * Inner class for Audit Log Entry
     */

    public static class AuditLogEntry {
        private int logId;
        private int userId;
        private String username;
        private String action;
        private String details;
        private LocalDateTime timestamp;

        public AuditLogEntry(int logId, int userId, String username, String action,
                             String details, LocalDateTime timestamp) {
            this.logId = logId;
            this.userId = userId;
            this.username = username;
            this.action = action;
            this.details = details;
            this.timestamp = timestamp;
        }

        // Getters
        public int getLogId() { return logId; }
        public int getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getAction() { return action; }
        public String getDetails() { return details; }
        public LocalDateTime getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("[%s] User '%s' (ID:%d): %s - %s",
                    timestamp, username, userId, action, details);
        }
    }
}