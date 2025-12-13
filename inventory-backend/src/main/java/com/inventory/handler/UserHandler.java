package com.inventory.handler;

import com.inventory.database.DatabaseConnection;
import com.inventory.model.User;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for User-related database operations (CRUD)
 * FIXED: Added BCrypt password hashing for security
 */
public class UserHandler {
    private static final Logger logger = LoggerFactory.getLogger(UserHandler.class);
    private static final int BCRYPT_ROUNDS = 12;

    /**
     * Authenticate user by username and password
     * FIXED: Now uses BCrypt to verify password
     * @return User object if authentication successful, null otherwise
     */
    public User loginUser(String username, String password) {
        // Validation
        if (username == null || username.trim().isEmpty()) {
            logger.warn("Login attempt with empty username");
            return null;
        }
        if (password == null || password.isEmpty()) {
            logger.warn("Login attempt with empty password");
            return null;
        }

        String sql = "SELECT * FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password");

                // FIXED: Verify password using BCrypt
                if (BCrypt.checkpw(password, storedHash)) {
                    User user = mapResultSetToUser(rs);
                    logger.info("User '{}' logged in successfully", username);
                    return user;
                } else {
                    logger.warn("Invalid password for user '{}'", username);
                    return null;
                }
            } else {
                logger.warn("User '{}' not found", username);
                return null;
            }

        } catch (SQLException e) {
            logger.error("Error during login for user '{}'", username, e);
            return null;
        }
    }

    /**
     * Register a new user
     * FIXED: Now hashes password before storing
     * @return true if registration successful, false otherwise
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

        // Check if username already exists
        if (usernameExists(user.getUsername())) {
            logger.error("Username '{}' already exists", user.getUsername());
            return false;
        }

        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, user.getUsername());

            // FIXED: Hash password before storing
            String hashedPassword = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt(BCRYPT_ROUNDS));
            pstmt.setString(2, hashedPassword);
            pstmt.setString(3, user.getRole());

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                ResultSet generatedKeys = pstmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    user.setUserId(generatedKeys.getInt(1));
                }
                // Update user object with hashed password
                user.setPassword(hashedPassword);
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
     * @return User object or null if not found
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
     * @return List of all users
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
     * FIXED: Hashes new password if changed
     * @return true if update successful, false otherwise
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

            // FIXED: Hash password if it's not already hashed
            String password = user.getPassword();
            if (!password.startsWith("$2a$") && !password.startsWith("$2b$")) {
                password = BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_ROUNDS));
            }
            pstmt.setString(2, password);
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
     * @return true if deletion successful, false otherwise
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
     * @return true if username exists, false otherwise
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
     * FIXED: New method for secure password change
     */
    public boolean changePassword(int userId, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            logger.error("New password must be at least 6 characters");
            return false;
        }

        // First verify old password
        User user = getUserById(userId);
        if (user == null) {
            logger.error("User not found");
            return false;
        }

        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            logger.warn("Old password is incorrect for user ID {}", userId);
            return false;
        }

        // Update with new hashed password
        String sql = "UPDATE users SET password = ? WHERE user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt(BCRYPT_ROUNDS));
            pstmt.setString(1, hashedPassword);
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

        System.out.println("=== Testing UserHandler with BCrypt ===\n");

        // Test 1: Get all users
        System.out.println("1. Getting all users:");
        List<User> users = handler.getAllUsers();
        users.forEach(u -> System.out.println(u.getUsername() + " - " + u.getRole()));

        // Test 2: Login with hashed password
        System.out.println("\n2. Testing login (admin/admin123):");
        User loggedInUser = handler.loginUser("admin", "admin123");
        if (loggedInUser != null) {
            System.out.println("✓ Login successful: " + loggedInUser);
        } else {
            System.out.println("✗ Login failed");
        }

        // Test 3: Register new user with hashed password
        System.out.println("\n3. Registering new user:");
        User newUser = new User("testuser", "test123456", "Cashier");
        boolean registered = handler.registerUser(newUser);
        System.out.println(registered ? "✓ User registered with hashed password" : "✗ Registration failed");

        // Test 4: Login with new user
        if (registered) {
            System.out.println("\n4. Testing login with new user:");
            User testLogin = handler.loginUser("testuser", "test123456");
            System.out.println(testLogin != null ? "✓ Login successful" : "✗ Login failed");

            // Test 5: Change password
            System.out.println("\n5. Testing password change:");
            boolean changed = handler.changePassword(newUser.getUserId(), "test123456", "newpassword123");
            System.out.println(changed ? "✓ Password changed" : "✗ Password change failed");

            if (changed) {
                User loginWithNew = handler.loginUser("testuser", "newpassword123");
                System.out.println(loginWithNew != null ? "✓ Login with new password successful" : "✗ Login failed");
            }

            // Test 6: Delete user
            System.out.println("\n6. Deleting test user:");
            boolean deleted = handler.deleteUser(newUser.getUserId());
            System.out.println(deleted ? "✓ User deleted" : "✗ Deletion failed");
        }

        // Close connection pool
        DatabaseConnection.closePool();
        System.out.println("\n=== Test completed ===");
    }
}