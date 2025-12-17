-- =================================================================
-- ALL STORE STOCK MANAGEMENT - DATABASE SCHEMA
-- SOFT DELETE ENABLED (active = true/false)
-- =================================================================

-- Drop existing tables (в правильном порядке)
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
                       password VARCHAR(255) NOT NULL,
                       role VARCHAR(20) NOT NULL CHECK (role IN ('Admin', 'Stock Manager', 'Cashier')),
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                       CONSTRAINT username_length CHECK (LENGTH(username) >= 3),
                       CONSTRAINT password_length CHECK (LENGTH(password) >= 6)
);

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

                           CONSTRAINT supplier_name_not_empty CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE INDEX idx_suppliers_name ON suppliers(name);

-- =================================================================
-- PRODUCTS TABLE (SOFT DELETE ENABLED)
-- =================================================================
CREATE TABLE products (
                          product_id SERIAL PRIMARY KEY,
                          name VARCHAR(100) NOT NULL,
                          category VARCHAR(50),
                          unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
                          quantity INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
                          supplier_id INT REFERENCES suppliers(supplier_id) ON DELETE SET NULL,
                          active BOOLEAN NOT NULL DEFAULT true,
                          last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT product_name_not_empty CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE INDEX idx_products_name ON products(name);
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_supplier ON products(supplier_id);
CREATE INDEX idx_products_active ON products(active);

-- Trigger: auto-update last_updated
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

                           CONSTRAINT action_not_empty CHECK (LENGTH(TRIM(action)) > 0)
);

CREATE INDEX idx_audit_log_user ON audit_log(user_id);
CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp);

-- =================================================================
-- TEST DATA
-- =================================================================

INSERT INTO users (username, password, role) VALUES
                                                 ('admin', 'admin123', 'Admin'),
                                                 ('manager', 'manager123', 'Stock Manager'),
                                                 ('cashier', 'cashier123', 'Cashier');

INSERT INTO suppliers (name, contact_info, email, address) VALUES
                                                               ('Coca-Cola Uzbekistan', '+998 71 123 4567', 'info@coca-cola.uz', 'Tashkent, Yunusabad'),
                                                               ('PepsiCo Central Asia', '+998 71 234 5678', 'sales@pepsi.uz', 'Tashkent, Chilanzar'),
                                                               ('Local Dairy Farm', '+998 90 123 4567', 'dairy@farm.uz', 'Tashkent Region');

INSERT INTO products (name, category, unit_price, quantity, supplier_id, active) VALUES
                                                                                     ('Coca-Cola 1.5L', 'Beverages', 8500.00, 100, 1, true),
                                                                                     ('Pepsi 1.5L', 'Beverages', 8000.00, 150, 2, true),
                                                                                     ('Milk 1L', 'Dairy', 12000.00, 50, 3, true),
                                                                                     ('Bread White', 'Bakery', 3500.00, 200, NULL, true),
                                                                                     ('Eggs 10pcs', 'Dairy', 18000.00, 80, 3, true),
                                                                                     ('Fanta Orange 1.5L', 'Beverages', 7500.00, 80, 1, true),
                                                                                     ('Sprite 1.5L', 'Beverages', 7500.00, 80, 1, true),
                                                                                     ('Mineral Water 1.5L', 'Beverages', 2500.00, 200, 1, true),
                                                                                     ('Lay''s Chips Classic 150g', 'Snacks', 12000.00, 60, 2, true),
                                                                                     ('Yogurt Natural 500g', 'Dairy', 9000.00, 30, 3, true);

INSERT INTO transactions (product_id, user_id, txn_type, quantity, total_price, notes) VALUES
                                                                                           (1, 3, 'Sale', 5, 42500.00, 'Cash payment'),
                                                                                           (2, 3, 'Sale', 3, 24000.00, 'Card payment'),
                                                                                           (3, 2, 'Purchase', 100, 1200000.00, 'Wholesale order'),
                                                                                           (4, 3, 'Sale', 10, 35000.00, 'Morning rush');

-- =================================================================
-- VIEWS
-- =================================================================

CREATE OR REPLACE VIEW v_inventory_overview AS
SELECT
    p.product_id,
    p.name AS product_name,
    p.category,
    p.unit_price,
    p.quantity,
    p.unit_price * p.quantity AS total_value,
    COALESCE(s.name, 'No Supplier') AS supplier_name,
    p.active
FROM products p
         LEFT JOIN suppliers s ON p.supplier_id = s.supplier_id
WHERE p.active = true;

CREATE OR REPLACE VIEW v_low_stock_products AS
SELECT
    p.product_id,
    p.name AS product_name,
    p.category,
    p.quantity,
    p.unit_price
FROM products p
WHERE p.active = true AND p.quantity <= 50;