# Function Analysis: @inventory-backend and @db

## Overview
This document provides a comprehensive analysis of all functions in the `@inventory-backend` and `@db` folders of the inventory management system. The system is a Java-based backend using socket communication for a multi-user inventory management application.

---

## Table of Contents
1. [Database Schema (@db folder)](#database-schema-db-folder)
2. [Core Classes](#core-classes)
3. [Database Connection](#database-connection)
4. [Handler Classes](#handler-classes)
5. [Server Components](#server-components)
6. [Utility Classes](#utility-classes)

---

## Database Schema (@db folder)

### File: `database_schema.sql`

This SQL file defines the complete database structure for the inventory management system.

#### Database Tables

##### 1. **users** Table
Stores user authentication and role information.
- **Columns:**
  - `user_id` (SERIAL PRIMARY KEY): Unique identifier for each user
  - `username` (VARCHAR(50)): Unique username, minimum 3 characters
  - `password` (VARCHAR(255)): User password, minimum 6 characters (stored in plain text for educational purposes)
  - `role` (VARCHAR(20)): User role - 'Admin', 'Stock Manager', or 'Cashier'
  - `created_at` (TIMESTAMP): Account creation timestamp

##### 2. **suppliers** Table
Manages supplier information with soft delete capability.
- **Columns:**
  - `supplier_id` (SERIAL PRIMARY KEY): Unique identifier for each supplier
  - `name` (VARCHAR(100)): Supplier name
  - `contact_info` (VARCHAR(255)): Contact information
  - `email` (VARCHAR(100)): Email address
  - `address` (TEXT): Physical address
  - `active` (BOOLEAN): Soft delete flag (default: true)
  - `created_at` (TIMESTAMP): Record creation timestamp

##### 3. **products** Table
Stores inventory products with soft delete and automatic timestamp updates.
- **Columns:**
  - `product_id` (SERIAL PRIMARY KEY): Unique identifier for each product
  - `name` (VARCHAR(100)): Product name
  - `category` (VARCHAR(50)): Product category
  - `unit_price` (NUMERIC(12,2)): Price per unit (must be >= 0)
  - `quantity` (INT): Current stock quantity (must be >= 0)
  - `supplier_id` (INT): Foreign key to suppliers table
  - `active` (BOOLEAN): Soft delete flag (default: true)
  - `last_updated` (TIMESTAMP): Auto-updated timestamp

##### 4. **transactions** Table
Records all sales and purchase transactions.
- **Columns:**
  - `txn_id` (SERIAL PRIMARY KEY): Unique transaction identifier
  - `product_id` (INT): Foreign key to products table
  - `user_id` (INT): Foreign key to users table
  - `txn_type` (VARCHAR(20)): 'Sale' or 'Purchase'
  - `quantity` (INT): Number of items in transaction (must be > 0)
  - `total_price` (NUMERIC(12,2)): Total transaction amount
  - `txn_date` (TIMESTAMP): Transaction timestamp
  - `notes` (TEXT): Additional transaction notes

##### 5. **audit_log** Table
Tracks all user actions in the system for audit purposes.
- **Columns:**
  - `log_id` (SERIAL PRIMARY KEY): Unique log entry identifier
  - `user_id` (INT): Foreign key to users table (SET NULL on delete)
  - `action` (VARCHAR(100)): Action description
  - `details` (TEXT): Additional action details
  - `timestamp` (TIMESTAMP): Action timestamp

#### Database Functions

##### 1. **update_last_updated()** Trigger Function
- **Purpose:** Automatically updates the `last_updated` timestamp on products table
- **Trigger:** Executes BEFORE UPDATE on products table
- **Returns:** Modified row with updated timestamp
- **Implementation:** PL/pgSQL function that sets `NEW.last_updated = CURRENT_TIMESTAMP`

#### Database Views

##### 1. **v_inventory_overview** View
- **Purpose:** Provides a comprehensive overview of active inventory
- **Columns:**
  - `product_id`: Product identifier
  - `product_name`: Product name
  - `category`: Product category
  - `unit_price`: Price per unit
  - `quantity`: Current stock
  - `total_value`: Calculated as `unit_price * quantity`
  - `supplier_name`: Supplier name or 'No Supplier'
  - `active`: Product status
- **Filters:** Only shows active products with active suppliers

##### 2. **v_low_stock_products** View
- **Purpose:** Identifies products with low stock levels
- **Columns:**
  - `product_id`: Product identifier
  - `product_name`: Product name
  - `category`: Product category
  - `quantity`: Current stock level
  - `unit_price`: Price per unit
- **Threshold:** Shows products with quantity <= 50

---

## Core Classes

### File: `Main.java`

Main entry point for the inventory backend application.

#### Functions:

##### 1. **main(String[] args)**
- **Purpose:** Application startup and initialization
- **Operations:**
  - Displays application banner
  - Tests database connection
  - Registers shutdown hook for graceful termination
  - Shows connection pool statistics
- **Return:** void

##### 2. **testDatabaseConnection()**
- **Purpose:** Validates database connectivity and checks table integrity
- **Operations:**
  - Connects to database
  - Counts users in the system
  - Checks all tables (users, suppliers, products, transactions, audit_log)
  - Logs record counts for each table
- **Error Handling:** Exits application if database connection fails
- **Return:** void

---

## Database Connection

### File: `DatabaseConnection.java`

Manages database connections using HikariCP connection pooling for optimal performance.

#### Functions:

##### 1. **initializeDataSource()**
- **Purpose:** Initializes HikariCP connection pool with configuration
- **Operations:**
  - Loads database properties from `database.properties`
  - Configures connection pool settings (max pool size, idle timeout, etc.)
  - Sets up leak detection and connection testing
  - Enables prepared statement caching for performance
- **Configuration Parameters:**
  - Maximum Pool Size: 10 (default)
  - Minimum Idle: 5 (default)
  - Connection Timeout: 30000ms (default)
  - Idle Timeout: 600000ms (default)
  - Max Lifetime: 1800000ms (default)
  - Leak Detection Threshold: 60000ms (default)
- **Return:** void
- **Throws:** IOException if properties file not found

##### 2. **loadDatabaseProperties()**
- **Purpose:** Loads database configuration from properties file
- **File Location:** `resources/database.properties`
- **Return:** Properties object with database configuration
- **Throws:** IOException if file not found

##### 3. **getConnection()**
- **Purpose:** Retrieves a connection from the pool
- **Return:** Database Connection object
- **Throws:** SQLException if connection cannot be obtained
- **Note:** Connection must be closed after use to return it to pool

##### 4. **testConnection()**
- **Purpose:** Tests if database connection is available
- **Return:** boolean - true if connection successful, false otherwise
- **Use Case:** Health checks and startup validation

##### 5. **closePool()**
- **Purpose:** Closes the connection pool during application shutdown
- **Operations:**
  - Checks if pool exists and is not closed
  - Closes all connections in the pool
  - Logs pool closure
- **Return:** void

##### 6. **getPoolStats()**
- **Purpose:** Retrieves current connection pool statistics
- **Return:** String containing pool statistics:
  - Active connections
  - Idle connections
  - Total connections
  - Threads waiting for connection
- **Use Case:** Monitoring and debugging connection pool performance

---

## Handler Classes

### File: `UserHandler.java`

Handles all user-related database operations (CRUD).

#### Functions:

##### 1. **loginUser(String username, String password)**
- **Purpose:** Authenticates user with username and password
- **Parameters:**
  - `username`: User's username
  - `password`: User's password (plain text comparison for educational purposes)
- **Return:** User object if authentication successful, null otherwise
- **Validation:**
  - Checks for empty username or password
  - Logs failed login attempts
- **SQL Query:** `SELECT * FROM users WHERE username = ? AND password = ?`

##### 2. **registerUser(User user)**
- **Purpose:** Creates a new user account
- **Parameters:** User object with username, password, and role
- **Return:** boolean - true if registration successful
- **Validation:**
  - Username must be at least 3 characters
  - Password must be at least 6 characters
  - Role cannot be empty
  - Checks if username already exists
- **SQL Query:** `INSERT INTO users (username, password, role) VALUES (?, ?, ?)`

##### 3. **getUserById(int userId)**
- **Purpose:** Retrieves user by their ID
- **Parameters:** User ID
- **Return:** User object or null if not found
- **SQL Query:** `SELECT * FROM users WHERE user_id = ?`

##### 4. **getAllUsers()**
- **Purpose:** Retrieves all registered users
- **Return:** List of User objects
- **SQL Query:** `SELECT * FROM users ORDER BY user_id`

##### 5. **updateUser(User user)**
- **Purpose:** Updates user information
- **Parameters:** User object with updated information
- **Return:** boolean - true if update successful
- **Validation:**
  - User ID must be valid
  - Username must be at least 3 characters
- **SQL Query:** `UPDATE users SET username = ?, password = ?, role = ? WHERE user_id = ?`

##### 6. **deleteUser(int userId)**
- **Purpose:** Permanently deletes a user (hard delete)
- **Parameters:** User ID
- **Return:** boolean - true if deletion successful
- **SQL Query:** `DELETE FROM users WHERE user_id = ?`
- **Warning:** This is a hard delete, not recommended for production

##### 7. **usernameExists(String username)**
- **Purpose:** Checks if a username is already taken
- **Parameters:** Username to check
- **Return:** boolean - true if username exists
- **SQL Query:** `SELECT COUNT(*) FROM users WHERE username = ?`

##### 8. **changePassword(int userId, String oldPassword, String newPassword)**
- **Purpose:** Changes user password
- **Parameters:**
  - `userId`: User's ID
  - `oldPassword`: Current password (not verified in simplified version)
  - `newPassword`: New password (must be at least 6 characters)
- **Return:** boolean - true if password changed successfully
- **SQL Query:** `UPDATE users SET password = ? WHERE user_id = ?`

##### 9. **mapResultSetToUser(ResultSet rs)** (private)
- **Purpose:** Converts database ResultSet to User object
- **Parameters:** ResultSet from query
- **Return:** User object
- **Throws:** SQLException

---

### File: `ProductHandler.java`

Handles all product-related database operations (CRUD).

#### Functions:

##### 1. **hasTransactions(int productId)**
- **Purpose:** Checks if a product has any related transactions
- **Parameters:** Product ID
- **Return:** boolean - true if product has transactions
- **Use Case:** Prevents deletion of products with transaction history
- **SQL Query:** `SELECT 1 FROM transactions WHERE product_id = ? LIMIT 1`

##### 2. **addProduct(Product product)**
- **Purpose:** Adds a new product to inventory
- **Parameters:** Product object with all required fields
- **Return:** boolean - true if product added successfully
- **Validation:**
  - Product name cannot be empty
  - Unit price must be >= 0
  - Quantity must be >= 0
  - Category defaults to "Uncategorized" if empty
- **SQL Query:** `INSERT INTO products (name, category, unit_price, quantity, supplier_id) VALUES (?, ?, ?, ?, ?)`

##### 3. **getProductById(int productId)**
- **Purpose:** Retrieves product by ID
- **Parameters:** Product ID
- **Return:** Product object or null if not found
- **SQL Query:** `SELECT * FROM products WHERE product_id = ?`

##### 4. **getAllProducts()**
- **Purpose:** Retrieves all products (active and inactive)
- **Return:** List of Product objects, ordered by active status and ID
- **SQL Query:** `SELECT * FROM products ORDER BY active DESC, product_id ASC`

##### 5. **getProductsByCategory(String category)**
- **Purpose:** Retrieves all active products in a specific category
- **Parameters:** Category name
- **Return:** List of Product objects
- **SQL Query:** `SELECT * FROM products WHERE category = ? AND active = true ORDER BY product_id ASC`

##### 6. **getProductsBySupplier(int supplierId)**
- **Purpose:** Retrieves all active products from a specific supplier
- **Parameters:** Supplier ID
- **Return:** List of Product objects
- **SQL Query:** `SELECT * FROM products WHERE supplier_id = ? AND active = true ORDER BY product_id ASC`

##### 7. **getLowStockProducts(int threshold)**
- **Purpose:** Finds products with stock below threshold
- **Parameters:** Stock threshold quantity
- **Return:** List of Product objects with low stock
- **SQL Query:** `SELECT * FROM products WHERE quantity <= ? AND active = true ORDER BY quantity`

##### 8. **updateProduct(Product product)**
- **Purpose:** Updates product information
- **Parameters:** Product object with updated values
- **Return:** boolean - true if update successful
- **Validation:**
  - Product ID must be valid
  - Name cannot be empty
  - Unit price must be >= 0
  - Quantity must be >= 0
- **SQL Query:** `UPDATE products SET name = ?, category = ?, unit_price = ?, quantity = ?, supplier_id = ? WHERE product_id = ?`

##### 9. **updateProductQuantity(int productId, int newQuantity)**
- **Purpose:** Updates only the quantity of a product
- **Parameters:** Product ID and new quantity
- **Return:** boolean - true if update successful
- **SQL Query:** `UPDATE products SET quantity = ? WHERE product_id = ?`

##### 10. **deactivateProduct(int productId)**
- **Purpose:** Soft deletes a product by setting active = false
- **Parameters:** Product ID
- **Return:** boolean - true if deactivation successful
- **SQL Query:** `UPDATE products SET active = false WHERE product_id = ? AND active = true`

##### 11. **searchProductsByName(String searchTerm)**
- **Purpose:** Searches products by name (case-insensitive partial match)
- **Parameters:** Search term
- **Return:** List of matching Product objects
- **SQL Query:** `SELECT * FROM products WHERE name ILIKE ? AND active = true ORDER BY product_id ASC`
- **Note:** Uses PostgreSQL's ILIKE for case-insensitive matching

##### 12. **mapResultSetToProduct(ResultSet rs)** (private)
- **Purpose:** Converts database ResultSet to Product object
- **Parameters:** ResultSet from query
- **Return:** Product object
- **Throws:** SQLException

---

### File: `SupplierHandler.java`

Handles all supplier-related database operations (CRUD).

#### Functions:

##### 1. **addSupplier(Supplier supplier)**
- **Purpose:** Adds a new supplier to the database
- **Parameters:** Supplier object with name, contact info, email, and address
- **Return:** boolean - true if supplier added successfully
- **SQL Query:** `INSERT INTO suppliers (name, contact_info, email, address) VALUES (?, ?, ?, ?)`

##### 2. **getSupplierById(int supplierId)**
- **Purpose:** Retrieves supplier by ID
- **Parameters:** Supplier ID
- **Return:** Supplier object or null if not found
- **SQL Query:** `SELECT * FROM suppliers WHERE supplier_id = ?`

##### 3. **getAllSuppliers()**
- **Purpose:** Retrieves all suppliers with product counts
- **Return:** List of Supplier objects including product_count field
- **SQL Query:** 
  ```sql
  SELECT s.supplier_id, s.name, s.contact_info, s.email, s.address, 
         s.created_at, s.active, COUNT(p.product_id) AS product_count
  FROM suppliers s
  LEFT JOIN products p ON p.supplier_id = s.supplier_id AND p.active = true
  GROUP BY s.supplier_id
  ORDER BY s.name
  ```
- **Note:** Includes count of active products per supplier

##### 4. **updateSupplier(Supplier supplier)**
- **Purpose:** Updates supplier information
- **Parameters:** Supplier object with updated values
- **Return:** boolean - true if update successful
- **SQL Query:** `UPDATE suppliers SET name = ?, contact_info = ?, email = ?, address = ? WHERE supplier_id = ?`

##### 5. **deactivateSupplier(int supplierId)**
- **Purpose:** Soft deletes a supplier by setting active = false
- **Parameters:** Supplier ID
- **Return:** boolean - true if deactivation successful
- **SQL Query:** `UPDATE suppliers SET active = false WHERE supplier_id = ? AND active = true`

##### 6. **deleteSupplier(int supplierId)**
- **Purpose:** Permanently deletes a supplier (hard delete)
- **Parameters:** Supplier ID
- **Return:** boolean - true if deletion successful
- **SQL Query:** `DELETE FROM suppliers WHERE supplier_id = ?`
- **Warning:** Hard delete - used only for testing/cleanup

##### 7. **searchSuppliersByName(String searchTerm)**
- **Purpose:** Searches suppliers by name (case-insensitive partial match)
- **Parameters:** Search term
- **Return:** List of matching Supplier objects
- **SQL Query:** `SELECT * FROM suppliers WHERE name ILIKE ? ORDER BY name`

##### 8. **getSuppliersWithProductCount()**
- **Purpose:** Gets suppliers with their product counts, sorted by count
- **Return:** List of formatted strings with supplier info and product counts
- **SQL Query:**
  ```sql
  SELECT s.supplier_id, s.name, COUNT(p.product_id) AS product_count
  FROM suppliers s
  LEFT JOIN products p ON s.supplier_id = p.supplier_id
  GROUP BY s.supplier_id, s.name
  ORDER BY product_count DESC, s.name
  ```

##### 9. **mapResultSetToSupplier(ResultSet rs)** (private)
- **Purpose:** Converts database ResultSet to Supplier object
- **Parameters:** ResultSet from query
- **Return:** Supplier object with product count if available
- **Throws:** SQLException

---

### File: `TransactionHandler.java`

Handles all transaction-related database operations with atomic stock updates.

#### Functions:

##### 1. **recordTransaction(Transaction transaction)**
- **Purpose:** Records a sale or purchase transaction with automatic stock update
- **Parameters:** Transaction object containing product, user, quantity, and type
- **Return:** boolean - true if transaction recorded successfully
- **Validation:**
  - Product ID, User ID, and quantity must be valid
  - Transaction type must be 'Sale' or 'Purchase'
- **Operations:**
  - Uses database transaction (ACID properties)
  - For Sales: Decreases product quantity (with stock check)
  - For Purchases: Increases product quantity
  - Calculates total price from product unit price
  - Rolls back if insufficient stock or error occurs
- **SQL Queries:**
  - Sale: `UPDATE products SET quantity = quantity - ? WHERE product_id = ? AND quantity >= ?`
  - Purchase: `UPDATE products SET quantity = quantity + ? WHERE product_id = ?`
  - Insert: `INSERT INTO transactions (product_id, user_id, txn_type, quantity, total_price, notes) VALUES (?, ?, ?, ?, ?, ?)`

##### 2. **getTransactionById(int txnId)**
- **Purpose:** Retrieves transaction by ID
- **Parameters:** Transaction ID
- **Return:** Transaction object or null if not found
- **SQL Query:** `SELECT * FROM transactions WHERE txn_id = ?`

##### 3. **getAllTransactions()**
- **Purpose:** Retrieves all transactions (limited to 1000 most recent)
- **Return:** List of Transaction objects, ordered by date descending
- **SQL Query:** `SELECT * FROM transactions ORDER BY txn_date DESC LIMIT 1000`

##### 4. **getTransactionsByType(String txnType)**
- **Purpose:** Retrieves transactions filtered by type (Sale or Purchase)
- **Parameters:** Transaction type ('Sale' or 'Purchase')
- **Return:** List of Transaction objects
- **SQL Query:** `SELECT * FROM transactions WHERE txn_type = ? ORDER BY txn_date DESC`

##### 5. **getTransactionsByDateRange(LocalDateTime startDate, LocalDateTime endDate)**
- **Purpose:** Retrieves transactions within a date range
- **Parameters:** Start and end datetime
- **Return:** List of Transaction objects
- **Validation:** Start date must be before end date
- **SQL Query:** `SELECT * FROM transactions WHERE txn_date BETWEEN ? AND ? ORDER BY txn_date DESC`

##### 6. **getTransactionsByProduct(int productId)**
- **Purpose:** Retrieves all transactions for a specific product
- **Parameters:** Product ID
- **Return:** List of Transaction objects
- **SQL Query:** `SELECT * FROM transactions WHERE product_id = ? ORDER BY txn_date DESC`

##### 7. **getTodayTransactions()**
- **Purpose:** Retrieves all transactions for the current day
- **Return:** List of today's Transaction objects
- **Implementation:** Calls getTransactionsByDateRange with today's date range

##### 8. **getDailySales(LocalDate date)**
- **Purpose:** Calculates total sales amount for a specific date
- **Parameters:** Date to calculate
- **Return:** BigDecimal total sales amount
- **SQL Query:** `SELECT COALESCE(SUM(total_price), 0) as total FROM transactions WHERE txn_type = 'Sale' AND DATE(txn_date) = ?`

##### 9. **getMonthlySales(int year, int month)**
- **Purpose:** Calculates total sales for a specific month
- **Parameters:** Year and month
- **Return:** BigDecimal total sales amount
- **SQL Query:** `SELECT COALESCE(SUM(total_price), 0) as total FROM transactions WHERE txn_type = 'Sale' AND EXTRACT(YEAR FROM txn_date) = ? AND EXTRACT(MONTH FROM txn_date) = ?`

##### 10. **getProductPrice(Connection conn, int productId)** (private)
- **Purpose:** Gets the unit price of a product for transaction calculation
- **Parameters:** Database connection and product ID
- **Return:** BigDecimal unit price
- **Throws:** SQLException if product not found

##### 11. **mapResultSetToTransaction(ResultSet rs)** (private)
- **Purpose:** Converts database ResultSet to Transaction object
- **Parameters:** ResultSet from query
- **Return:** Transaction object
- **Throws:** SQLException

---

### File: `AuditLogHandler.java`

Handles audit logging for tracking all user actions in the system.

#### Functions:

##### 1. **logAction(int userId, String action, String details)**
- **Purpose:** Records a user action in the audit log
- **Parameters:**
  - `userId`: ID of user performing the action
  - `action`: Action type (e.g., "LOGIN", "ADD_PRODUCT", "DELETE_USER")
  - `details`: Additional details about the action
- **Return:** boolean - true if logged successfully
- **Validation:**
  - User ID must be valid
  - Action cannot be empty
- **SQL Query:** `INSERT INTO audit_log (user_id, action, details) VALUES (?, ?, ?)`

##### 2. **logAction(int userId, String action)** (overloaded)
- **Purpose:** Records a user action without details
- **Parameters:** User ID and action type
- **Return:** boolean - true if logged successfully
- **Implementation:** Calls main logAction with null details

##### 3. **getAllLogs()**
- **Purpose:** Retrieves all audit logs (limited to 1000 most recent)
- **Return:** List of AuditLogEntry objects with username
- **SQL Query:**
  ```sql
  SELECT al.*, u.username FROM audit_log al
  LEFT JOIN users u ON al.user_id = u.user_id
  ORDER BY al.timestamp DESC LIMIT 1000
  ```

##### 4. **getLogsByUser(int userId)**
- **Purpose:** Retrieves audit logs for a specific user
- **Parameters:** User ID
- **Return:** List of AuditLogEntry objects
- **SQL Query:**
  ```sql
  SELECT al.*, u.username FROM audit_log al
  LEFT JOIN users u ON al.user_id = u.user_id
  WHERE al.user_id = ? ORDER BY al.timestamp DESC
  ```

##### 5. **getLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate)**
- **Purpose:** Retrieves audit logs within a date range
- **Parameters:** Start and end datetime
- **Return:** List of AuditLogEntry objects
- **Validation:** Start date must be before end date
- **SQL Query:**
  ```sql
  SELECT al.*, u.username FROM audit_log al
  LEFT JOIN users u ON al.user_id = u.user_id
  WHERE al.timestamp BETWEEN ? AND ?
  ORDER BY al.timestamp DESC
  ```

##### 6. **clearOldLogs(int daysToKeep)**
- **Purpose:** Deletes audit logs older than specified days
- **Parameters:** Number of days to keep logs
- **Return:** int - number of logs deleted
- **SQL Query:** `DELETE FROM audit_log WHERE timestamp < NOW() - INTERVAL '1 day' * ?`

---

### File: `ReportHandler.java`

Comprehensive reporting handler for sales, inventory, transactions, suppliers, and user activity.

#### Sales Report Functions:

##### 1. **getSalesSummary(LocalDate startDate, LocalDate endDate)**
- **Purpose:** Generates sales summary for a date range
- **Parameters:** Start and end date
- **Return:** SalesSummary object with total transactions, items sold, and revenue
- **SQL Query:**
  ```sql
  SELECT COUNT(*) as total_transactions,
         SUM(quantity) as total_items_sold,
         SUM(total_price) as total_revenue
  FROM transactions
  WHERE txn_type = 'Sale' AND DATE(txn_date) BETWEEN ? AND ?
  ```

##### 2. **getTodaySales()**
- **Purpose:** Gets today's sales summary
- **Return:** SalesSummary for current day
- **Implementation:** Calls getSalesSummary with today's date

##### 3. **getWeeklySales()**
- **Purpose:** Gets this week's sales summary
- **Return:** SalesSummary from Monday to today
- **Implementation:** Calculates week start and calls getSalesSummary

##### 4. **getMonthlySales()**
- **Purpose:** Gets this month's sales summary
- **Return:** SalesSummary from first of month to today
- **Implementation:** Calculates month start and calls getSalesSummary

##### 5. **getTopSellingProducts(int limit, LocalDate startDate, LocalDate endDate)**
- **Purpose:** Identifies top-selling products by quantity sold
- **Parameters:** Result limit and date range
- **Return:** List of TopProduct objects with sales statistics
- **SQL Query:**
  ```sql
  SELECT p.product_id, p.name, p.category,
         SUM(t.quantity) as total_sold,
         SUM(t.total_price) as total_revenue
  FROM transactions t
  JOIN products p ON t.product_id = p.product_id
  WHERE t.txn_type = 'Sale' AND DATE(t.txn_date) BETWEEN ? AND ?
  GROUP BY p.product_id, p.name, p.category
  ORDER BY total_sold DESC LIMIT ?
  ```

##### 6. **getSalesByCategory(LocalDate startDate, LocalDate endDate)**
- **Purpose:** Generates sales statistics grouped by product category
- **Parameters:** Date range
- **Return:** List of CategorySales objects with transaction count, quantity, and revenue per category
- **SQL Query:**
  ```sql
  SELECT p.category,
         COUNT(DISTINCT t.txn_id) as transaction_count,
         SUM(t.quantity) as total_quantity,
         SUM(t.total_price) as total_revenue
  FROM transactions t
  JOIN products p ON t.product_id = p.product_id
  WHERE t.txn_type = 'Sale' AND DATE(t.txn_date) BETWEEN ? AND ?
  GROUP BY p.category
  ORDER BY total_revenue DESC
  ```

#### Stock Report Functions:

##### 7. **getInventoryStatus()**
- **Purpose:** Provides comprehensive inventory status snapshot
- **Return:** InventoryStatus object with:
  - Total products count
  - Total items in stock
  - Total inventory value
  - Low stock products count (quantity <= 10)
  - Out of stock products count (quantity = 0)
- **SQL Query:**
  ```sql
  SELECT COUNT(*) as total_products,
         SUM(quantity) as total_items,
         SUM(unit_price * quantity) as total_value,
         COUNT(CASE WHEN quantity <= 10 THEN 1 END) as low_stock_count,
         COUNT(CASE WHEN quantity = 0 THEN 1 END) as out_of_stock_count
  FROM products WHERE active = true
  ```

##### 8. **getProductsByStockLevel()**
- **Purpose:** Categorizes products by stock level
- **Return:** Map with three categories:
  - `out_of_stock`: Products with quantity = 0
  - `low_stock`: Products with quantity <= 10
  - `normal_stock`: Products with quantity > 10
- **SQL Query:** `SELECT product_id, name, category, quantity, unit_price FROM products WHERE active = true ORDER BY quantity ASC`

#### Transaction Report Functions:

##### 9. **getTransactionStats(LocalDate startDate, LocalDate endDate)**
- **Purpose:** Generates aggregated transaction statistics by type
- **Parameters:** Date range
- **Return:** TransactionStats object with statistics for Sales and Purchases
- **SQL Query:**
  ```sql
  SELECT txn_type,
         COUNT(*) as count,
         SUM(quantity) as total_quantity,
         SUM(total_price) as total_amount
  FROM transactions
  WHERE DATE(txn_date) BETWEEN ? AND ?
  GROUP BY txn_type
  ```

#### Supplier Report Functions:

##### 10. **getSupplierPerformance(LocalDate startDate, LocalDate endDate)**
- **Purpose:** Analyzes supplier performance based on product sales
- **Parameters:** Date range
- **Return:** List of SupplierPerformance objects with:
  - Supplier ID and name
  - Number of products
  - Total items sold
  - Total revenue generated
- **SQL Query:**
  ```sql
  SELECT s.supplier_id, s.name,
         COUNT(DISTINCT p.product_id) as product_count,
         COALESCE(SUM(t.total_price), 0) as total_revenue,
         COALESCE(SUM(t.quantity), 0) as total_items_sold
  FROM suppliers s
  LEFT JOIN products p ON s.supplier_id = p.supplier_id AND p.active = true
  LEFT JOIN transactions t ON p.product_id = t.product_id
    AND t.txn_type = 'Sale'
    AND DATE(t.txn_date) BETWEEN ? AND ?
  WHERE s.active = true
  GROUP BY s.supplier_id, s.name
  ORDER BY total_revenue DESC
  ```

##### 11. **getAllSuppliersSummary()**
- **Purpose:** Gets simple summary of all suppliers with product counts
- **Return:** List of SupplierSummary objects
- **SQL Query:**
  ```sql
  SELECT s.supplier_id, s.name, s.contact_info,
         COUNT(p.product_id) as product_count
  FROM suppliers s
  LEFT JOIN products p ON s.supplier_id = p.supplier_id AND p.active = true
  WHERE s.active = true
  GROUP BY s.supplier_id, s.name, s.contact_info
  ORDER BY product_count DESC
  ```

#### User Activity Report Functions:

##### 12. **getUserActivityReport(LocalDate startDate, LocalDate endDate)**
- **Purpose:** Analyzes user activity from audit logs
- **Parameters:** Date range
- **Return:** List of UserActivity objects with:
  - User ID, username, and role
  - Action count
  - Last action timestamp
- **SQL Query:**
  ```sql
  SELECT u.user_id, u.username, u.role,
         COUNT(al.log_id) as action_count,
         MAX(al.timestamp) as last_action
  FROM users u
  LEFT JOIN audit_log al ON u.user_id = al.user_id
    AND DATE(al.timestamp) BETWEEN ? AND ?
  GROUP BY u.user_id, u.username, u.role
  ORDER BY action_count DESC
  ```

##### 13. **getMostActiveUsers(int limit, LocalDate startDate, LocalDate endDate)**
- **Purpose:** Identifies most active users by action count
- **Parameters:** Result limit and date range
- **Return:** Limited list of UserActivity objects, sorted by action count
- **Implementation:** Calls getUserActivityReport and sorts/limits results

---

## Server Components

### File: `SocketServer.java`

Multi-threaded socket server for handling client connections.

#### Functions:

##### 1. **SocketServer()** (Constructor)
- **Purpose:** Initializes the server with thread pool
- **Configuration:**
  - Creates ExecutorService with fixed thread pool of 50 threads
  - Initializes client counter

##### 2. **start()**
- **Purpose:** Starts the socket server and accepts client connections
- **Operations:**
  - Creates ServerSocket on port 8080
  - Tests database connection
  - Enters infinite loop to accept clients
  - Assigns each client to a thread from pool
  - Handles graceful shutdown
- **Error Handling:** Logs errors and ensures proper cleanup

##### 3. **stop()**
- **Purpose:** Gracefully shuts down the server
- **Operations:**
  - Closes server socket
  - Shuts down executor service
  - Closes database connection pool
  - Logs shutdown status

##### 4. **getActiveClients()**
- **Purpose:** Returns count of active client connections
- **Return:** int - number of connected clients

##### 5. **main(String[] args)**
- **Purpose:** Server startup entry point
- **Operations:**
  - Creates SocketServer instance
  - Registers shutdown hook for cleanup
  - Starts the server

---

### File: `ClientHandler.java`

Handles individual client socket connections with JSON-based protocol.

#### Core Functions:

##### 1. **ClientHandler(Socket socket, int clientId)** (Constructor)
- **Purpose:** Initializes handler for a client connection
- **Operations:**
  - Stores socket and client ID
  - Sets socket options (timeout, keep-alive, TCP no delay)
  - Initializes all handler instances
  - Sets socket timeout to 5 minutes

##### 2. **run()**
- **Purpose:** Main client processing loop (implements Runnable)
- **Operations:**
  - Sets up input/output streams
  - Registers client in ActiveClientRegistry
  - Sends welcome message
  - Processes client requests in loop
  - Handles disconnection

##### 3. **processRequest(String action, JsonObject request)**
- **Purpose:** Routes client requests to appropriate handlers
- **Parameters:** Action name and request JSON
- **Return:** JsonObject response
- **Supported Actions:**
  - User: login, register, logout, get_all_users, change_password
  - Product: get_all_products, get_product, add_product, update_product, delete_product, search_products, get_low_stock, get_products_by_supplier
  - Supplier: get_all_suppliers, get_supplier, add_supplier, update_supplier, delete_supplier
  - Transaction: record_transaction, get_all_transactions, get_today_transactions, get_transactions_by_date_range, get_daily_sales, get_monthly_sales
  - Audit: get_audit_logs, get_connected_clients
  - Report: get_sales_summary, get_today_sales, get_weekly_sales, get_monthly_sales_report, get_top_selling_products, get_sales_by_category, get_inventory_status, get_products_by_stock_level, get_transaction_stats, get_supplier_performance, get_all_suppliers_summary, get_user_activity_report, get_most_active_users

##### 4. **requiresAuth(String action)**
- **Purpose:** Determines if action requires authentication
- **Parameters:** Action name
- **Return:** boolean - false only for login/logout actions
- **Implementation:** Checks against whitelist of public actions

#### Authentication Functions:

##### 5. **handleLogin(JsonObject request)**
- **Purpose:** Authenticates user and creates session
- **Operations:**
  - Validates username and password
  - Checks rate limiting (currently disabled)
  - Authenticates via UserHandler
  - Updates client registry with user info
  - Logs action to audit log
- **Return:** JsonObject with success status and user data

##### 6. **handleLogout()**
- **Purpose:** Ends user session
- **Operations:**
  - Logs logout to audit log
  - Clears authenticated user
- **Return:** JsonObject with success status

##### 7. **handleRegister(JsonObject request)**
- **Purpose:** Registers new user (Admin only)
- **Access:** Admin role required
- **Note:** Currently not used by frontend
- **Return:** JsonObject with success status

##### 8. **handleChangePassword(JsonObject request)**
- **Purpose:** Changes user password
- **Access:** Authenticated user
- **Note:** Not currently used by frontend
- **Return:** JsonObject with success status

##### 9. **handleGetAllUsers()**
- **Purpose:** Retrieves all registered users
- **Access:** Authenticated user required
- **Return:** JsonObject with list of users

#### Product Action Handlers:

##### 10. **handleGetAllProducts()**
- **Purpose:** Retrieves all products in inventory
- **Access:** Authenticated user required
- **Return:** JsonObject with list of products

##### 11. **handleGetProduct(JsonObject request)**
- **Purpose:** Retrieves specific product by ID
- **Parameters:** productId in request
- **Return:** JsonObject with product data

##### 12. **handleAddProduct(JsonObject request)**
- **Purpose:** Creates new product in inventory
- **Access:** Admin or Stock Manager required
- **Parameters:** name, category, unitPrice, quantity, supplierId (optional)
- **Operations:**
  - Validates product data
  - Adds product via ProductHandler
  - Logs action to audit log
- **Return:** JsonObject with success status and product data

##### 13. **handleUpdateProduct(JsonObject request)**
- **Purpose:** Updates existing product
- **Access:** Admin or Stock Manager required
- **Parameters:** productId, name, category, unitPrice, quantity, supplierId
- **Operations:**
  - Validates product data
  - Updates via ProductHandler
  - Logs action to audit log
- **Return:** JsonObject with success status

##### 14. **handleDeleteProduct(JsonObject request)**
- **Purpose:** Deactivates product (soft delete)
- **Access:** Admin only
- **Parameters:** productId
- **Operations:**
  - Deactivates product via ProductHandler
  - Logs action to audit log
- **Return:** JsonObject with success status

##### 15. **handleSearchProducts(JsonObject request)**
- **Purpose:** Searches products by name
- **Parameters:** search_term
- **Return:** JsonObject with matching products

##### 16. **handleGetLowStock(JsonObject request)**
- **Purpose:** Retrieves products below stock threshold
- **Parameters:** threshold
- **Return:** JsonObject with low stock products

##### 17. **handleGetProductsBySupplier(JsonObject request)**
- **Purpose:** Gets all products from specific supplier
- **Parameters:** supplierId
- **Return:** JsonObject with supplier's products

#### Supplier Action Handlers:

##### 18. **handleGetAllSuppliers()**
- **Purpose:** Retrieves all suppliers
- **Access:** Authenticated user required
- **Return:** JsonObject with list of suppliers

##### 19. **handleGetSupplier(JsonObject request)**
- **Purpose:** Retrieves specific supplier by ID
- **Parameters:** supplierId
- **Return:** JsonObject with supplier data

##### 20. **handleAddSupplier(JsonObject request)**
- **Purpose:** Creates new supplier
- **Access:** Admin or Stock Manager required
- **Parameters:** supplier object (name, contactInfo, email, address)
- **Return:** JsonObject with success status and supplier data

##### 21. **handleUpdateSupplier(JsonObject request)**
- **Purpose:** Updates existing supplier
- **Access:** Admin or Stock Manager required
- **Parameters:** supplier object with ID
- **Return:** JsonObject with success status

##### 22. **handleDeleteSupplier(JsonObject request)**
- **Purpose:** Deactivates supplier (soft delete)
- **Access:** Admin only
- **Parameters:** supplierId
- **Return:** JsonObject with success status

#### Transaction Action Handlers:

##### 23. **handleRecordTransaction(JsonObject request)**
- **Purpose:** Records sale or purchase transaction
- **Access:** Authenticated user required
- **Parameters:** transaction object (productId, quantity, txnType, notes)
- **Operations:**
  - Validates transaction data
  - Records via TransactionHandler (atomically updates stock)
  - Retrieves updated product data
  - Logs action to audit log
- **Return:** JsonObject with success status and updated product

##### 24. **handleGetAllTransactions()**
- **Purpose:** Retrieves all transactions
- **Access:** Authenticated user required
- **Return:** JsonObject with list of transactions

##### 25. **handleGetTodayTransactions()**
- **Purpose:** Retrieves today's transactions
- **Access:** Authenticated user required
- **Return:** JsonObject with today's transactions

##### 26. **handleGetTransactionsByDateRange(JsonObject request)**
- **Purpose:** Retrieves transactions within date range
- **Parameters:** startDate, endDate (ISO format)
- **Return:** JsonObject with transactions in range

##### 27. **handleGetDailySales(JsonObject request)**
- **Purpose:** Calculates total sales for specific date
- **Parameters:** date (ISO format)
- **Return:** JsonObject with sales amount

##### 28. **handleGetMonthlySales(JsonObject request)**
- **Purpose:** Calculates total sales for specific month
- **Parameters:** year, month
- **Return:** JsonObject with sales amount

#### Audit Log Handlers:

##### 29. **handleGetAuditLogs()**
- **Purpose:** Retrieves system audit logs
- **Access:** Admin only
- **Return:** JsonObject with list of audit log entries

##### 30. **handleGetConnectedClients()**
- **Purpose:** Retrieves list of currently connected clients
- **Access:** Admin only
- **Use Case:** Server monitoring
- **Return:** JsonObject with active client list

#### Report Action Handlers:

##### 31. **handleGetSalesSummary(JsonObject request)**
- **Purpose:** Generates sales summary for date range
- **Parameters:** startDate, endDate
- **Return:** JsonObject with SalesSummary data

##### 32. **handleGetTodaySales()**
- **Purpose:** Gets today's sales summary
- **Return:** JsonObject with today's sales data

##### 33. **handleGetWeeklySales()**
- **Purpose:** Gets this week's sales summary
- **Return:** JsonObject with weekly sales data

##### 34. **handleGetMonthlySalesReport()**
- **Purpose:** Gets this month's sales summary
- **Return:** JsonObject with monthly sales data

##### 35. **handleGetTopSellingProducts(JsonObject request)**
- **Purpose:** Identifies top-selling products
- **Parameters:** limit (optional, default 10), startDate, endDate
- **Return:** JsonObject with top products list

##### 36. **handleGetSalesByCategory(JsonObject request)**
- **Purpose:** Generates sales breakdown by category
- **Parameters:** startDate, endDate
- **Return:** JsonObject with category sales data

##### 37. **handleGetInventoryStatus()**
- **Purpose:** Provides current inventory status snapshot
- **Return:** JsonObject with inventory statistics

##### 38. **handleGetProductsByStockLevel()**
- **Purpose:** Categorizes products by stock level
- **Return:** JsonObject with products grouped by stock level

##### 39. **handleGetTransactionStats(JsonObject request)**
- **Purpose:** Generates transaction statistics
- **Parameters:** startDate, endDate
- **Return:** JsonObject with transaction stats by type

##### 40. **handleGetSupplierPerformance(JsonObject request)**
- **Purpose:** Analyzes supplier performance
- **Parameters:** startDate, endDate
- **Return:** JsonObject with supplier performance data

##### 41. **handleGetAllSuppliersSummary()**
- **Purpose:** Gets summary of all suppliers
- **Return:** JsonObject with supplier summaries

##### 42. **handleGetUserActivityReport(JsonObject request)**
- **Purpose:** Generates user activity report
- **Access:** Admin only
- **Parameters:** startDate, endDate
- **Return:** JsonObject with user activity data

##### 43. **handleGetMostActiveUsers(JsonObject request)**
- **Purpose:** Identifies most active users
- **Access:** Admin only
- **Parameters:** limit (optional, default 5), startDate, endDate
- **Return:** JsonObject with active users list

#### Helper Functions:

##### 44. **createResponse(boolean success, String message, Object data)**
- **Purpose:** Creates standardized JSON response
- **Parameters:** Success status, message, and optional data
- **Return:** JsonObject with standard response format (success, message, timestamp, data)

##### 45. **sendResponse(JsonObject response)**
- **Purpose:** Sends JSON response to client
- **Parameters:** Response JSON object
- **Implementation:** Serializes to JSON and writes to output stream

##### 46. **isAuthenticated()**
- **Purpose:** Checks if user is authenticated
- **Return:** boolean - true if user logged in

##### 47. **isAdmin()**
- **Purpose:** Checks if authenticated user is Admin
- **Return:** boolean - true if user has Admin role

##### 48. **isAdminOrManager()**
- **Purpose:** Checks if user is Admin or Stock Manager
- **Return:** boolean - true if user has elevated privileges

##### 49. **disconnect()**
- **Purpose:** Cleanly closes client connection
- **Operations:**
  - Closes input/output streams
  - Closes socket
  - Removes client from registry
  - Logs disconnection

---

### File: `ActiveClientRegistry.java`

Thread-safe registry for monitoring active client connections.

#### Functions:

##### 1. **add(ActiveClient client)**
- **Purpose:** Registers a new client connection
- **Parameters:** ActiveClient object
- **Thread Safety:** Uses ConcurrentHashMap
- **Return:** void

##### 2. **updateUser(int clientId, String username, String role)**
- **Purpose:** Updates client with authenticated user information
- **Parameters:** Client ID, username, and role
- **Use Case:** Called after successful login
- **Return:** void

##### 3. **remove(int clientId)**
- **Purpose:** Removes client from registry on disconnect
- **Parameters:** Client ID
- **Return:** void

##### 4. **getAll()**
- **Purpose:** Retrieves all active clients
- **Return:** Collection of ActiveClient objects
- **Use Case:** Server monitoring and admin dashboard

##### 5. **count()**
- **Purpose:** Returns number of active connections
- **Return:** int - active client count

---

## Utility Classes

### File: `JsonUtil.java`

JSON serialization/deserialization utility for client-server communication.

#### Functions:

##### 1. **getGson()**
- **Purpose:** Provides configured Gson instance
- **Configuration:**
  - Custom LocalDateTime adapter
  - ISO date-time format
  - Thread-safe singleton
- **Return:** Gson instance

##### 2. **toJson(Object object)**
- **Purpose:** Converts Java object to JSON string
- **Parameters:** Object to serialize
- **Return:** JSON string or null on error
- **Error Handling:** Logs errors and returns null

##### 3. **fromJson(String json, Class<T> classOfT)**
- **Purpose:** Deserializes JSON string to Java object
- **Parameters:** JSON string and target class type
- **Return:** Object of type T or null on error
- **Error Handling:** Logs JSON parsing errors

##### 4. **fromJson(String json, Type typeOfT)** (overloaded)
- **Purpose:** Deserializes JSON with generic type support
- **Parameters:** JSON string and target type
- **Return:** Object of specified type or null on error
- **Use Case:** For collections and generic types

##### 5. **isValidJson(String json)**
- **Purpose:** Validates if string is well-formed JSON
- **Parameters:** String to validate
- **Return:** boolean - true if valid JSON
- **Use Case:** Input validation before parsing

---

## Summary

### System Architecture

The inventory management system is built with:

1. **Database Layer (@db):** PostgreSQL database with 5 tables, triggers, views, and soft-delete support
2. **Connection Management:** HikariCP connection pooling for optimal performance
3. **Handler Layer:** Separate handlers for Users, Products, Suppliers, Transactions, Audit Logs, and Reports
4. **Server Layer:** Multi-threaded socket server using thread pools
5. **Communication Protocol:** JSON-based request/response over TCP sockets

### Key Features

- **Multi-user Support:** Concurrent client connections with thread pooling
- **Role-Based Access Control:** Admin, Stock Manager, and Cashier roles with different permissions
- **Audit Logging:** Complete tracking of all user actions
- **Atomic Transactions:** Database transactions ensure stock consistency
- **Soft Deletes:** Products and suppliers use active flags instead of hard deletes
- **Comprehensive Reporting:** Sales, inventory, transaction, supplier, and user activity reports
- **Connection Pooling:** HikariCP for efficient database connection management
- **Thread Safety:** ConcurrentHashMap for active client registry

### Function Categories

- **CRUD Operations:** 40+ functions for managing users, products, suppliers, and transactions
- **Authentication:** Login, logout, and session management
- **Reporting:** 13 comprehensive reporting functions
- **Audit:** Action logging and retrieval functions
- **Monitoring:** Active client tracking and connection statistics
- **Utility:** JSON serialization and database connection management

### Total Function Count

- **Database Schema:** 1 trigger function, 2 views
- **Handler Classes:** 70+ functions across 6 handler classes
- **Server Components:** 50+ functions for client handling and request routing
- **Utility Classes:** 5 JSON utility functions
- **Database Connection:** 6 connection management functions

**Grand Total:** 130+ documented functions across the entire system.
