-- =================================================================
-- ALL STORE STOCK MANAGEMENT - DATABASE SCHEMA
-- FIXED: Added BCrypt hashed passwords for test users
-- =================================================================

-- Drop existing tables (в правильном порядке из-за зависимостей)
DROP TABLE IF EXISTS audit_log CASCADE;
DROP TABLE IF EXISTS transactions CASCADE;
DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS suppliers CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- =================================================================
-- USERS TABLE
-- =================================================================
CREATE TABLE users (
                       user_id SERIAL PRIMARY KEY,
                       username VARCHAR(50) UNIQUE NOT NULL,
                       password VARCHAR(255) NOT NULL,  -- BCrypt hash (60 characters)
                       role VARCHAR(20) NOT NULL CHECK (role IN ('Admin', 'Stock Manager', 'Cashier')),
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
                       CONSTRAINT username_length CHECK (LENGTH(username) >= 3),
                       CONSTRAINT password_length CHECK (LENGTH(password) >= 6)
);

-- Индексы для быстрого поиска
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_role ON users(role);

-- =================================================================
-- SUPPLIERS TABLE
-- =================================================================
CREATE TABLE suppliers (
                           supplier_id SERIAL PRIMARY KEY,
                           name VARCHAR(100) NOT NULL,
                           contact_info VARCHAR(255),
                           email VARCHAR(100),
                           address TEXT,
                           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
                           CONSTRAINT supplier_name_not_empty CHECK (LENGTH(TRIM(name)) > 0)
);

-- Индекс для поиска по имени
CREATE INDEX idx_suppliers_name ON suppliers(name);

-- =================================================================
-- PRODUCTS TABLE
-- =================================================================
CREATE TABLE products (
                          product_id SERIAL PRIMARY KEY,
                          name VARCHAR(100) NOT NULL,
                          category VARCHAR(50),
                          unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
                          quantity INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
                          supplier_id INT REFERENCES suppliers(supplier_id) ON DELETE SET NULL,
                          last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
                          CONSTRAINT product_name_not_empty CHECK (LENGTH(TRIM(name)) > 0)
);

-- Индексы для быстрого поиска
CREATE INDEX idx_products_name ON products(name);
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_supplier ON products(supplier_id);

-- Триггер для автоматического обновления last_updated
CREATE OR REPLACE FUNCTION update_last_updated()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_updated = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_product_timestamp
    BEFORE UPDATE ON products
    FOR EACH ROW
    EXECUTE FUNCTION update_last_updated();

-- =================================================================
-- TRANSACTIONS TABLE
-- =================================================================
CREATE TABLE transactions (
                              txn_id SERIAL PRIMARY KEY,
                              product_id INT NOT NULL REFERENCES products(product_id) ON DELETE RESTRICT,
                              user_id INT NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
                              txn_type VARCHAR(20) NOT NULL CHECK (txn_type IN ('Sale', 'Purchase')),
                              quantity INT NOT NULL CHECK (quantity > 0),
                              total_price NUMERIC(12,2) NOT NULL CHECK (total_price >= 0),
                              txn_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              notes TEXT
);

-- Индексы для отчетов и статистики
CREATE INDEX idx_transactions_date ON transactions(txn_date);
CREATE INDEX idx_transactions_type ON transactions(txn_type);
CREATE INDEX idx_transactions_product ON transactions(product_id);
CREATE INDEX idx_transactions_user ON transactions(user_id);

-- =================================================================
-- AUDIT LOG TABLE
-- =================================================================
CREATE TABLE audit_log (
                           log_id SERIAL PRIMARY KEY,
                           user_id INT REFERENCES users(user_id) ON DELETE SET NULL,
                           action VARCHAR(100) NOT NULL,
                           details TEXT,
                           timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
                           CONSTRAINT action_not_empty CHECK (LENGTH(TRIM(action)) > 0)
);

-- Индекс для поиска по пользователю и времени
CREATE INDEX idx_audit_log_user ON audit_log(user_id);
CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp);

COMMENT ON TABLE audit_log IS 'Audit trail for all user actions - matches Java AuditLogHandler';
COMMENT ON COLUMN audit_log.details IS 'Additional details about the action (can be JSON or plain text)';

-- =================================================================
-- TEST DATA WITH BCRYPT HASHED PASSWORDS
-- =================================================================

-- FIXED: Тестовые пользователи с хешированными паролями (BCrypt)
-- admin123 -> $2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYCj.MxEZsC
-- manager123 -> $2a$12$6vCXn7hFQ0gBnJMPHgfhM.mUzJhY5c6.qk8b1Zr3PqX4xQyW9N0Oy
-- cashier123 -> $2a$12$LHcV8GgHnhU9fQ7qGfCLbO3XJqbz9wC3kG7Nxm2KvQqLp8YzN3Xgm

