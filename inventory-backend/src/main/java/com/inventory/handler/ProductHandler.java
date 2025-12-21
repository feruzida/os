package com.inventory.handler;

import com.inventory.database.DatabaseConnection;
import com.inventory.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for Product-related database operations (CRUD)
 * Checks if a product has any related transactions.
 * Used to prevent deletion of products with history.
 */
public class ProductHandler {
    private static final Logger logger = LoggerFactory.getLogger(ProductHandler.class);

    public boolean hasTransactions(int productId) {
        String sql = "SELECT 1 FROM transactions WHERE product_id = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            logger.error("Error checking transactions for product {}", productId, e);
            return true;
        }
    }

    /**
     * Add a new product to the inventory
     * Added validation before insert
     * @return true if product added successfully
     */

    public boolean addProduct(Product product) {
    if (product == null) {
            logger.error("Product is null in addProduct()");
            return false;
        }
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            logger.error("Product name cannot be empty");
            return false;
        }
        if (product.getUnitPrice() == null || product.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
            logger.error("Invalid unit price for product '{}'", product.getName());
            return false;
        }
        if (product.getQuantity() < 0) {
            logger.error("Quantity cannot be negative for product '{}'", product.getName());
            return false;
        }
        if (product.getCategory() == null || product.getCategory().trim().isEmpty()) {
            logger.warn("Product '{}' has no category", product.getName());
            product.setCategory("Uncategorized");
        }

        String sql = "INSERT INTO products (name, category, unit_price, quantity, supplier_id) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, product.getName().trim());
            pstmt.setString(2, product.getCategory().trim());
            pstmt.setBigDecimal(3, product.getUnitPrice());
            pstmt.setInt(4, product.getQuantity());

            if (product.getSupplierId() != null) {
                pstmt.setInt(5, product.getSupplierId());
            } else {
                pstmt.setNull(5, Types.INTEGER);
            }

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                ResultSet generatedKeys = pstmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    product.setProductId(generatedKeys.getInt(1));
                }
                logger.info("Product '{}' added successfully with ID {}", product.getName(), product.getProductId());
                return true;
            }

            return false;

        } catch (SQLException e) {
            logger.error("Error adding product '{}'", product.getName(), e);
            return false;
        }
    }

    /**
     * Get product by ID
     * @return Product object or null if not found
     */

    public Product getProductById(int productId) {
        if (productId <= 0) {
            logger.error("Invalid product ID: {}", productId);
            return null;
        }

        String sql = "SELECT * FROM products WHERE product_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, productId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToProduct(rs);
            }

        } catch (SQLException e) {
            logger.error("Error fetching product with ID {}", productId, e);
        }

        return null;
    }

    /**
     * Get all products
     * @return List of all products
     */
    public List<Product> getAllProducts() {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products ORDER BY active DESC, product_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                products.add(mapResultSetToProduct(rs));
            }

        } catch (SQLException e) {
            logger.error("Error fetching all products", e);
        }

        return products;
    }

    /**
     * Get products by category
     * @return List of products in the category
     */

    public List<Product> getProductsByCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            logger.error("Category cannot be empty");
            return new ArrayList<>();
        }

        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE category = ? AND active = true ORDER BY product_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, category);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                products.add(mapResultSetToProduct(rs));
            }

            logger.info("Retrieved {} products in category '{}'", products.size(), category);

        } catch (SQLException e) {
            logger.error("Error fetching products by category '{}'", category, e);
        }

        return products;
    }

    /**
     * Get products by supplier
     * @return List of products from the supplier
     */

    public List<Product> getProductsBySupplier(int supplierId) {
        if (supplierId <= 0) {
            logger.error("Invalid supplier ID: {}", supplierId);
            return new ArrayList<>();
        }

        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE supplier_id = ? AND active = true ORDER BY product_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, supplierId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                products.add(mapResultSetToProduct(rs));
            }

            logger.info("Retrieved {} products from supplier ID {}", products.size(), supplierId);

        } catch (SQLException e) {
            logger.error("Error fetching products by supplier ID {}", supplierId, e);
        }

        return products;
    }

    /**
     * Get low stock products (quantity <= threshold)
     * @return List of products with low stock
     */

    public List<Product> getLowStockProducts(int threshold) {
        if (threshold < 0) {
            logger.error("Threshold cannot be negative");
            return new ArrayList<>();
        }

        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE quantity <= ? AND active = true ORDER BY quantity";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, threshold);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                products.add(mapResultSetToProduct(rs));
            }

            logger.info("Retrieved {} low stock products (threshold: {})", products.size(), threshold);

        } catch (SQLException e) {
            logger.error("Error fetching low stock products", e);
        }

        return products;
    }

    /**
     * Update product information
     * Added validation
     * @return true if update successful
     */
    public boolean updateProduct(Product product) {
     if (product == null) {
            logger.error("Product is null in updateProduct()");
            return false;
        }

        if (product.getProductId() <= 0) {
            logger.error("Invalid product ID");
            return false;
        }
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            logger.error("Product name cannot be empty");
            return false;
        }
        if (product.getUnitPrice() == null || product.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
            logger.error("Invalid unit price");
            return false;
        }
        if (product.getQuantity() < 0) {
            logger.error("Quantity cannot be negative");
            return false;
        }

        String sql = "UPDATE products SET name = ?, category = ?, unit_price = ?, quantity = ?, supplier_id = ? WHERE product_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, product.getName().trim());
            pstmt.setString(2, product.getCategory().trim());
            pstmt.setBigDecimal(3, product.getUnitPrice());
            pstmt.setInt(4, product.getQuantity());

            if (product.getSupplierId() != null) {
                pstmt.setInt(5, product.getSupplierId());
            } else {
                pstmt.setNull(5, Types.INTEGER);
            }

            pstmt.setInt(6, product.getProductId());

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Product '{}' (ID: {}) updated successfully", product.getName(), product.getProductId());
                return true;
            }

            return false;

        } catch (SQLException e) {
            logger.error("Error updating product '{}'", product.getName(), e);
            return false;
        }
    }

    /**
     * Update product quantity (for stock adjustments)
     * Added validation
     * @return true if update successful
     */

    public boolean updateProductQuantity(int productId, int newQuantity) {
        if (productId <= 0) {
            logger.error("Invalid product ID");
            return false;
        }
        if (newQuantity < 0) {
            logger.error("Quantity cannot be negative");
            return false;
        }

        String sql = "UPDATE products SET quantity = ? WHERE product_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, newQuantity);
            pstmt.setInt(2, productId);

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Product ID {} quantity updated to {}", productId, newQuantity);
                return true;
            }

            return false;

        } catch (SQLException e) {
            logger.error("Error updating product quantity for ID {}", productId, e);
            return false;
        }
    }

    /**
     * Delete product by ID soft.
     * @return true if deletion successful.
     */

    public boolean deactivateProduct(int productId) {
        if (productId <= 0) return false;

        String sql = "UPDATE products SET active = false WHERE product_id = ? AND active = true";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.error("Error deactivating product {}", productId, e);
            return false;
        }
    }

    /**
     * Search products by name (partial match)
     * @return List of matching products
     */

    public List<Product> searchProductsByName(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            logger.warn("Search term is empty, returning all products");
            return getAllProducts();
        }

        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE name ILIKE ? AND active = true ORDER BY product_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + searchTerm.trim() + "%");
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                products.add(mapResultSetToProduct(rs));
            }

            logger.info("Found {} products matching '{}'", products.size(), searchTerm);

        } catch (SQLException e) {
            logger.error("Error searching products by name '{}'", searchTerm, e);
        }

        return products;
    }

    /**
     * Map ResultSet to a Product object
     */

    private Product mapResultSetToProduct(ResultSet rs) throws SQLException {
        Integer supplierId = rs.getInt("supplier_id");
        if (rs.wasNull()) {
            supplierId = null;
        }

        Product product = new Product(
                rs.getInt("product_id"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getBigDecimal("unit_price"),
                rs.getInt("quantity"),
                supplierId,
                rs.getTimestamp("last_updated").toLocalDateTime()
        );

        product.setActive(rs.getBoolean("active"));

        return product;
    }
}