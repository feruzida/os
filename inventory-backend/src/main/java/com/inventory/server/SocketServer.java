package com.inventory.server;


import com.inventory.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-threaded Socket Server for Inventory Management System
 * Handles multiple client connections concurrently
 */
public class SocketServer {
    private static final Logger logger = LoggerFactory.getLogger(SocketServer.class);
    private static final int PORT = 8080;
    private static final int MAX_CLIENTS = 50;

    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private AtomicInteger clientCounter = new AtomicInteger(0);
    private volatile boolean running = false;

    public SocketServer() {
        this.executorService = Executors.newFixedThreadPool(MAX_CLIENTS);
    }

    /**
     * Start the socket server
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            running = true;

            logger.info("=================================================");
            logger.info("   INVENTORY SOCKET SERVER STARTED");
            logger.info("=================================================");
            logger.info("Port: {}", PORT);
            logger.info("Max Clients: {}", MAX_CLIENTS);
            logger.info("Waiting for client connections...");
            logger.info("=================================================");

            // Test database connection
            if (!DatabaseConnection.testConnection()) {
                logger.error("Database connection failed! Server cannot start.");
                return;
            }
            logger.info("Database connection: OK");

            // Accept client connections
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    int clientId = clientCounter.incrementAndGet();

                    logger.info("New client connected: ID={}, IP={}, Port={}",
                            clientId,
                            clientSocket.getInetAddress().getHostAddress(),
                            clientSocket.getPort());

                    // Handle client in separate thread
                    ClientHandler clientHandler = new ClientHandler(clientSocket, clientId);
                    executorService.submit(clientHandler);

                } catch (IOException e) {
                    if (running) {
                        logger.error("Error accepting client connection", e);
                    }
                }
            }

        } catch (IOException e) {
            logger.error("Failed to start server on port {}", PORT, e);
        } finally {
            stop();
        }
    }

    /**
     * Stop the socket server
     */
    public void stop() {
        running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                logger.info("Server socket closed");
            }
        } catch (IOException e) {
            logger.error("Error closing server socket", e);
        }

        executorService.shutdown();
        logger.info("Executor service shutdown");

        DatabaseConnection.closePool();
        logger.info("Database connection pool closed");

        logger.info("=================================================");
        logger.info("   SERVER STOPPED");
        logger.info("=================================================");
    }

    /**
     * Get number of active clients
     */
    public int getActiveClients() {
        return clientCounter.get();
    }

    /**
     * Main method to start the server
     */
    public static void main(String[] args) {
        SocketServer server = new SocketServer();

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received");
            server.stop();
        }));

        // Start server
        server.start();
    }
}
