package com.inventory.handler;

import com.inventory.database.DatabaseConnection;
import com.inventory.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for User-related database operations (CRUD)
 * Passwords are stored in plain text ONLY for educational purposes
 * In real production systems, passwords must be hashed (e.g., BCrypt)
 * */

public class UserHandler {
    private static final Logger logger = LoggerFactory.getLogger(UserHandler.class);

    /**
     * Authenticate user by username and password
     * SIMPLIFIED: Direct password comparison (no hashing)
     */
    public User loginUser(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            logger.warn("Login attempt with empty username");
            return null;
        }
        if (password == null || password.isEmpty()) {
            logger.warn("Login attempt with empty password");
            return null;
        }

        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                User user = mapResultSetToUser(rs);
                logger.info("User '{}' logged in successfully", username);
                return user;
            } else {
                logger.warn("Invalid username or password for '{}'", username);
                return null;
            }

        } catch (SQLException e) {
            logger.error("Error during login for user '{}'", username, e);
            return null;
        }
    }

    /**
     * Register a new user
     * SIMPLIFIED: Stores plain text password
     */
    public boolean registerUser(User user) {
        // Validation
        if (user.getUsername() == null || user.getUsername().trim().length() < 3) {
            logger.error("Username must be at least 3 characters");
            return false;
        }
        if (user.getPassword() == null || user.getPassword().length() < 6) {
            logger.error("Password must be at least 6 characters");
            return false;
        }
        if (user.getRole() == null || user.getRole().trim().isEmpty()) {
            logger.error("Role cannot be empty");
            return false;
        }

        // Check if a username already exists
        if (usernameExists(user.getUsername())) {
            logger.error("Username '{}' already exists", user.getUsername());
            return false;
        }

        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPassword()); // Plain text password
            pstmt.setString(3, user.getRole());

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                ResultSet generatedKeys = pstmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    user.setUserId(generatedKeys.getInt(1));
                }
                logger.info("User '{}' registered successfully", user.getUsername());
                return true;
            }

            return false;

        } catch (SQLException e) {
            logger.error("Error registering user '{}'", user.getUsername(), e);
            return false;
        }
    }

    /**
     * Get user by ID
     */
    public User getUserById(int userId) {
        String sql = "SELECT * FROM users WHERE user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToUser(rs);
            }

        } catch (SQLException e) {
            logger.error("Error fetching user with ID {}", userId, e);
        }

        return null;
    }

    /**
     * Get all users
     */
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY user_id";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                users.add(mapResultSetToUser(rs));
            }

            logger.info("Retrieved {} users from database", users.size());

        } catch (SQLException e) {
            logger.error("Error fetching all users", e);
        }

        return users;
    }

    /**
     * Update user information
     * SIMPLIFIED: Plain text password
     */
    public boolean updateUser(User user) {
        // Validation
        if (user.getUserId() <= 0) {
            logger.error("Invalid user ID");
            return false;
        }
        if (user.getUsername() == null || user.getUsername().trim().length() < 3) {
            logger.error("Username must be at least 3 characters");
            return false;
        }

        String sql = "UPDATE users SET username = ?, password = ?, role = ? WHERE user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPassword()); // Plain text
            pstmt.setString(3, user.getRole());
            pstmt.setInt(4, user.getUserId());

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("User '{}' updated successfully", user.getUsername());
                return true;
            }

            return false;

        } catch (SQLException e) {
            logger.error("Error updating user '{}'", user.getUsername(), e);
            return false;
        }
    }

    /**
     * Delete user by ID
     */
    public boolean deleteUser(int userId) {
        if (userId <= 0) {
            logger.error("Invalid user ID");
            return false;
        }

        String sql = "DELETE FROM users WHERE user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("User with ID {} deleted successfully", userId);
                return true;
            }

            return false;

        } catch (SQLException e) {
            logger.error("Error deleting user with ID {}", userId, e);
            return false;
        }
    }

    /**
     * Check if username already exists
     */
    public boolean usernameExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            logger.error("Error checking if username exists", e);
        }

        return false;
    }

    /**
     * Change user password
     * SIMPLIFIED: No old password verification, plain text
     * oldPassword is ignored in a simplified educational version
     */

    public boolean changePassword(int userId, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            logger.error("New password must be at least 6 characters");
            return false;
        }

        String sql = "UPDATE users SET password = ? WHERE user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newPassword);
            pstmt.setInt(2, userId);

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Password changed successfully for user ID {}", userId);
                return true;
            }

            return false;

        } catch (SQLException e) {
            logger.error("Error changing password for user ID {}", userId, e);
            return false;
        }
    }

    /**
     * Map ResultSet to User object
     */
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("user_id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("role"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    // ========== TESTING METHOD ==========
    public static void main(String[] args) {
        UserHandler handler = new UserHandler();

        System.out.println("=== Testing UserHandler (Simplified) ===\n");

        // Test 1: Get all users
        System.out.println("1. Getting all users:");
        List<User> users = handler.getAllUsers();
        users.forEach(u -> System.out.println(u.getUsername() + " - " + u.getRole()));

        // Test 2: Login
        System.out.println("\n2. Testing login (admin/admin123):");
        User loggedInUser = handler.loginUser("admin", "admin123");
        if (loggedInUser != null) {
            System.out.println("✓ Login successful: " + loggedInUser);
        } else {
            System.out.println("✗ Login failed");
        }

        // Test 3: Register new user
        System.out.println("\n3. Registering new user:");
        User newUser = new User("testuser", "test123456", "Cashier");
        boolean registered = handler.registerUser(newUser);
        System.out.println(registered ? "✓ User registered" : "✗ Registration failed");

        // Test 4: Login with new user
        if (registered) {
            System.out.println("\n4. Testing login with new user:");
            User testLogin = handler.loginUser("testuser", "test123456");
            System.out.println(testLogin != null ? "✓ Login successful" : "✗ Login failed");

            // Test 5: Delete user
            System.out.println("\n5. Deleting test user:");
            boolean deleted = handler.deleteUser(newUser.getUserId());
            System.out.println(deleted ? "✓ User deleted" : "✗ Deletion failed");
        }

        // Close connection pool
        DatabaseConnection.closePool();
        System.out.println("\n=== Test completed ===");
    }
}