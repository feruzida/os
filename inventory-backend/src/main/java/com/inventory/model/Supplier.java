package com.inventory.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Supplier model class representing a supplier in the system
 */
public class Supplier {
    private int supplierId;
    private String name;
    private String contactInfo;
    private String email;
    private String address;
    private LocalDateTime createdAt;
    private boolean active;
    private int productCount; // Derived field, not stored in database

    // Constructors
    public Supplier() {
    }

    public Supplier(String name, String contactInfo) {
        this.name = name;
        this.contactInfo = contactInfo;
    }

    public Supplier(String name, String contactInfo, String email, String address) {
        this.name = name;
        this.contactInfo = contactInfo;
        this.email = email;
        this.address = address;
    }

    public Supplier(int supplierId, String name, String contactInfo, String email,
                    String address, LocalDateTime createdAt) {
        this.supplierId = supplierId;
        this.name = name;
        this.contactInfo = contactInfo;
        this.email = email;
        this.address = address;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public int getSupplierId() {
        return supplierId;
    }
    public int getProductCount() {
        return productCount;
    }
    public void setProductCount(int productCount) {
        this.productCount = productCount;
    }

    public void setSupplierId(int supplierId) {
        this.supplierId = supplierId;
    }
    public void setActive(boolean active) {
        this.active = active;
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Supplier{" +
                "supplierId=" + supplierId +
                ", name='" + name + '\'' +
                ", contactInfo='" + contactInfo + '\'' +
                ", email='" + email + '\'' +
                ", address='" + address + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Supplier supplier = (Supplier) o;
        return supplierId == supplier.supplierId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(supplierId);
    }
}