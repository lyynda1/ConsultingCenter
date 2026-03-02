package com.advisora.Model.ressource;

import java.sql.Timestamp;

public class ResourceMarketOrder {
    private int idOrder;
    private int idListing;
    private int buyerId;
    private String buyerName;
    private int sellerId;
    private String sellerName;
    private int idRs;
    private String resourceName;
    private int qty;
    private double unitPrice;
    private double totalAmount;
    private String status;
    private String deliveryStatus;
    private String deliveryTrackingCode;
    private String deliveryLabelUrl;
    private int targetProjectId;
    private String targetProjectTitle;
    private int reviewStars;
    private String reviewComment;
    private Timestamp createdAt;

    public int getIdOrder() {
        return idOrder;
    }

    public void setIdOrder(int idOrder) {
        this.idOrder = idOrder;
    }

    public int getIdListing() {
        return idListing;
    }

    public void setIdListing(int idListing) {
        this.idListing = idListing;
    }

    public int getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(int buyerId) {
        this.buyerId = buyerId;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public int getSellerId() {
        return sellerId;
    }

    public void setSellerId(int sellerId) {
        this.sellerId = sellerId;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public int getIdRs() {
        return idRs;
    }

    public void setIdRs(int idRs) {
        this.idRs = idRs;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public String getDeliveryTrackingCode() {
        return deliveryTrackingCode;
    }

    public void setDeliveryTrackingCode(String deliveryTrackingCode) {
        this.deliveryTrackingCode = deliveryTrackingCode;
    }

    public String getDeliveryLabelUrl() {
        return deliveryLabelUrl;
    }

    public void setDeliveryLabelUrl(String deliveryLabelUrl) {
        this.deliveryLabelUrl = deliveryLabelUrl;
    }

    public int getTargetProjectId() {
        return targetProjectId;
    }

    public void setTargetProjectId(int targetProjectId) {
        this.targetProjectId = targetProjectId;
    }

    public String getTargetProjectTitle() {
        return targetProjectTitle;
    }

    public void setTargetProjectTitle(String targetProjectTitle) {
        this.targetProjectTitle = targetProjectTitle;
    }

    public int getReviewStars() {
        return reviewStars;
    }

    public void setReviewStars(int reviewStars) {
        this.reviewStars = reviewStars;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
