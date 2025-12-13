package com.inventory.util;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JSON utility class for serializing and deserializing objects
 * Used by Socket Server for client-server communication
 */
public class JsonUtil {
    private static final Logger logger = LoggerFactory.getLogger(JsonUtil.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            .create();

    /**
     * Custom TypeAdapter for LocalDateTime
     */
    private static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        @Override
        public JsonElement serialize(LocalDateTime dateTime, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(dateTime.format(formatter));
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type type, JsonDeserializationContext context)
                throws JsonParseException {
            return LocalDateTime.parse(json.getAsString(), formatter);
        }
    }

    /**
     * Get the configured Gson instance
     */
    public static Gson getGson() {
        return gson;
    }

    /**
     * Convert object to JSON string
     * @param object Object to convert
     * @return JSON string representation
     */
    public static String toJson(Object object) {
        try {
            return gson.toJson(object);
        } catch (Exception e) {
            logger.error("Error converting object to JSON", e);
            return null;
        }
    }

    /**
     * Convert JSON string to object
     * @param json JSON string
     * @param classOfT Target class type
     * @return Deserialized object or null if error
     */
    public static <T> T fromJson(String json, Class<T> classOfT) {
        try {
            return gson.fromJson(json, classOfT);
        } catch (JsonSyntaxException e) {
            logger.error("Error parsing JSON to {}", classOfT.getSimpleName(), e);
            return null;
        }
    }

    /**
     * Convert JSON string to object (with Type parameter for generic types)
     * @param json JSON string
     * @param typeOfT Target type
     * @return Deserialized object or null if error
     */
    public static <T> T fromJson(String json, Type typeOfT) {
        try {
            return gson.fromJson(json, typeOfT);
        } catch (JsonSyntaxException e) {
            logger.error("Error parsing JSON to type {}", typeOfT, e);
            return null;
        }
    }

    /**
     * Check if string is valid JSON
     * @param json String to check
     * @return true if valid JSON
     */
    public static boolean isValidJson(String json) {
        try {
            gson.fromJson(json, Object.class);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    // ========== TESTING METHOD ==========
    public static void main(String[] args) {
        System.out.println("=== Testing JsonUtil ===\n");

        // Test 1: Object to JSON
        System.out.println("1. Converting User to JSON:");
        com.inventory.model.User user = new com.inventory.model.User(1, "admin", "admin123", "Admin",
                java.time.LocalDateTime.now());
        String userJson = toJson(user);
        System.out.println(userJson);

        // Test 2: JSON to Object
        System.out.println("\n2. Converting JSON to User:");
        String jsonString = "{\"userId\":1,\"username\":\"test\",\"password\":\"test123\",\"role\":\"Cashier\"}";
        com.inventory.model.User parsedUser = fromJson(jsonString, com.inventory.model.User.class);
        System.out.println(parsedUser);

        // Test 3: Product to JSON
        System.out.println("\n3. Converting Product to JSON:");
        com.inventory.model.Product product = new com.inventory.model.Product(
                1, "Coca-Cola", "Beverages",
                new java.math.BigDecimal("8500.00"), 100, 1,
                java.time.LocalDateTime.now()
        );
        String productJson = toJson(product);
        System.out.println(productJson);

        // Test 4: Validate JSON
        System.out.println("\n4. Validating JSON:");
        System.out.println("Valid JSON: " + isValidJson(jsonString));
        System.out.println("Invalid JSON: " + isValidJson("{invalid json}"));

        // Test 5: Example socket message format
        System.out.println("\n5. Example Socket Request/Response:");
        String requestJson = "{\"action\":\"login\",\"username\":\"admin\",\"password\":\"admin123\"}";
        String responseJson = "{\"success\":true,\"message\":\"Login successful\",\"user\":" + userJson + "}";
        System.out.println("Request:  " + requestJson);
        System.out.println("Response: " + responseJson);

        System.out.println("\n=== Test completed ===");
    }
}