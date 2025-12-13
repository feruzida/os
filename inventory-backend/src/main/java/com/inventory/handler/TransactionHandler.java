package com.inventory.handler;

import com.inventory.database.DatabaseConnection;
import com.inventory.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for Transaction-related database operations (CRUD)
 */
public class TransactionHandler {
    private static final Logger logger = LoggerFactory.getLogger(TransactionHandler.class);

    /**
     * Record a new transaction (Sale or Purchase)
     * This method also updates product quantity automatically
     * @return true if transaction recorded successfully
     */
    public boolean recordTransaction(Transaction transaction) {
        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // Start transaction

            // Insert transaction record
            String insertSql = "INSERT INTO transactions (product_id, user_id, txn_type, quantity, total_price, notes) VALUES (?, ?, ?, ?, ?, ?)";
            pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);

            pstmt.setInt(1, transaction.getProductId());
            pstmt.setInt(2, transaction.getUserId());
            pstmt.setString(3, transaction.getTxnType());
            pstmt.setInt(4, transaction.getQuantity());
            pstmt.setBigDecimal(5, transaction.getTotalPrice());
            pstmt.setString(6, transaction.getNotes());

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                ResultSet generatedKeys = pstmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    transaction.setTxnId(generatedKeys.getInt(1));
                }
            }

            // Update product quantity
            String updateSql;
            if (transaction.isSale()) {
                // Sale: decrease quantity
                updateSql = "UPDATE products SET quantity = quantity - ? WHERE product_id = ? AND quantity >= ?";
            } else {
                // Purchase: increase quantity
                updateSql = "UPDATE products SET quantity = quantity + ? WHERE product_id = ?";
            }

            pstmt.close();
            pstmt = conn.prepareStatement(updateSql);
            pstmt.setInt(1, transaction.getQuantity());
            pstmt.setInt(2, transaction.getProductId());

            if (transaction.isSale()) {
                pstmt.setInt(3, transaction.getQuantity());
            }

            int updated = pstmt.executeUpdate();

            if (updated == 0 && transaction.isSale()) {
                conn.rollback();
                logger.error("Insufficient stock for product ID {}", transaction.getProductId());
                return false;
            }

            conn.commit(); // Commit transaction
            logger.info("Transaction recorded: {} of {} units for product ID {}",
                    transaction.getTxnType(), transaction.getQuantity(), transaction.getProductId());
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("Error rolling back transaction", ex);
                }
            }
            logger.error("Error recording transaction", e);
            return false;
        } finally {
            try {
                if (pstmt != null) pstmt.close();
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                logger.error("Error closing resources", e);
            }
        }
    }

    /**
     * Get transaction by ID
     * @return Transaction object or null if not found
     */
    public Transaction getTransactionById(int txnId) {
        String sql = "SELECT * FROM transactions WHERE txn_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, txnId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToTransaction(rs);
            }

        } catch (SQLException e) {
            logger.error("Error fetching transaction with ID {}", txnId, e);
        }

        return null;
    }

    /**
     * Get all transactions
     * @return List of all transactions
     */
    public List<Transaction> getAllTransactions() {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM transactions ORDER BY txn_date DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                transactions.add(mapResultSetToTransaction(rs));
            }

            logger.info("Retrieved {} transactions from database", transactions.size());

        } catch (SQLException e) {
            logger.error("Error fetching all transactions", e);
        }

        return transactions;
    }

    /**
     * Get transactions by type (Sale or Purchase)
     * @return List of transactions of the specified type
     */
    public List<Transaction> getTransactionsByType(String txnType) {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM transactions WHERE txn_type = ? ORDER BY txn_date DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, txnType);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                transactions.add(mapResultSetToTransaction(rs));
            }

            logger.info("Retrieved {} {} transactions", transactions.size(), txnType);

        } catch (SQLException e) {
            logger.error("Error fetching transactions by type '{}'", txnType, e);
        }

        return transactions;
    }

    /**
     * Get transactions by date range
     * @return List of transactions within the date range
     */
    public List<Transaction> getTransactionsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM transactions WHERE txn_date BETWEEN ? AND ? ORDER BY txn_date DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setTimestamp(1, Timestamp.valueOf(startDate));
            pstmt.setTimestamp(2, Timestamp.valueOf(endDate));
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                transactions.add(mapResultSetToTransaction(rs));
            }

            logger.info("Retrieved {} transactions between {} and {}",
                    transactions.size(), startDate, endDate);

        } catch (SQLException e) {
            logger.error("Error fetching transactions by date range", e);
        }

        return transactions;
    }

    /**
     * Get transactions for a specific product
     * @return List of transactions for the product
     */
    public List<Transaction> getTransactionsByProduct(int productId) {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM transactions WHERE product_id = ? ORDER BY txn_date DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, productId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                transactions.add(mapResultSetToTransaction(rs));
            }

            logger.info("Retrieved {} transactions for product ID {}", transactions.size(), productId);

        } catch (SQLException e) {
            logger.error("Error fetching transactions for product ID {}", productId, e);
        }

        return transactions;
    }

    /**
     * Get today's transactions
     * @return List of today's transactions
     */
    public List<Transaction> getTodayTransactions() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        return getTransactionsByDateRange(startOfDay, endOfDay);
    }

    /**
     * Get daily sales report
     * @return Total sales amount for the specified date
     */
    public BigDecimal getDailySales(LocalDate date) {
        String sql = "SELECT COALESCE(SUM(total_price), 0) as total FROM transactions " +
                "WHERE txn_type = 'Sale' AND DATE(txn_date) = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(date));
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getBigDecimal("total");
            }

        } catch (SQLException e) {
            logger.error("Error calculating daily sales for {}", date, e);
        }

        return BigDecimal.ZERO;
    }

    /**
     * Get monthly sales report
     * @return Total sales amount for the specified month
     */
    public BigDecimal getMonthlySales(int year, int month) {
        String sql = "SELECT COALESCE(SUM(total_price), 0) as total FROM transactions " +
                "WHERE txn_type = 'Sale' AND EXTRACT(YEAR FROM txn_date) = ? AND EXTRACT(MONTH FROM txn_date) = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, year);
            pstmt.setInt(2, month);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getBigDecimal("total");
            }

        } catch (SQLException e) {
            logger.error("Error calculating monthly sales for {}-{}", year, month, e);
        }

        return BigDecimal.ZERO;
    }

    /**
     * Map ResultSet to Transaction object
     */
    private Transaction mapResultSetToTransaction(ResultSet rs) throws SQLException {
        return new Transaction(
                rs.getInt("txn_id"),
                rs.getInt("product_id"),
                rs.getInt("user_id"),
                rs.getString("txn_type"),
                rs.getInt("quantity"),
                rs.getBigDecimal("total_price"),
                rs.getTimestamp("txn_date").toLocalDateTime(),
                rs.getString("notes")
        );
    }

    // ========== TESTING METHOD ==========
    public static void main(String[] args) {
        TransactionHandler handler = new TransactionHandler();

        System.out.println("=== Testing TransactionHandler ===\n");

        // Test 1: Get all transactions
        System.out.println("1. Getting all transactions:");
        List<Transaction> transactions = handler.getAllTransactions();
        transactions.forEach(System.out::println);

        // Test 2: Get today's transactions
        System.out.println("\n2. Today's transactions:");
        List<Transaction> todayTxns = handler.getTodayTransactions();
        System.out.println("Total today: " + todayTxns.size());
        todayTxns.forEach(System.out::println);

        // Test 3: Record a sale
        System.out.println("\n3. Recording a sale transaction:");
        Transaction sale = new Transaction(1, 1, "Sale", 2, new BigDecimal("17000.00"));
        sale.setNotes("Test sale transaction");
        boolean recorded = handler.recordTransaction(sale);
        System.out.println(recorded ? "✓ Sale recorded: " + sale : "✗ Failed to record sale");

        // Test 4: Record a purchase
        System.out.println("\n4. Recording a purchase transaction:");
        Transaction purchase = new Transaction(2, 1, "Purchase", 50, new BigDecimal("400000.00"));
        purchase.setNotes("Test purchase transaction");
        recorded = handler.recordTransaction(purchase);
        System.out.println(recorded ? "✓ Purchase recorded: " + purchase : "✗ Failed to record purchase");

        // Test 5: Get transactions by type
        System.out.println("\n5. Getting all Sale transactions:");
        List<Transaction> sales = handler.getTransactionsByType("Sale");
        System.out.println("Total sales: " + sales.size());

        // Test 6: Get daily sales
        System.out.println("\n6. Today's sales total:");
        BigDecimal dailySales = handler.getDailySales(LocalDate.now());
        System.out.println("Total: " + dailySales + " UZS");

        // Test 7: Get monthly sales
        System.out.println("\n7. This month's sales:");
        LocalDate now = LocalDate.now();
        BigDecimal monthlySales = handler.getMonthlySales(now.getYear(), now.getMonthValue());
        System.out.println("Total: " + monthlySales + " UZS");

        // Test 8: Get transactions for specific product
        System.out.println("\n8. Transactions for product ID 1:");
        List<Transaction> productTxns = handler.getTransactionsByProduct(1);
        productTxns.forEach(System.out::println);

        // Close connection pool
        DatabaseConnection.closePool();
        System.out.println("\n=== Test completed ===");
    }
}