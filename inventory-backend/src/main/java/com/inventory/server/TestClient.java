package com.inventory.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * Simple test client for Socket Server
 */
public class TestClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8080;
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("=================================================");
            System.out.println("  INVENTORY SYSTEM TEST CLIENT");
            System.out.println("=================================================");
            System.out.println("Connected to server: " + SERVER_HOST + ":" + SERVER_PORT);

            // Read welcome message
            String welcome = in.readLine();
            System.out.println("Server: " + welcome);
            System.out.println();

            // Test 1: Login
            System.out.println("TEST 1: Login");
            JsonObject loginRequest = new JsonObject();
            loginRequest.addProperty("action", "login");
            loginRequest.addProperty("username", "admin");
            loginRequest.addProperty("password", "admin123");

            out.println(gson.toJson(loginRequest));
            String loginResponse = in.readLine();
            System.out.println("Response: " + loginResponse);
            System.out.println();

            // Test 2: Get all products
            System.out.println("TEST 2: Get all products");
            JsonObject productsRequest = new JsonObject();
            productsRequest.addProperty("action", "get_all_products");

            out.println(gson.toJson(productsRequest));
            String productsResponse = in.readLine();
            System.out.println("Response: " + productsResponse);
            System.out.println();

            // Test 3: Get all suppliers
            System.out.println("TEST 3: Get all suppliers");
            JsonObject suppliersRequest = new JsonObject();
            suppliersRequest.addProperty("action", "get_all_suppliers");

            out.println(gson.toJson(suppliersRequest));
            String suppliersResponse = in.readLine();
            System.out.println("Response: " + suppliersResponse);
            System.out.println();

            // Test 4: Get today's transactions
            System.out.println("TEST 4: Get today's transactions");
            JsonObject txnRequest = new JsonObject();
            txnRequest.addProperty("action", "get_today_transactions");

            out.println(gson.toJson(txnRequest));
            String txnResponse = in.readLine();
            System.out.println("Response: " + txnResponse);
            System.out.println();

            // Interactive mode
            System.out.println("=================================================");
            System.out.println("  INTERACTIVE MODE");
            System.out.println("=================================================");
            System.out.println("Enter JSON requests (or 'exit' to quit):");
            System.out.println("Example: {\"action\":\"get_all_users\"}");
            System.out.println();

            String input;
            while (true) {
                System.out.print("> ");
                input = scanner.nextLine();

                if (input.equalsIgnoreCase("exit")) {
                    break;
                }

                out.println(input);
                String response = in.readLine();
                System.out.println("Response: " + response);
                System.out.println();
            }

            System.out.println("Disconnected from server");

        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
        }
    }
}