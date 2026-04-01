# All Store Stock Management (Inventory Control)

Linux-based multi-user inventory control system with **client-server architecture**, **real-time synchronization**, **role-based access**, and **PostgreSQL-backed transaction processing**.

This project was developed as an Operating Systems term project and demonstrates practical implementation of:
- TCP socket communication
- concurrent multi-client handling
- centralized business logic
- persistent relational data storage
- real-time stock synchronization
- audit logging and reporting

---

# 🚀 Key Features

- **User Authentication & Authorization**
  - Admin
  - Stock Manager
  - Cashier

- **Inventory Management**
  - Add / update / delete products
  - automatic quantity tracking
  - stock status updates

- **Supplier Management**
  - supplier records
  - product–supplier linking

- **Transaction Processing**
  - sales
  - purchases
  - timestamped logs
  - total price calculation

- **Reporting Dashboard**
  - daily reports
  - weekly reports
  - monthly reports
  - stock movement summaries

- **Real-Time Synchronization**
  - all connected clients instantly receive updated stock state

- **Audit Logging**
  - every critical action is stored for traceability

- **Connection Monitoring**
  - server-side display of connected clients (IP + port)

---

# 🏗️ System Architecture

```text
Electron Client GUI
        ↓
 TCP Socket Communication
        ↓
 Java Socket Server
        ↓
 Java Business Logic Layer
        ↓
 PostgreSQL Database
 Components
Frontend
Electron.js
HTML / CSS / JavaScript
Linux desktop GUI
Backend
Pure Java
TCP sockets
concurrent client handling
JDBC
transaction management
Database
PostgreSQL
🗃️ Database Schema

Core entities:

users
products
suppliers
transactions
audit_log
Example Tables
users(user_id, username, password, role)
products(product_id, name, quantity, unit_price)
transactions(txn_id, product_id, txn_type, quantity, txn_date)
audit_log(log_id, user_id, action, timestamp)
⚙️ Tech Stack
Java (OpenJDK 17)
Electron.js
PostgreSQL
JDBC
TCP Sockets
Linux
▶️ How to Run
1) Clone repository
git clone https://github.com/your-username/all-store-stock-management.git
cd all-store-stock-management
2) Start PostgreSQL

Create database:

CREATE DATABASE inventory_db;

Run schema:

psql -U postgres -d inventory_db -f database/schema.sql
3) Run Backend
cd backend
javac MainServer.java
java MainServer
4) Run Frontend
cd frontend
npm install
npm start
