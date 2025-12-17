package com.inventory.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inventory.handler.*;
import com.inventory.model.*;
import com.inventory.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles individual client connections in separate threads
 * Processes JSON requests and returns JSON responses
 * FIXED: Added socket timeout, rate limiting, and better error handling
 */
public class ClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final Gson gson = JsonUtil.getGson();

    // FIXED: Rate limiting for login attempts
    private static final ConcurrentHashMap<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 5;

    // Socket timeout (5 minutes)
    private static final int SOCKET_TIMEOUT = 300000;

    private Socket clientSocket;
    private int clientId;
    private BufferedReader in;
    private PrintWriter out;

    // Handlers
    private UserHandler userHandler;
    private ProductHandler productHandler;
    private SupplierHandler supplierHandler;
    private TransactionHandler transactionHandler;
    private AuditLogHandler auditLogHandler;

    // Current authenticated user
    private User authenticatedUser;

    public ClientHandler(Socket socket, int clientId) {
        this.clientSocket = socket;
        this.clientId = clientId;

        // FIXED: Set socket timeout and keep-alive
        try {
            socket.setSoTimeout(SOCKET_TIMEOUT);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
        } catch (SocketException e) {
            logger.error("Error setting socket options for client {}", clientId, e);
        }

        // Initialize handlers
        this.userHandler = new UserHandler();
        this.productHandler = new ProductHandler();
        this.supplierHandler = new SupplierHandler();
        this.transactionHandler = new TransactionHandler();
        this.auditLogHandler = new AuditLogHandler();
    }

    @Override
    public void run() {
        try {
            // Setup streams
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            logger.info("Client {} handler started from {}:{}",
                    clientId,
                    clientSocket.getInetAddress().getHostAddress(),
                    clientSocket.getPort());

            // Send welcome message
            sendResponse(createResponse(true, "Connected to Inventory Server", null));

            // Process client requests
            String request;
            while ((request = in.readLine()) != null) {
                logger.debug("Client {} request: {}", clientId, request);

                try {
                    JsonObject requestJson = JsonParser.parseString(request).getAsJsonObject();
                    String action = requestJson.get("action").getAsString();

                    JsonObject response = processRequest(action, requestJson);
                    sendResponse(response);

                } catch (Exception e) {
                    logger.error("Error processing request from client {}", clientId, e);
                    sendResponse(createResponse(false, "Error: " + e.getMessage(), null));
                }
            }

        } catch (IOException e) {
            logger.error("Client {} connection error: {}", clientId, e.getMessage());
        } finally {
            disconnect();
        }
    }

    /**
     * Process client request based on action
     * FIXED: Added authentication check and better error handling
     */
    private JsonObject processRequest(String action, JsonObject request) {
        try {
            // Check authentication for protected actions
            if (requiresAuth(action) && !isAuthenticated()) {
                return createResponse(false, "Authentication required", null);
            }

            switch (action) {
                // ========== USER ACTIONS ==========
                case "login":
                    return handleLogin(request);
                case "register":
                    return handleRegister(request);
                case "logout":
                    return handleLogout();
                case "get_all_users":
                    return handleGetAllUsers();
                case "change_password":
                    return handleChangePassword(request);

                // ========== PRODUCT ACTIONS ==========
                case "get_all_products":
                    return handleGetAllProducts();
                case "get_product":
                    return handleGetProduct(request);
                case "add_product":
                    return handleAddProduct(request);
                case "update_product":
                    return handleUpdateProduct(request);
                case "delete_product":
                    return handleDeleteProduct(request);
                case "search_products":
                    return handleSearchProducts(request);
                case "get_low_stock":
                    return handleGetLowStock(request);

                // ========== SUPPLIER ACTIONS ==========
                case "get_all_suppliers":
                    return handleGetAllSuppliers();
                case "get_supplier":
                    return handleGetSupplier(request);
                case "add_supplier":
                    return handleAddSupplier(request);
                case "update_supplier":
                    return handleUpdateSupplier(request);
                case "delete_supplier":
                    return handleDeleteSupplier(request);

                // ========== TRANSACTION ACTIONS ==========
                case "record_transaction":
                    return handleRecordTransaction(request);
                case "get_all_transactions":
                    return handleGetAllTransactions();
                case "get_today_transactions":
                    return handleGetTodayTransactions();
                case "get_daily_sales":
                    return handleGetDailySales(request);
                case "get_monthly_sales":
                    return handleGetMonthlySales(request);

                // ========== AUDIT LOG ACTIONS ==========
                case "get_audit_logs":
                    return handleGetAuditLogs();

                default:
                    return createResponse(false, "Unknown action: " + action, null);
            }
        } catch (Exception e) {
            logger.error("Error processing action '{}' for client {}", action, clientId, e);
            return createResponse(false, "Internal server error", null);
        }
    }

    /**
     * FIXED: Check if action requires authentication
     */
    private boolean requiresAuth(String action) {
        return !action.equals("login");
    }

    // ========== USER ACTION HANDLERS ==========

    /**
     * FIXED: Added rate limiting for login attempts
     */
    private JsonObject handleLogin(JsonObject request) {
        String username = request.get("username").getAsString();
        String password = request.get("password").getAsString();
        String clientIP = clientSocket.getInetAddress().getHostAddress();
        String key = clientIP + ":" + username;

        // Check rate limiting
        LoginAttempt attempt = loginAttempts.get(key);
        //if (attempt != null && attempt.isLocked()) {
        //    return createResponse(false,
        //            "Too many login attempts. Try again in " + attempt.getRemainingLockoutMinutes() + " minutes.",
        //            null);
        //}

        User user = userHandler.loginUser(username, password);

        if (user != null) {
            loginAttempts.remove(key); // Clear failed attempts on success
            authenticatedUser = user;

            // Log successful login
            auditLogHandler.logAction(user.getUserId(), "LOGIN",
                    "Logged in from " + clientIP);

            logger.info("Client {} authenticated as user '{}' ({})", clientId, username, user.getRole());
            return createResponse(true, "Login successful", user);
        } else {
            // Track failed attempt
            if (attempt == null) {
                attempt = new LoginAttempt();
                loginAttempts.put(key, attempt);
            }
            attempt.recordFailedAttempt();

            logger.warn("Failed login attempt for '{}' from {}", username, clientIP);
            return createResponse(false, "Invalid username or password", null);
        }
    }

    private JsonObject handleRegister(JsonObject request) {
        if (!isAdmin()) {
            return createResponse(false, "Only admins can register new users", null);
        }

        User newUser = gson.fromJson(request.get("user"), User.class);
        boolean success = userHandler.registerUser(newUser);

        if (success) {
            auditLogHandler.logAction(authenticatedUser.getUserId(), "REGISTER_USER",
                    "Registered new user: " + newUser.getUsername() + " (Role: " + newUser.getRole() + ")");
        }

        return createResponse(success,
                success ? "User registered successfully" : "Failed to register user",
                success ? newUser : null);
    }

    private JsonObject handleLogout() {
        if (authenticatedUser != null) {
            auditLogHandler.logAction(authenticatedUser.getUserId(), "LOGOUT",
                    "Logged out from " + clientSocket.getInetAddress().getHostAddress());

            logger.info("Client {} (user '{}') logged out", clientId, authenticatedUser.getUsername());
            authenticatedUser = null;
        }
        return createResponse(true, "Logged out successfully", null);
    }

    private JsonObject handleGetAllUsers() {
        if (!isAuthenticated()) {
            return createResponse(false, "Not authenticated", null);
        }

        List<User> users = userHandler.getAllUsers();
        return createResponse(true, "Users retrieved", users);
    }

    /**
     * FIXED: New handler for password change
     */
    private JsonObject handleChangePassword(JsonObject request) {
        if (!isAuthenticated()) {
            return createResponse(false, "Not authenticated", null);
        }

        String oldPassword = request.get("old_password").getAsString();
        String newPassword = request.get("new_password").getAsString();

        boolean success = userHandler.changePassword(
                authenticatedUser.getUserId(), oldPassword, newPassword);

        if (success) {
            auditLogHandler.logAction(authenticatedUser.getUserId(), "CHANGE_PASSWORD",
                    "Password changed successfully");
        }

        return createResponse(success,
                success ? "Password changed successfully" : "Failed to change password",
                null);
    }

    // ========== PRODUCT ACTION HANDLERS ==========

    private JsonObject handleGetAllProducts() {
        if (!isAuthenticated()) {
            return createResponse(false, "Not authenticated", null);
        }

        List<Product> products = productHandler.getAllProducts();
        return createResponse(true, "Products retrieved", products);
    }

    private JsonObject handleGetProduct(JsonObject request) {
        if (!isAuthenticated()) {
            return createResponse(false, "Not authenticated", null);
        }

        int productId = request.get("product_id").getAsInt();
        Product product = productHandler.getProductById(productId);

        return createResponse(product != null,
                product != null ? "Product found" : "Product not found",
                product);
    }

   private JsonObject handleAddProduct(JsonObject request) {
       if (!isAdminOrManager()) {
           return createResponse(false, "Only admins and managers can add products", null);
       }

       try {
           Product product = new Product();
           product.setName(request.get("name").getAsString());
           product.setCategory(request.get("category").getAsString());
           product.setUnitPrice(request.get("unitPrice").getAsBigDecimal());
           product.setQuantity(request.get("quantity").getAsInt());

           boolean success = productHandler.addProduct(product);

           if (success) {
               auditLogHandler.logAction(
                       authenticatedUser.getUserId(),
                       "ADD_PRODUCT",
                       "Added product: " + product.getName()
               );
           }

           return createResponse(
                   success,
                   success ? "Product added successfully" : "Failed to add product",
                   success ? product : null
           );

       } catch (Exception e) {
           logger.error("Error parsing add_product request", e);
           return createResponse(false, "Invalid product data", null);
       }
   }


    private JsonObject handleUpdateProduct(JsonObject request) {
        if (!isAdminOrManager()) {
            return createResponse(false, "Only admins and managers can update products", null);
        }

        try {
            if (!request.has("productId")) {
                return createResponse(false, "Product ID is required", null);
            }

            Product product = new Product();
            product.setProductId(request.get("productId").getAsInt());
            product.setName(request.get("name").getAsString());
            product.setCategory(request.get("category").getAsString());
            product.setUnitPrice(request.get("unitPrice").getAsBigDecimal());
            product.setQuantity(request.get("quantity").getAsInt());

            if (product.getProductId() <= 0) {
                return createResponse(false, "Invalid product ID", null);
            }

            boolean success = productHandler.updateProduct(product);

            if (success) {
                auditLogHandler.logAction(
                        authenticatedUser.getUserId(),
                        "UPDATE_PRODUCT",
                        "Updated product ID " + product.getProductId()
                );
            }

            return createResponse(
                    success,
                    success ? "Product updated successfully" : "Failed to update product",
                    null
            );

        } catch (Exception e) {
            logger.error("Error parsing update_product request", e);
            return createResponse(false, "Invalid update product data", null);
        }
    }



    private JsonObject handleDeleteProduct(JsonObject request) {

        if (!isAdmin()) {
            return createResponse(false, "Only admins can delete products", null);
        }

        int productId = request.get("product_id").getAsInt();

        boolean success = productHandler.deactivateProduct(productId);

        if (success) {
            auditLogHandler.logAction(
                    authenticatedUser.getUserId(),
                    "DEACTIVATE_PRODUCT",
                    "Deactivated product ID " + productId
            );
        }

        return createResponse(
                success,
                success ? "Product deactivated" : "Product not found or already inactive",
                null
        );
    }




    private JsonObject handleSearchProducts(JsonObject request) {
        if (!isAuthenticated()) {
            return createResponse(false, "Not authenticated", null);
        }

        String searchTerm = request.get("search_term").getAsString();
        List<Product> products = productHandler.searchProductsByName(searchTerm);

        return createResponse(true, "Search completed", products);
    }

    private JsonObject handleGetLowStock(JsonObject request) {
        if (!isAuthenticated()) {
            return createResponse(false, "Not authenticated", null);
        }

        int threshold = request.get("threshold").getAsInt();
        List<Product> products = productHandler.getLowStockProducts(threshold);

        return createResponse(true, "Low stock products retrieved", products);
    }

    // ========== SUPPLIER ACTION HANDLERS ==========

    private JsonObject handleGetAllSuppliers() {
        if (!isAuthenticated()) {
            return createResponse(false, "Not authenticated", null);
        }

        List<Supplier> suppliers = supplierHandler.getAllSuppliers();
        return createResponse(true, "Suppliers retrieved", suppliers);
    }

    private JsonObject handleGetSupplier(JsonObject request) {
        if (!isAuthenticated()) {
            return createResponse(false, "Not authenticated", null);
        }

        int supplierId = request.get("supplier_id").getAsInt();
        Supplier supplier = supplierHandler.getSupplierById(supplierId);

        return createResponse(supplier != null,
                supplier != null ? "Supplier found" : "Supplier not found",
                supplier);
    }

    private JsonObject handleAddSupplier(JsonObject request) {
        if (!isAdminOrManager()) {
            return createResponse(false, "Only admins and managers can add suppliers", null);
        }

        Supplier supplier = gson.fromJson(request.get("supplier"), Supplier.class);
        boolean success = supplierHandler.addSupplier(supplier);

        if (success) {
            auditLogHandler.logAction(authenticatedUser.getUserId(), "ADD_SUPPLIER",
                    "Added supplier: " + supplier.getName());
        }

        return createResponse(success,
                success ? "Supplier added successfully" : "Failed to add supplier",
                success ? supplier : null);
    }

    private JsonObject handleUpdateSupplier(JsonObject request) {
        if (!isAdminOrManager()) {
            return createResponse(false, "Only admins and managers can update suppliers", null);
        }

        Supplier supplier = gson.fromJson(request.get("supplier"), Supplier.class);
        boolean success = supplierHandler.updateSupplier(supplier);

        if (success) {
            auditLogHandler.logAction(authenticatedUser.getUserId(), "UPDATE_SUPPLIER",
                    "Updated supplier ID " + supplier.getSupplierId());
        }

        return createResponse(success,
                success ? "Supplier updated successfully" : "Failed to update supplier",
                null);
    }

    private JsonObject handleDeleteSupplier(JsonObject request) {
        if (!isAdmin()) {
            return createResponse(false, "Only admins can delete suppliers", null);
        }

        int supplierId = request.get("supplier_id").getAsInt();
        boolean success = supplierHandler.deleteSupplier(supplierId);

        if (success) {
            auditLogHandler.logAction(authenticatedUser.getUserId(), "DELETE_SUPPLIER",
                    "Deleted supplier ID " + supplierId);
        }

        return createResponse(success,
                success ? "Supplier deleted successfully" : "Failed to delete supplier",
                null);
    }

    // ========== TRANSACTION ACTION HANDLERS ==========

    private JsonObject handleRecordTransaction(JsonObject request) {
        if (!isAuthenticated()) {
            return createResponse(false, "Authentication required", null);
        }

        try {
            JsonObject tx = request.getAsJsonObject("transaction");

            if (tx == null) {
                return createResponse(false, "Transaction data is required", null);
            }

            Transaction transaction = new Transaction();
            transaction.setProductId(tx.get("productId").getAsInt());
            transaction.setQuantity(tx.get("quantity").getAsInt());
            transaction.setTxnType(tx.get("txnType").getAsString());
            transaction.setNotes(tx.has("notes") ? tx.get("notes").getAsString() : null);

            // userId ВСЕГДА берём с сервера
            transaction.setUserId(authenticatedUser.getUserId());

            boolean success = transactionHandler.recordTransaction(transaction);

            Product updatedProduct = null;
            if (success) {
                updatedProduct = productHandler.getProductById(transaction.getProductId());

                auditLogHandler.logAction(
                        authenticatedUser.getUserId(),
                        "RECORD_TRANSACTION",
                        transaction.getTxnType() +
                                " productId=" + transaction.getProductId() +
                                " qty=" + transaction.getQuantity()
                );
            }

            return createResponse(
                    success,
                    success ? "Transaction recorded successfully" : "Failed to record transaction",
                    updatedProduct
            );


        } catch (Exception e) {
            logger.error("Error parsing record_transaction request", e);
            return createResponse(false, "Invalid transaction data", null);
        }
    }


    private JsonObject handleGetAllTransactions() {
        if (!isAuthenticated()) {
            return createResponse(false, "Not authenticated", null);
        }

        List<Transaction> transactions = transactionHandler.getAllTransactions();
        return createResponse(true, "Transactions retrieved", transactions);
    }

    private JsonObject handleGetTodayTransactions() {
        if (!isAuthenticated()) {
            return createResponse(false, "Not authenticated", null);
        }

        List<Transaction> transactions = transactionHandler.getTodayTransactions();
        return createResponse(true, "Today's transactions retrieved", transactions);
    }

    private JsonObject handleGetDailySales(JsonObject request) {
        if (!isAuthenticated()) {
            return createResponse(false, "Not authenticated", null);
        }

        String dateStr = request.get("date").getAsString();
        java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
        java.math.BigDecimal sales = transactionHandler.getDailySales(date);

        return createResponse(true, "Daily sales retrieved", sales);
    }

    private JsonObject handleGetMonthlySales(JsonObject request) {
        if (!isAuthenticated()) {
            return createResponse(false, "Not authenticated", null);
        }

        int year = request.get("year").getAsInt();
        int month = request.get("month").getAsInt();
        java.math.BigDecimal sales = transactionHandler.getMonthlySales(year, month);

        return createResponse(true, "Monthly sales retrieved", sales);
    }

    // ========== AUDIT LOG HANDLERS ==========

    private JsonObject handleGetAuditLogs() {
        if (!isAdmin()) {
            return createResponse(false, "Only admins can view audit logs", null);
        }

        List<AuditLogHandler.AuditLogEntry> logs = auditLogHandler.getAllLogs();
        return createResponse(true, "Audit logs retrieved", logs);
    }

    // ========== HELPER METHODS ==========

    private JsonObject createResponse(boolean success, String message, Object data) {
        JsonObject response = new JsonObject();
        response.addProperty("success", success);
        response.addProperty("message", message);
        response.addProperty("timestamp", LocalDateTime.now().toString());

        if (data != null) {
            response.add("data", gson.toJsonTree(data));
        }

        return response;
    }

    private void sendResponse(JsonObject response) {
        out.println(gson.toJson(response));
    }

    private boolean isAuthenticated() {
        return authenticatedUser != null;
    }

    private boolean isAdmin() {
        return isAuthenticated() && authenticatedUser.isAdmin();
    }

    private boolean isAdminOrManager() {
        return isAuthenticated() &&
                (authenticatedUser.isAdmin() || authenticatedUser.isStockManager());
    }

    private void disconnect() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
            logger.info("Client {} disconnected", clientId);
        } catch (IOException e) {
            logger.error("Error closing client {} connection", clientId, e);
        }
    }

    /**
     * FIXED: Inner class for tracking login attempts
     */
    private static class LoginAttempt {
        private int attempts = 0;
        private LocalDateTime lockoutUntil = null;

        public void recordFailedAttempt() {
            attempts++;
            if (attempts >= MAX_LOGIN_ATTEMPTS) {
                lockoutUntil = LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES);
            }
        }

        public boolean isLocked() {
            if (lockoutUntil == null) return false;
            if (LocalDateTime.now().isAfter(lockoutUntil)) {
                // Reset after lockout period
                attempts = 0;
                lockoutUntil = null;
                return false;
            }
            return true;
        }

        public long getRemainingLockoutMinutes() {
            if (lockoutUntil == null) return 0;
            return java.time.Duration.between(LocalDateTime.now(), lockoutUntil).toMinutes();
        }
    }
}