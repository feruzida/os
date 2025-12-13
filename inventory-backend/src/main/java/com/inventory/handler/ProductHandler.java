package com.inventory.handler;

import com.inventory.database.DatabaseConnection;
import com.inventory.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for Product-related database operations (CRUD)
 */
public class ProductHandler {
    private static final Logger logger = LoggerFactory.getLogger(ProductHandler.class);

    /**
     * Add a new product to inventory
     * @return true if product added successfully
     */
    public boolean addProduct(Product product) {
        String sql = "INSERT INTO products (name, category, unit_price, quantity, supplier_id) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, product.getName());
            pstmt.setString(2, product.getCategory());
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
        String sql = "SELECT * FROM products ORDER BY category, name";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                products.add(mapResultSetToProduct(rs));
            }

            logger.info("Retrieved {} products from database", products.size());

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
        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE category = ? ORDER BY name";

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
        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE supplier_id = ? ORDER BY name";

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
        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE quantity <= ? ORDER BY quantity";

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
     * @return true if update successful
     */
    public boolean updateProduct(Product product) {
        String sql = "UPDATE products SET name = ?, category = ?, unit_price = ?, quantity = ?, supplier_id = ? WHERE product_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, product.getName());
            pstmt.setString(2, product.getCategory());
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
     * @return true if update successful
     */
    public boolean updateProductQuantity(int productId, int newQuantity) {
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
     * Delete product by ID
     * @return true if deletion successful
     */
    public boolean deleteProduct(int productId) {
        String sql = "DELETE FROM products WHERE product_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, productId);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Product with ID {} deleted successfully", productId);
                return true;
            }

            return false;

        } catch (SQLException e) {
            logger.error("Error deleting product with ID {}", productId, e);
            return false;
        }
    }

    /**
     * Search products by name (partial match)
     * @return List of matching products
     */
    public List<Product> searchProductsByName(String searchTerm) {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE name ILIKE ? ORDER BY name";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + searchTerm + "%");
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
     * Map ResultSet to Product object
     */
    private Product mapResultSetToProduct(ResultSet rs) throws SQLException {
        Integer supplierId = rs.getInt("supplier_id");
        if (rs.wasNull()) {
            supplierId = null;
        }

        return new Product(
                rs.getInt("product_id"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getBigDecimal("unit_price"),
                rs.getInt("quantity"),
                supplierId,
                rs.getTimestamp("last_updated").toLocalDateTime()
        );
    }

    // ========== TESTING METHOD ==========
    public static void main(String[] args) {
        ProductHandler handler = new ProductHandler();

        System.out.println("=== Testing ProductHandler ===\n");

        // Test 1: Get all products
        System.out.println("1. Getting all products:");
        List<Product> products = handler.getAllProducts();
        products.forEach(System.out::println);

        // Test 2: Get product by ID
        System.out.println("\n2. Getting product by ID 1:");
        Product product = handler.getProductById(1);
        System.out.println(product);

        // Test 3: Add new product
        System.out.println("\n3. Adding new product:");
        Product newProduct = new Product("Test Product", "Test Category",
                new java.math.BigDecimal("15000.00"), 100);
        newProduct.setSupplierId(1);
        boolean added = handler.addProduct(newProduct);
        System.out.println(added ? "✓ Product added: " + newProduct : "✗ Failed to add product");

        // Test 4: Search products
        System.out.println("\n4. Searching for 'Coca':");
        List<Product> searchResults = handler.searchProductsByName("Coca");
        searchResults.forEach(System.out::println);

        // Test 5: Get low stock products
        System.out.println("\n5. Getting low stock products (threshold: 60):");
        List<Product> lowStock = handler.getLowStockProducts(60);
        lowStock.forEach(p -> System.out.println(p.getName() + " - Quantity: " + p.getQuantity()));

        // Test 6: Update product
        if (newProduct.getProductId() > 0) {
            System.out.println("\n6. Updating product quantity:");
            boolean updated = handler.updateProductQuantity(newProduct.getProductId(), 150);
            System.out.println(updated ? "✓ Quantity updated to 150" : "✗ Update failed");

            // Test 7: Delete product
            System.out.println("\n7. Deleting test product:");
            boolean deleted = handler.deleteProduct(newProduct.getProductId());
            System.out.println(deleted ? "✓ Product deleted" : "✗ Deletion failed");
        }

        // Test 8: Get products by category
        System.out.println("\n8. Getting products in 'Beverages' category:");
        List<Product> beverages = handler.getProductsByCategory("Beverages");
        beverages.forEach(System.out::println);

        // Close connection pool
        DatabaseConnection.closePool();
        System.out.println("\n=== Test completed ===");
    }
}