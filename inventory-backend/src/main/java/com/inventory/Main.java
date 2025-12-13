package com.inventory;

import com.inventory.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Main entry point for Inventory Backend Application
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("=================================================");
        logger.info("  ALL STORE STOCK MANAGEMENT - Backend Server   ");
        logger.info("=================================================");

        // Test database connection
        testDatabaseConnection();

        // Add shutdown hook to close connection pool
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down application...");
            DatabaseConnection.closePool();
            logger.info("Application stopped");
        }));

        logger.info("Backend server is ready");
        logger.info("Pool Status: {}", DatabaseConnection.getPoolStats());
    }

    /**
     * Test database connection and query
     */
    private static void testDatabaseConnection() {
        logger.info("Testing database connection...");

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // Test query
            String sql = "SELECT COUNT(*) as total FROM users";
            ResultSet rs = stmt.executeQuery(sql);

            if (rs.next()) {
                int userCount = rs.getInt("total");
                logger.info("✓ Database connection successful!");
                logger.info("✓ Found {} users in database", userCount);
            }

            // Test all tables
            String[] tables = {"users", "suppliers", "products", "transactions", "audit_log"};
            logger.info("Checking tables:");

            for (String table : tables) {
                sql = "SELECT COUNT(*) as count FROM " + table;
                rs = stmt.executeQuery(sql);
                if (rs.next()) {
                    logger.info("  - {}: {} records", table, rs.getInt("count"));
                }
            }

        } catch (Exception e) {
            logger.error("✗ Database connection failed!", e);
            System.exit(1);
        }
    }
}