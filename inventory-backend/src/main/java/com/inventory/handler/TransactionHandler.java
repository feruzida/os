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
 * FIXED: Improved resource management and validation
 */
public class TransactionHandler {
    private static final Logger logger = LoggerFactory.getLogger(TransactionHandler.class);

    /**
     * Record a new transaction (Sale or Purchase)
     * This method also updates product quantity automatically
     * FIXED: Better resource management with try-with-resources
     * @return true if transaction recorded successfully
     */
    public boolean recordTransaction(Transaction transaction) {
        if (transaction == null) {
            logger.error("Transaction is null");
            return false;
        }

        if (transaction.getProductId() <= 0 ||
            transaction.getUserId() <= 0 ||
            transaction.getQuantity() <= 0 ||
            (!transaction.isSale() && !transaction.isPurchase())) {
            logger.error("Invalid transaction data");
            return false;
        }

        String updateSql = transaction.isSale()
            ? "UPDATE products SET quantity = quantity - ? WHERE product_id = ? AND quantity >= ?"
            : "UPDATE products SET quantity = quantity + ? WHERE product_id = ?";

        String insertSql =
            "INSERT INTO transactions (product_id, user_id, txn_type, quantity, total_price, notes) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try (
                PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                PreparedStatement insertStmt = conn.prepareStatement(insertSql)
            ) {
                // 1️⃣ Update stock first
                updateStmt.setInt(1, transaction.getQuantity());
                updateStmt.setInt(2, transaction.getProductId());
                if (transaction.isSale()) {
                    updateStmt.setInt(3, transaction.getQuantity());
                }

                int updated = updateStmt.executeUpdate();
                if (updated == 0 && transaction.isSale()) {
                    conn.rollback();
                    logger.error("Insufficient stock for product ID {}", transaction.getProductId());
                    return false;
                }

                // 2️⃣ Calculate total price on backend
                BigDecimal unitPrice = getProductPrice(conn, transaction.getProductId());
                BigDecimal totalPrice = unitPrice.multiply(
                    BigDecimal.valueOf(transaction.getQuantity())
                );

                // 3️⃣ Insert transaction
                insertStmt.setInt(1, transaction.getProductId());
                insertStmt.setInt(2, transaction.getUserId());
                insertStmt.setString(3, transaction.getTxnType());
                insertStmt.setInt(4, transaction.getQuantity());
                insertStmt.setBigDecimal(5, totalPrice);
                insertStmt.setString(6, transaction.getNotes());

                insertStmt.executeUpdate();

                conn.commit();
                logger.info("{} transaction completed for product ID {}",
                    transaction.getTxnType(), transaction.getProductId());
                return true;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            logger.error("Error recording transaction", e);
            return false;
        }
    }


    /**
     * Get transaction by ID
     * @return Transaction object or null if not found
     */
    public Transaction getTransactionById(int txnId) {
        if (txnId <= 0) {
            logger.error("Invalid transaction ID");
            return null;
        }

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
        String sql = "SELECT * FROM transactions ORDER BY txn_date DESC LIMIT 1000";

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
        if (!"Sale".equalsIgnoreCase(txnType) && !"Purchase".equalsIgnoreCase(txnType)) {
            logger.error("Invalid transaction type: {}", txnType);
            return new ArrayList<>();
        }

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
        if (startDate == null || endDate == null) {
            logger.error("Date range cannot be null");
            return new ArrayList<>();
        }
        if (startDate.isAfter(endDate)) {
            logger.error("Start date must be before end date");
            return new ArrayList<>();
        }

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
        if (productId <= 0) {
            logger.error("Invalid product ID");
            return new ArrayList<>();
        }

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
        if (date == null) {
            logger.error("Date cannot be null");
            return BigDecimal.ZERO;
        }

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
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            logger.error("Invalid year or month");
            return BigDecimal.ZERO;
        }

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
    private BigDecimal getProductPrice(Connection conn, int productId) throws SQLException {
        String sql = "SELECT unit_price FROM products WHERE product_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getBigDecimal("unit_price");
            } else {
                throw new SQLException("Product not found for ID " + productId);
            }
        }
    }

}