INSERT INTO users (username, password, role) VALUES
                                                 ('admin', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYCj.MxEZsC', 'Admin'),
                                                 ('manager', '$2a$12$6vCXn7hFQ0gBnJMPHgfhM.mUzJhY5c6.qk8b1Zr3PqX4xQyW9N0Oy', 'Stock Manager'),
                                                 ('cashier', '$2a$12$LHcV8GgHnhU9fQ7qGfCLbO3XJqbz9wC3kG7Nxm2KvQqLp8YzN3Xgm', 'Cashier');

-- Тестовые поставщики
INSERT INTO suppliers (name, contact_info, email, address) VALUES
                                                               ('Coca-Cola Uzbekistan', '+998 71 123 4567', 'info@coca-cola.uz', 'Tashkent, Yunusabad'),
                                                               ('PepsiCo Central Asia', '+998 71 234 5678', 'sales@pepsi.uz', 'Tashkent, Chilanzar'),
                                                               ('Local Dairy Farm', '+998 90 123 4567', 'dairy@farm.uz', 'Tashkent Region');

-- Тестовые продукты
INSERT INTO products (name, category, unit_price, quantity, supplier_id) VALUES
                                                                             ('Coca-Cola 1.5L', 'Beverages', 8500.00, 100, 1),
                                                                             ('Pepsi 1.5L', 'Beverages', 8000.00, 150, 2),
                                                                             ('Milk 1L', 'Dairy', 12000.00, 50, 3),
                                                                             ('Bread White', 'Bakery', 3500.00, 200, NULL),
                                                                             ('Eggs 10pcs', 'Dairy', 18000.00, 80, 3),
                                                                             ('Fanta Orange 1.5L', 'Beverages', 7500.00, 80, 1),
                                                                             ('Sprite 1.5L', 'Beverages', 7500.00, 80, 1),
                                                                             ('Mineral Water 1.5L', 'Beverages', 2500.00, 200, 1),
                                                                             ('Lay''s Chips Classic 150g', 'Snacks', 12000.00, 60, 2),
                                                                             ('Yogurt Natural 500g', 'Dairy', 9000.00, 30, 3);

-- Тестовые транзакции
INSERT INTO transactions (product_id, user_id, txn_type, quantity, total_price, notes) VALUES
                                                                                           (1, 3, 'Sale', 5, 42500.00, 'Cash payment'),
                                                                                           (2, 3, 'Sale', 3, 24000.00, 'Card payment'),
                                                                                           (3, 2, 'Purchase', 100, 1200000.00, 'Wholesale order'),
                                                                                           (4, 3, 'Sale', 10, 35000.00, 'Morning rush'),
                                                                                           (1, 3, 'Sale', 2, 17000.00, 'Customer purchase'),
                                                                                           (6, 3, 'Sale', 5, 37500.00, 'Card payment'),
                                                                                           (9, 3, 'Sale', 3, 36000.00, 'Cash payment');

-- Тестовые записи в audit log
INSERT INTO audit_log (user_id, action, details) VALUES
                                                     (1, 'LOGIN', 'Admin logged in from 192.168.1.100'),
                                                     (1, 'ADD_USER', 'Created new user: manager with role Stock Manager'),
                                                     (1, 'ADD_USER', 'Created new user: cashier with role Cashier'),
                                                     (1, 'ADD_SUPPLIER', 'Added supplier: Coca-Cola Uzbekistan'),
                                                     (1, 'ADD_SUPPLIER', 'Added supplier: PepsiCo Central Asia'),
                                                     (2, 'LOGIN', 'Stock Manager logged in from 192.168.1.101'),
                                                     (2, 'ADD_PRODUCT', 'Added product: Coca-Cola 1.5L (Category: Beverages, Price: 8500.00)'),
                                                     (2, 'ADD_PRODUCT', 'Added product: Pepsi 1.5L (Category: Beverages, Price: 8000.00)'),
                                                     (2, 'RECORD_PURCHASE', 'Recorded purchase: Product ID 3, Quantity 100, Total: 1200000.00'),
                                                     (3, 'LOGIN', 'Cashier logged in from 192.168.1.102'),
                                                     (3, 'RECORD_SALE', 'Recorded sale: Product ID 1 (Coca-Cola 1.5L), Quantity 5, Total: 42500.00'),
                                                     (3, 'RECORD_SALE', 'Recorded sale: Product ID 2 (Pepsi 1.5L), Quantity 3, Total: 24000.00'),
                                                     (3, 'RECORD_SALE', 'Recorded sale: Product ID 4 (Bread White), Quantity 10, Total: 35000.00');

-- =================================================================
-- USEFUL VIEWS (для отчетов)
-- =================================================================

-- View: Текущий инвентарь с информацией о поставщиках
CREATE OR REPLACE VIEW v_inventory_overview AS
SELECT
    p.product_id,
    p.name AS product_name,
    p.category,
    p.unit_price,
    p.quantity,
    p.unit_price * p.quantity AS total_value,
    COALESCE(s.name, 'No Supplier') AS supplier_name,
    COALESCE(s.contact_info, 'N/A') AS supplier_contact
FROM products p
         LEFT JOIN suppliers s ON p.supplier_id = s.supplier_id
ORDER BY p.category, p.name;

-- View: Продажи по дням
CREATE OR REPLACE VIEW v_daily_sales AS
SELECT
    DATE(txn_date) AS sale_date,
    COUNT(*) AS total_transactions,
    SUM(quantity) AS total_items_sold,
    SUM(total_price) AS total_revenue
FROM transactions
WHERE txn_type = 'Sale'
GROUP BY DATE(txn_date)
ORDER BY sale_date DESC;

-- View: Топ продаваемые товары
CREATE OR REPLACE VIEW v_top_selling_products AS
SELECT
    p.product_id,
    p.name AS product_name,
    p.category,
    COUNT(t.txn_id) AS transaction_count,
    SUM(t.quantity) AS total_sold,
    SUM(t.total_price) AS total_revenue
FROM products p
         INNER JOIN transactions t ON p.product_id = t.product_id
WHERE t.txn_type = 'Sale'
GROUP BY p.product_id, p.name, p.category
ORDER BY total_revenue DESC
    LIMIT 10;

-- View: Low stock products (меньше 50 единиц)
CREATE OR REPLACE VIEW v_low_stock_products AS
SELECT
    p.product_id,
    p.name AS product_name,
    p.category,
    p.quantity,
    p.unit_price,
    COALESCE(s.name, 'No Supplier') AS supplier_name,
    COALESCE(s.contact_info, 'N/A') AS supplier_contact
FROM products p
         LEFT JOIN suppliers s ON p.supplier_id = s.supplier_id
WHERE p.quantity <= 50
ORDER BY p.quantity ASC, p.category, p.name;

-- View: User activity summary
CREATE OR REPLACE VIEW v_user_activity AS
SELECT
    u.user_id,
    u.username,
    u.role,
    COUNT(DISTINCT t.txn_id) AS total_transactions,
    COUNT(DISTINCT al.log_id) AS total_actions,
    MAX(al.timestamp) AS last_activity
FROM users u
         LEFT JOIN transactions t ON u.user_id = t.user_id
         LEFT JOIN audit_log al ON u.user_id = al.user_id
GROUP BY u.user_id, u.username, u.role
ORDER BY total_transactions DESC;

-- =================================================================
-- VERIFICATION QUERIES
-- =================================================================

-- Проверить все таблицы
SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL
SELECT 'suppliers', COUNT(*) FROM suppliers
UNION ALL
SELECT 'products', COUNT(*) FROM products
UNION ALL
SELECT 'transactions', COUNT(*) FROM transactions
UNION ALL
SELECT 'audit_log', COUNT(*) FROM audit_log;

-- Проверить структуру audit_log
SELECT
    column_name,
    data_type,
    character_maximum_length,
    is_nullable
FROM information_schema.columns
WHERE table_name = 'audit_log'
ORDER BY ordinal_position;

-- Проверить views
SELECT * FROM v_inventory_overview LIMIT 5;
SELECT * FROM v_daily_sales LIMIT 5;
SELECT * FROM v_top_selling_products LIMIT 5;
SELECT * FROM v_low_stock_products LIMIT 5;

-- =================================================================
-- TEST LOGIN CREDENTIALS (for reference only)
-- =================================================================
-- Username: admin    | Password: admin123    | Role: Admin
-- Username: manager  | Password: manager123  | Role: Stock Manager
-- Username: cashier  | Password: cashier123  | Role: Cashier
-- =================================================================

-- Success message
DO $$
BEGIN
    RAISE NOTICE '=================================================================';
    RAISE NOTICE 'Database schema created successfully!';
    RAISE NOTICE 'SECURITY: All passwords are now BCrypt hashed';
    RAISE NOTICE 'Compatible with Java Backend (UserHandler with BCrypt)';
    RAISE NOTICE '=================================================================';
    RAISE NOTICE 'Default users (passwords are hashed in database):';
    RAISE NOTICE '  - admin/admin123 (Admin)';
    RAISE NOTICE '  - manager/manager123 (Stock Manager)';
    RAISE NOTICE '  - cashier/cashier123 (Cashier)';
    RAISE NOTICE '=================================================================';
END $$;