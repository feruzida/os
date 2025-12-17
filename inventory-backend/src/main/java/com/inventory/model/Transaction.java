package com.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Transaction model class representing a sale or purchase transaction
 */
public class Transaction {
    private int txnId;
    private int productId;
    private int userId;
    private String txnType; // "Sale" or "Purchase"
    private int quantity;
    private BigDecimal totalPrice;
    private LocalDateTime txnDate;
    private String notes;

    // Constructors
    public Transaction() {
    }

    public Transaction(int productId, int userId, String txnType, int quantity, BigDecimal totalPrice) {
        this.productId = productId;
        this.userId = userId;
        this.txnType = txnType;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
    }

    public Transaction(int txnId, int productId, int userId, String txnType,
                       int quantity, BigDecimal totalPrice, LocalDateTime txnDate, String notes) {
        this.txnId = txnId;
        this.productId = productId;
        this.userId = userId;
        this.txnType = txnType;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.txnDate = txnDate;
        this.notes = notes;
    }

    // Getters and Setters
    public int getTxnId() {
        return txnId;
    }

    public void setTxnId(int txnId) {
        this.txnId = txnId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public LocalDateTime getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(LocalDateTime txnDate) {
        this.txnDate = txnDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    // Business methods
    public boolean isSale() {
        return "Sale".equalsIgnoreCase(txnType);
    }

    public boolean isPurchase() {
        return "Purchase".equalsIgnoreCase(txnType);
    }


    public BigDecimal getUnitPrice() {
        return totalPrice.divide(BigDecimal.valueOf(quantity), 2, java.math.RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "txnId=" + txnId +
                ", productId=" + productId +
                ", userId=" + userId +
                ", txnType='" + txnType + '\'' +
                ", quantity=" + quantity +
                ", totalPrice=" + totalPrice +
                ", txnDate=" + txnDate +
                ", notes='" + notes + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return txnId == that.txnId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(txnId);
    }
}