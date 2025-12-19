package com.inventory.handler;

import com.inventory.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/**
 * Handler for generating various reports
 * Sales Reports, Stock Reports, Transaction Reports, Supplier Reports, User Activity Reports
 * COMPLETE VERSION - Covers all proposal requirements
 */
public class ReportHandler {
    private static final Logger logger = LoggerFactory.getLogger(ReportHandler.class);

    // ========== SALES REPORTS ==========

    /**
     * Get sales summary for a date range
     */
    public SalesSummary getSalesSummary(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            logger.error("Date range cannot be null");
            return null;
        }

        String sql = "SELECT " +
                "COUNT(*) as total_transactions, " +
                "SUM(quantity) as total_items_sold, " +
                "SUM(total_price) as total_revenue " +
                "FROM transactions " +
                "WHERE txn_type = 'Sale' " +
                "AND DATE(txn_date) BETWEEN ? AND ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(startDate));
            pstmt.setDate(2, Date.valueOf(endDate));
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new SalesSummary(
                        rs.getInt("total_transactions"),
                        rs.getInt("total_items_sold"),
                        rs.getBigDecimal("total_revenue")
                );
            }

        } catch (SQLException e) {
            logger.error("Error fetching sales summary", e);
        }

        return null;
    }

    /**
     * Get today's sales summary
     */
    public SalesSummary getTodaySales() {
        LocalDate today = LocalDate.now();
        return getSalesSummary(today, today);
    }

    /**
     * Get this week's sales summary
     */
    public SalesSummary getWeeklySales() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
        return getSalesSummary(weekStart, today);
    }

    /**
     * Get this month's sales summary
     */
    public SalesSummary getMonthlySales() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        return getSalesSummary(monthStart, today);
    }

    /**
     * Get top selling products
     */
    public List<TopProduct> getTopSellingProducts(int limit, LocalDate startDate, LocalDate endDate) {
        List<TopProduct> topProducts = new ArrayList<>();

        String sql = "SELECT " +
                "p.product_id, " +
                "p.name, " +
                "p.category, " +
                "SUM(t.quantity) as total_sold, " +
                "SUM(t.total_price) as total_revenue " +
                "FROM transactions t " +
                "JOIN products p ON t.product_id = p.product_id " +
                "WHERE t.txn_type = 'Sale' " +
                "AND DATE(t.txn_date) BETWEEN ? AND ? " +
                "GROUP BY p.product_id, p.name, p.category " +
                "ORDER BY total_sold DESC " +
                "LIMIT ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(startDate));
            pstmt.setDate(2, Date.valueOf(endDate));
            pstmt.setInt(3, limit);

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                topProducts.add(new TopProduct(
                        rs.getInt("product_id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getInt("total_sold"),
                        rs.getBigDecimal("total_revenue")
                ));
            }

            logger.info("Retrieved {} top selling products", topProducts.size());

        } catch (SQLException e) {
            logger.error("Error fetching top selling products", e);
        }

        return topProducts;
    }

    /**
     * Get sales by category
     */
    public List<CategorySales> getSalesByCategory(LocalDate startDate, LocalDate endDate) {
        List<CategorySales> categorySales = new ArrayList<>();

        String sql = "SELECT " +
                "p.category, " +
                "COUNT(DISTINCT t.txn_id) as transaction_count, " +
                "SUM(t.quantity) as total_quantity, " +
                "SUM(t.total_price) as total_revenue " +
                "FROM transactions t " +
                "JOIN products p ON t.product_id = p.product_id " +
                "WHERE t.txn_type = 'Sale' " +
                "AND DATE(t.txn_date) BETWEEN ? AND ? " +
                "GROUP BY p.category " +
                "ORDER BY total_revenue DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(startDate));
            pstmt.setDate(2, Date.valueOf(endDate));
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                categorySales.add(new CategorySales(
                        rs.getString("category"),
                        rs.getInt("transaction_count"),
                        rs.getInt("total_quantity"),
                        rs.getBigDecimal("total_revenue")
                ));
            }

            logger.info("Retrieved sales for {} categories", categorySales.size());

        } catch (SQLException e) {
            logger.error("Error fetching sales by category", e);
        }

        return categorySales;
    }

    // ========== STOCK REPORTS ==========

    /**
     * Get current inventory status
     */
    public InventoryStatus getInventoryStatus() {
        String sql = "SELECT " +
                "COUNT(*) as total_products, " +
                "SUM(quantity) as total_items, " +
                "SUM(unit_price * quantity) as total_value, " +
                "COUNT(CASE WHEN quantity <= 10 THEN 1 END) as low_stock_count, " +
                "COUNT(CASE WHEN quantity = 0 THEN 1 END) as out_of_stock_count " +
                "FROM products " +
                "WHERE active = true";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return new InventoryStatus(
                        rs.getInt("total_products"),
                        rs.getInt("total_items"),
                        rs.getBigDecimal("total_value"),
                        rs.getInt("low_stock_count"),
                        rs.getInt("out_of_stock_count")
                );
            }

        } catch (SQLException e) {
            logger.error("Error fetching inventory status", e);
        }

        return null;
    }

    /**
     * Get products by stock level
     */
    public Map<String, List<StockProduct>> getProductsByStockLevel() {
        Map<String, List<StockProduct>> stockLevels = new LinkedHashMap<>();
        stockLevels.put("out_of_stock", new ArrayList<>());
        stockLevels.put("low_stock", new ArrayList<>());
        stockLevels.put("normal_stock", new ArrayList<>());

        String sql = "SELECT product_id, name, category, quantity, unit_price " +
                "FROM products " +
                "WHERE active = true " +
                "ORDER BY quantity ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                StockProduct product = new StockProduct(
                        rs.getInt("product_id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("unit_price")
                );

                if (product.getQuantity() == 0) {
                    stockLevels.get("out_of_stock").add(product);
                } else if (product.getQuantity() <= 10) {
                    stockLevels.get("low_stock").add(product);
                } else {
                    stockLevels.get("normal_stock").add(product);
                }
            }

        } catch (SQLException e) {
            logger.error("Error fetching products by stock level", e);
        }

        return stockLevels;
    }

    // ========== TRANSACTION REPORTS ==========

    /**
     * Get transaction statistics
     */
    public TransactionStats getTransactionStats(LocalDate startDate, LocalDate endDate) {

        String sql = "SELECT " +
                "txn_type, " +
                "COUNT(*) as count, " +
                "SUM(quantity) as total_quantity, " +
                "SUM(total_price) as total_amount " +
                "FROM transactions " +
                "WHERE DATE(txn_date) BETWEEN ? AND ? " +
                "GROUP BY txn_type";

        Map<String, TransactionTypeStats> stats = new HashMap<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(startDate));
            pstmt.setDate(2, Date.valueOf(endDate));

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String type = rs.getString("txn_type");

                // ✅ НОРМАЛИЗАЦИЯ
                if ("sale".equalsIgnoreCase(type)) type = "Sale";
                else if ("purchase".equalsIgnoreCase(type)) type = "Purchase";

                stats.put(type, new TransactionTypeStats(
                        type,
                        rs.getInt("count"),
                        rs.getInt("total_quantity"),
                        rs.getBigDecimal("total_amount")
                ));
            }

        } catch (SQLException e) {
            logger.error("Error fetching transaction stats", e);
        }

        return new TransactionStats(stats);
    }


    // ========== SUPPLIER REPORTS ========== ✅ NEW

    /**
     * Get supplier performance report
     */
    public List<SupplierPerformance> getSupplierPerformance(LocalDate startDate, LocalDate endDate) {
        List<SupplierPerformance> result = new ArrayList<>();

        String sql = "SELECT " +
                "s.supplier_id, " +
                "s.name, " +
                "COUNT(DISTINCT p.product_id) as product_count, " +
                "COALESCE(SUM(t.total_price), 0) as total_revenue, " +
                "COALESCE(SUM(t.quantity), 0) as total_items_sold " +
                "FROM suppliers s " +
                "LEFT JOIN products p ON s.supplier_id = p.supplier_id AND p.active = true " +
                "LEFT JOIN transactions t ON p.product_id = t.product_id " +
                "   AND t.txn_type = 'Sale' " +
                "   AND DATE(t.txn_date) BETWEEN ? AND ? " +
                "WHERE s.active = true " +
                "GROUP BY s.supplier_id, s.name " +
                "ORDER BY total_revenue DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(startDate));
            pstmt.setDate(2, Date.valueOf(endDate));
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                result.add(new SupplierPerformance(
                        rs.getInt("supplier_id"),
                        rs.getString("name"),
                        rs.getInt("product_count"),
                        rs.getInt("total_items_sold"),
                        rs.getBigDecimal("total_revenue")
                ));
            }

            logger.info("Retrieved supplier performance for {} suppliers", result.size());

        } catch (SQLException e) {
            logger.error("Error fetching supplier performance", e);
        }

        return result;
    }

    /**
     * Get all suppliers with product counts (simple version)
     */
    public List<SupplierSummary> getAllSuppliersSummary() {
        List<SupplierSummary> result = new ArrayList<>();

        String sql = "SELECT " +
                "s.supplier_id, " +
                "s.name, " +
                "s.contact_info, " +
                "COUNT(p.product_id) as product_count " +
                "FROM suppliers s " +
                "LEFT JOIN products p ON s.supplier_id = p.supplier_id AND p.active = true " +
                "WHERE s.active = true " +
                "GROUP BY s.supplier_id, s.name, s.contact_info " +
                "ORDER BY product_count DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                result.add(new SupplierSummary(
                        rs.getInt("supplier_id"),
                        rs.getString("name"),
                        rs.getString("contact_info"),
                        rs.getInt("product_count")
                ));
            }

            logger.info("Retrieved {} supplier summaries", result.size());

        } catch (SQLException e) {
            logger.error("Error fetching supplier summaries", e);
        }

        return result;
    }

    // ========== USER ACTIVITY REPORTS ========== ✅ NEW

    /**
     * Get user activity summary from audit logs
     */
    public List<UserActivity> getUserActivityReport(LocalDate startDate, LocalDate endDate) {
        List<UserActivity> result = new ArrayList<>();

        String sql = "SELECT " +
                "u.user_id, " +
                "u.username, " +
                "u.role, " +
                "COUNT(al.log_id) as action_count, " +
                "MAX(al.timestamp) as last_action " +
                "FROM users u " +
                "LEFT JOIN audit_log al ON u.user_id = al.user_id " +
                "   AND DATE(al.timestamp) BETWEEN ? AND ? " +
                "GROUP BY u.user_id, u.username, u.role " +
                "ORDER BY action_count DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(startDate));
            pstmt.setDate(2, Date.valueOf(endDate));
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Timestamp lastActionTs = rs.getTimestamp("last_action");
                result.add(new UserActivity(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("role"),
                        rs.getInt("action_count"),
                        lastActionTs != null ? lastActionTs.toLocalDateTime() : null
                ));
            }

            logger.info("Retrieved user activity for {} users", result.size());

        } catch (SQLException e) {
            logger.error("Error fetching user activity report", e);
        }

        return result;
    }

    /**
     * Get most active users
     */
    public List<UserActivity> getMostActiveUsers(int limit, LocalDate startDate, LocalDate endDate) {
        List<UserActivity> allActivity = getUserActivityReport(startDate, endDate);

        // Sort by action count and limit
        allActivity.sort((a, b) -> Integer.compare(b.getActionCount(), a.getActionCount()));

        return allActivity.size() > limit
                ? allActivity.subList(0, limit)
                : allActivity;
    }

    // ========== INNER CLASSES FOR REPORT DATA ==========

    public static class SalesSummary {
        private int totalTransactions;
        private int totalItemsSold;
        private BigDecimal totalRevenue;

        public SalesSummary(int totalTransactions, int totalItemsSold, BigDecimal totalRevenue) {
            this.totalTransactions = totalTransactions;
            this.totalItemsSold = totalItemsSold;
            this.totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
        }

        public int getTotalTransactions() { return totalTransactions; }
        public int getTotalItemsSold() { return totalItemsSold; }
        public BigDecimal getTotalRevenue() { return totalRevenue; }
    }

    public static class TopProduct {
        private int productId;
        private String name;
        private String category;
        private int totalSold;
        private BigDecimal totalRevenue;

        public TopProduct(int productId, String name, String category, int totalSold, BigDecimal totalRevenue) {
            this.productId = productId;
            this.name = name;
            this.category = category;
            this.totalSold = totalSold;
            this.totalRevenue = totalRevenue;
        }

        public int getProductId() { return productId; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public int getTotalSold() { return totalSold; }
        public BigDecimal getTotalRevenue() { return totalRevenue; }
    }

    public static class CategorySales {
        private String category;
        private int transactionCount;
        private int totalQuantity;
        private BigDecimal totalRevenue;

        public CategorySales(String category, int transactionCount, int totalQuantity, BigDecimal totalRevenue) {
            this.category = category;
            this.transactionCount = transactionCount;
            this.totalQuantity = totalQuantity;
            this.totalRevenue = totalRevenue;
        }

        public String getCategory() { return category; }
        public int getTransactionCount() { return transactionCount; }
        public int getTotalQuantity() { return totalQuantity; }
        public BigDecimal getTotalRevenue() { return totalRevenue; }
    }

    public static class InventoryStatus {
        private int totalProducts;
        private int totalItems;
        private BigDecimal totalValue;
        private int lowStockCount;
        private int outOfStockCount;

        public InventoryStatus(int totalProducts, int totalItems, BigDecimal totalValue,
                               int lowStockCount, int outOfStockCount) {
            this.totalProducts = totalProducts;
            this.totalItems = totalItems;
            this.totalValue = totalValue != null ? totalValue : BigDecimal.ZERO;
            this.lowStockCount = lowStockCount;
            this.outOfStockCount = outOfStockCount;
        }

        public int getTotalProducts() { return totalProducts; }
        public int getTotalItems() { return totalItems; }
        public BigDecimal getTotalValue() { return totalValue; }
        public int getLowStockCount() { return lowStockCount; }
        public int getOutOfStockCount() { return outOfStockCount; }
    }

    public static class StockProduct {
        private int productId;
        private String name;
        private String category;
        private int quantity;
        private BigDecimal unitPrice;

        public StockProduct(int productId, String name, String category, int quantity, BigDecimal unitPrice) {
            this.productId = productId;
            this.name = name;
            this.category = category;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public int getProductId() { return productId; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public int getQuantity() { return quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public BigDecimal getTotalValue() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public static class TransactionTypeStats {
        private String type;
        private int count;
        private int totalQuantity;
        private BigDecimal totalAmount;

        public TransactionTypeStats(String type, int count, int totalQuantity, BigDecimal totalAmount) {
            this.type = type;
            this.count = count;
            this.totalQuantity = totalQuantity;
            this.totalAmount = totalAmount;
        }

        public String getType() { return type; }
        public int getCount() { return count; }
        public int getTotalQuantity() { return totalQuantity; }
        public BigDecimal getTotalAmount() { return totalAmount; }
    }

    public static class TransactionStats {
        private Map<String, TransactionTypeStats> stats;

        public TransactionStats(Map<String, TransactionTypeStats> stats) {
            this.stats = stats;
        }

        public Map<String, TransactionTypeStats> getStats() { return stats; }

        public TransactionTypeStats getSales() {
            return stats.getOrDefault("Sale",
                    new TransactionTypeStats("Sale", 0, 0, BigDecimal.ZERO));
        }

        public TransactionTypeStats getPurchases() {
            return stats.getOrDefault("Purchase",
                    new TransactionTypeStats("Purchase", 0, 0, BigDecimal.ZERO));
        }
    }

    // ========== NEW CLASSES FOR SUPPLIER & USER REPORTS ========== ✅

    public static class SupplierPerformance {
        private int supplierId;
        private String name;
        private int productCount;
        private int totalItemsSold;
        private BigDecimal totalRevenue;

        public SupplierPerformance(int supplierId, String name, int productCount,
                                   int totalItemsSold, BigDecimal totalRevenue) {
            this.supplierId = supplierId;
            this.name = name;
            this.productCount = productCount;
            this.totalItemsSold = totalItemsSold;
            this.totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
        }

        public int getSupplierId() { return supplierId; }
        public String getName() { return name; }
        public int getProductCount() { return productCount; }
        public int getTotalItemsSold() { return totalItemsSold; }
        public BigDecimal getTotalRevenue() { return totalRevenue; }
    }

    public static class SupplierSummary {
        private int supplierId;
        private String name;
        private String contactInfo;
        private int productCount;

        public SupplierSummary(int supplierId, String name, String contactInfo, int productCount) {
            this.supplierId = supplierId;
            this.name = name;
            this.contactInfo = contactInfo;
            this.productCount = productCount;
        }

        public int getSupplierId() { return supplierId; }
        public String getName() { return name; }
        public String getContactInfo() { return contactInfo; }
        public int getProductCount() { return productCount; }
    }

    public static class UserActivity {
        private int userId;
        private String username;
        private String role;
        private int actionCount;
        private java.time.LocalDateTime lastAction;

        public UserActivity(int userId, String username, String role,
                            int actionCount, java.time.LocalDateTime lastAction) {
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.actionCount = actionCount;
            this.lastAction = lastAction;
        }

        public int getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
        public int getActionCount() { return actionCount; }
        public java.time.LocalDateTime getLastAction() { return lastAction; }
    }
}