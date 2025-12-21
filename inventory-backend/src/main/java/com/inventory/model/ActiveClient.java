package com.inventory.model;

import java.time.LocalDateTime;


/**
 * Represents an active client connected to the socket server
 * Stores connection info and authenticated user details
 */

public class ActiveClient {
    private final int clientId;
    private String username;
    private String role;
    private final String ip;
    private final int port;
    private final LocalDateTime connectedAt;

    public ActiveClient(int clientId, String ip, int port, LocalDateTime connectedAt) {
        this.clientId = clientId;
        this.ip = ip;
        this.port = port;
        this.connectedAt = connectedAt;
    }

    public int getClientId() { return clientId; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getIp() { return ip; }
    public int getPort() { return port; }
    public LocalDateTime getConnectedAt() { return connectedAt; }

    public void setUser(String username, String role) {
        this.username = username;
        this.role = role;
    }
}
