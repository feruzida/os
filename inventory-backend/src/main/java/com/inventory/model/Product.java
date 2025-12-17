package com.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Product model class representing a product in inventory
 */
public class Product {
    private int productId;
    private String name;
    private String category;
    private BigDecimal unitPrice;
    private int quantity;
    private Integer supplierId; // Can be null
    private LocalDateTime lastUpdated;
    private boolean active = true;


    // Constructors
    public Product() {
    }

    public Product(String name, String category, BigDecimal unitPrice, int quantity) {
        this.name = name;
        this.category = category;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public Product(int productId, String name, String category, BigDecimal unitPrice,
                   int quantity, Integer supplierId, LocalDateTime lastUpdated) {
        this.productId = productId;
        this.name = name;
        this.category = category;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.supplierId = supplierId;
        this.lastUpdated = lastUpdated;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    // Getters and Setters
    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Integer getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Integer supplierId) {
        this.supplierId = supplierId;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    // Business methods
    public BigDecimal getTotalValue() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public boolean isInStock() {
        return quantity > 0;
    }

    public boolean isLowStock(int threshold) {
        return quantity <= threshold;
    }

    public void addStock(int amount) {
        this.quantity += amount;
    }

    public void removeStock(int amount) {
        if (amount > this.quantity) {
            throw new IllegalArgumentException("Cannot remove more than available stock");
        }
        this.quantity -= amount;
    }

    @Override
    public String toString() {
        return "Product{" +
                "productId=" + productId +
                ", name='" + name + '\'' +
                ", category='" + category + '\'' +
                ", unitPrice=" + unitPrice +
                ", quantity=" + quantity +
                ", supplierId=" + supplierId +
                ", lastUpdated=" + lastUpdated +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return productId == product.productId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId);
    }
}