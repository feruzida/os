package com.inventory.server;

import com.inventory.model.ActiveClient;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveClientRegistry {

    private static final ConcurrentHashMap<Integer, ActiveClient> clients = new ConcurrentHashMap<>();

    private ActiveClientRegistry() {}

    public static void add(ActiveClient client) {
        clients.put(client.getClientId(), client);
    }

    public static void updateUser(int clientId, String username, String role) {
        ActiveClient client = clients.get(clientId);
        if (client != null) {
            client.setUser(username, role);
        }
    }

    public static void remove(int clientId) {
        clients.remove(clientId);
    }

    public static Collection<ActiveClient> getAll() {
        return clients.values();
    }

    public static int count() {
        return clients.size();
    }
}
