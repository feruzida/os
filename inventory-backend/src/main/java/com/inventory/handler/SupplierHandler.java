package com.inventory.handler;

import com.inventory.database.DatabaseConnection;
import com.inventory.model.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for Supplier-related database operations (CRUD)
 */
public class SupplierHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(SupplierHandler.class);

    /**
     * Add a new supplier
     * @return true if supplier added successfully
     */
    public boolean addSupplier(Supplier supplier) {
        String sql =
                "INSERT INTO suppliers (name, contact_info, email, address) " +
                        "VALUES (?, ?, ?, ?)";

        try (
                Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt =
                        conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ) {
            pstmt.setString(1, supplier.getName());
            pstmt.setString(2, supplier.getContactInfo());
            pstmt.setString(3, supplier.getEmail());
            pstmt.setString(4, supplier.getAddress());

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                ResultSet generatedKeys = pstmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    supplier.setSupplierId(generatedKeys.getInt(1));
                }
                logger.info(
                        "Supplier '{}' added successfully with ID {}",
                        supplier.getName(),
                        supplier.getSupplierId()
                );
                return true;
            }
            return false;

        } catch (SQLException e) {
            logger.error("Error adding supplier '{}'", supplier.getName(), e);
            return false;
        }
    }

    /**
     * Get supplier by ID
     * @return Supplier object or null if not found
     */
    public Supplier getSupplierById(int supplierId) {
        String sql = "SELECT * FROM suppliers WHERE supplier_id = ?";

        try (
                Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setInt(1, supplierId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToSupplier(rs);
            }
        } catch (SQLException e) {
            logger.error(
                    "Error fetching supplier with ID {}",
                    supplierId,
                    e
            );
        }
        return null;
    }

    /**
     * Get all suppliers
     * @return List of all suppliers
     */
    public List<Supplier> getAllSuppliers() {
        List<Supplier> suppliers = new ArrayList<>();

        String sql = """
        SELECT
            s.supplier_id,
            s.name,
            s.contact_info,
            s.email,
            s.address,
            s.created_at,
            s.active,
            COUNT(p.product_id) AS product_count
        FROM suppliers s
        LEFT JOIN products p
            ON p.supplier_id = s.supplier_id
            AND p.active = true
        GROUP BY s.supplier_id
        ORDER BY s.name
        """;

        try (
                Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)
        ) {
            while (rs.next()) {
                suppliers.add(mapResultSetToSupplier(rs));
            }
            logger.info("Retrieved {} suppliers with product count", suppliers.size());
        } catch (SQLException e) {
            logger.error("Error fetching suppliers", e);
        }

        return suppliers;
    }


    /**
     * Update supplier information
     * @return true if update successful
     */
    public boolean updateSupplier(Supplier supplier) {
        String sql =
                "UPDATE suppliers SET name = ?, contact_info = ?, " +
                        "email = ?, address = ? WHERE supplier_id = ?";

        try (
                Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, supplier.getName());
            pstmt.setString(2, supplier.getContactInfo());
            pstmt.setString(3, supplier.getEmail());
            pstmt.setString(4, supplier.getAddress());
            pstmt.setInt(5, supplier.getSupplierId());

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info(
                        "Supplier '{}' (ID: {}) updated successfully",
                        supplier.getName(),
                        supplier.getSupplierId()
                );
                return true;
            }
            return false;

        } catch (SQLException e) {
            logger.error("Error updating supplier '{}'", supplier.getName(), e);
            return false;
        }
    }

    /**
     * Soft delete supplier (active = false)
     */
    public boolean deactivateSupplier(int supplierId) {
        String sql = "UPDATE suppliers SET active = false WHERE supplier_id = ? AND active = true";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, supplierId);
            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.error("Error deactivating supplier {}", supplierId, e);
            return false;
        }
    }


    /**
     * Delete supplier by ID
     * @return true if deletion successful
     */
    public boolean deleteSupplier(int supplierId) {
        String sql = "DELETE FROM suppliers WHERE supplier_id = ?";

        try (
                Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setInt(1, supplierId);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info(
                        "Supplier with ID {} deleted successfully",
                        supplierId
                );
                return true;
            }
            return false;

        } catch (SQLException e) {
            logger.error(
                    "Error deleting supplier with ID {}",
                    supplierId,
                    e
            );
            return false;
        }
    }

    /**
     * Search suppliers by name (partial match)
     * @return List of matching suppliers
     */
    public List<Supplier> searchSuppliersByName(String searchTerm) {
        List<Supplier> suppliers = new ArrayList<>();
        String sql =
                "SELECT * FROM suppliers " +
                        "WHERE name ILIKE ? ORDER BY name";

        try (
                Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, "%" + searchTerm + "%");
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                suppliers.add(mapResultSetToSupplier(rs));
            }
            logger.info(
                    "Found {} suppliers matching '{}'",
                    suppliers.size(),
                    searchTerm
            );
        } catch (SQLException e) {
            logger.error(
                    "Error searching suppliers by name '{}'",
                    searchTerm,
                    e
            );
        }
        return suppliers;
    }

    /**
     * Get supplier with product count
     */
    public List<String> getSuppliersWithProductCount() {
        List<String> results = new ArrayList<>();
        String sql =
                "SELECT s.supplier_id, s.name, " +
                        "COUNT(p.product_id) AS product_count " +
                        "FROM suppliers s " +
                        "LEFT JOIN products p ON s.supplier_id = p.supplier_id " +
                        "GROUP BY s.supplier_id, s.name " +
                        "ORDER BY product_count DESC, s.name";

        try (
                Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)
        ) {
            while (rs.next()) {
                String result = String.format(
                        "Supplier: %s (ID: %d) - Products: %d",
                        rs.getString("name"),
                        rs.getInt("supplier_id"),
                        rs.getInt("product_count")
                );
                results.add(result);
            }
            logger.info(
                    "Retrieved supplier statistics for {} suppliers",
                    results.size()
            );
        } catch (SQLException e) {
            logger.error(
                    "Error fetching suppliers with product count",
                    e
            );
        }
        return results;
    }

    /**
     * Map ResultSet to Supplier object
     */
    private Supplier mapResultSetToSupplier(ResultSet rs)
            throws SQLException {

        Supplier supplier = new Supplier(
                rs.getInt("supplier_id"),
                rs.getString("name"),
                rs.getString("contact_info"),
                rs.getString("email"),
                rs.getString("address"),
                rs.getTimestamp("created_at")
                        .toLocalDateTime()
        );
        supplier.setActive(rs.getBoolean("active"));
        try {
            supplier.setProductCount(rs.getInt("product_count"));
        } catch (SQLException ignored) {
            supplier.setProductCount(0);
        }
        return supplier;
    }

    // ========== TESTING METHOD ==========
    public static void main(String[] args) {

        SupplierHandler handler = new SupplierHandler();

        System.out.println("=== Testing SupplierHandler ===\n");

        // 1. Get all suppliers
        System.out.println("1. Getting all suppliers:");
        List<Supplier> suppliers = handler.getAllSuppliers();
        suppliers.forEach(System.out::println);

        // 2. Get supplier by ID
        System.out.println("\n2. Getting supplier by ID 1:");
        Supplier supplier = handler.getSupplierById(1);
        System.out.println(supplier);

        // 3. Add new supplier
        System.out.println("\n3. Adding new supplier:");
        Supplier newSupplier = new Supplier(
                "Test Supplier Ltd",
                "+998 90 999 8877",
                "test@supplier.com",
                "Tashkent, Test Street 123"
        );
        boolean added = handler.addSupplier(newSupplier);
        System.out.println(
                added
                        ? "✓ Supplier added: " + newSupplier
                        : "✗ Failed to add supplier"
        );

        // 4. Search suppliers
        System.out.println("\n4. Searching for 'Coca':");
        List<Supplier> searchResults =
                handler.searchSuppliersByName("Coca");
        searchResults.forEach(System.out::println);

        // 5. Update supplier
        if (newSupplier.getSupplierId() > 0) {
            System.out.println("\n5. Updating supplier:");
            newSupplier.setContactInfo("+998 71 888 7766");
            boolean updated = handler.updateSupplier(newSupplier);
            System.out.println(
                    updated ? "✓ Supplier updated" : "✗ Update failed"
            );

            Supplier updatedSupplier =
                    handler.getSupplierById(
                            newSupplier.getSupplierId()
                    );
            System.out.println("Updated: " + updatedSupplier);

            // 6. Delete supplier
            System.out.println("\n6. Deleting test supplier:");
            boolean deleted =
                    handler.deleteSupplier(
                            newSupplier.getSupplierId()
                    );
            System.out.println(
                    deleted
                            ? "✓ Supplier deleted"
                            : "✗ Deletion failed"
            );
        }

        // 7. Supplier stats
        System.out.println("\n7. Suppliers with product counts:");
        List<String> stats =
                handler.getSuppliersWithProductCount();
        stats.forEach(System.out::println);

        DatabaseConnection.closePool();
        System.out.println("\n=== Test completed ===");
    }
}
