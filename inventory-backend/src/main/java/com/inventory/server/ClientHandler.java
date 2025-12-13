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
import java.time.LocalDateTime;
import java.util.List;

/**
 * Handles individual client connections in separate threads
 * Processes JSON requests and returns JSON responses
 */
public class ClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final Gson gson = JsonUtil.getGson();

    private Socket clientSocket;
    private int clientId;
    private BufferedReader in;
    private PrintWriter out;

    // Handlers
    private UserHandler userHandler;
    private ProductHandler productHandler;
    private SupplierHandler supplierHandler;
    private TransactionHandler transactionHandler;

    // Current authenticated user
    private User authenticatedUser;

    public ClientHandler(Socket socket, int clientId) {
        this.clientSocket = socket;
        this.clientId = clientId;

        // Initialize handlers
        this.userHandler = new UserHandler();
        this.productHandler = new ProductHandler();
        this.supplierHandler = new SupplierHandler();
        this.transactionHandler = new TransactionHandler();
    }

    @Override
    public void run() {
        try {
            // Setup streams
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            logger.info("Client {} handler started", clientId);

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
            logger.error("Client {} connection error", clientId, e);
        } finally {
            disconnect();
        }
    }

    /**
     * Process client request based on action
     */
    private JsonObject processRequest(String action, JsonObject request) {
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

            default:
                return createResponse(false, "Unknown action: " + action, null);
        }
    }

    // ========== USER ACTION HANDLERS ==========

    private JsonObject handleLogin(JsonObject request) {
        String username = request.get("username").getAsString();
        String password = request.get("password").getAsString();

        User user = userHandler.loginUser(username, password);

        if (user != null) {
            authenticatedUser = user;
            logger.info("Client {} authenticated as user '{}'", clientId, username);
            return createResponse(true, "Login successful", user);
        } else {
            return createResponse(false, "Invalid username or password", null);
        }
    }

    private JsonObject handleRegister(JsonObject request) {
        if (!isAdmin()) {
            return createResponse(false, "Only admins can register new users", null);
        }

        User newUser = gson.fromJson(request.get("user"), User.class);
        boolean success = userHandler.registerUser(newUser);

        return createResponse(success,
                success ? "User registered successfully" : "Failed to register user",
                success ? newUser : null);
    }

    private JsonObject handleLogout() {
        if (authenticatedUser != null) {
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

        Product product = gson.fromJson(request.get("product"), Product.class);
        boolean success = productHandler.addProduct(product);

        return createResponse(success,
                success ? "Product added successfully" : "Failed to add product",
                success ? product : null);
    }

    private JsonObject handleUpdateProduct(JsonObject request) {
        if (!isAdminOrManager()) {
            return createResponse(false, "Only admins and managers can update products", null);
        }

        Product product = gson.fromJson(request.get("product"), Product.class);
        boolean success = productHandler.updateProduct(product);

        return createResponse(success,
                success ? "Product updated successfully" : "Failed to update product",
                null);
    }

    private JsonObject handleDeleteProduct(JsonObject request) {
        if (!isAdmin()) {
            return createResponse(false, "Only admins can delete products", null);
        }

        int productId = request.get("product_id").getAsInt();
        boolean success = productHandler.deleteProduct(productId);

        return createResponse(success,
                success ? "Product deleted successfully" : "Failed to delete product",
                null);
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

        return createResponse(success,
                success ? "Supplier deleted successfully" : "Failed to delete supplier",
                null);
    }

    // ========== TRANSACTION ACTION HANDLERS ==========

    private JsonObject handleRecordTransaction(JsonObject request) {
        if (!isAuthenticated()) {
            return createResponse(false, "Not authenticated", null);
        }

        Transaction transaction = gson.fromJson(request.get("transaction"), Transaction.class);
        transaction.setUserId(authenticatedUser.getUserId());

        boolean success = transactionHandler.recordTransaction(transaction);

        return createResponse(success,
                success ? "Transaction recorded successfully" : "Failed to record transaction",
                success ? transaction : null);
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
}