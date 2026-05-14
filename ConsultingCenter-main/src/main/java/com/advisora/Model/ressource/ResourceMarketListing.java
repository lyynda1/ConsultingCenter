package com.advisora.Model.ressource;

import java.sql.Timestamp;

public class ResourceMarketListing {
    private int idListing;
    private int sellerId;
    private String sellerName;
    private int sourceProjectId;
    private String sourceProjectTitle;
    private int idRs;
    private String resourceName;
    private String fournisseurName;
    private int qtyTotal;
    private int qtyAvailable;
    private double unitPrice;
    private String note;
    private String status;
    private String imageUrl;
    private double averageStars;
    private int reviewCount;
    private Timestamp createdAt;

    public int getIdListing() {
        return idListing;
    }

    public void setIdListing(int idListing) {
        this.idListing = idListing;
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

    public int getSourceProjectId() {
        return sourceProjectId;
    }

    public void setSourceProjectId(int sourceProjectId) {
        this.sourceProjectId = sourceProjectId;
    }

    public String getSourceProjectTitle() {
        return sourceProjectTitle;
    }

    public void setSourceProjectTitle(String sourceProjectTitle) {
        this.sourceProjectTitle = sourceProjectTitle;
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

    public String getFournisseurName() {
        return fournisseurName;
    }

    public void setFournisseurName(String fournisseurName) {
        this.fournisseurName = fournisseurName;
    }

    public int getQtyTotal() {
        return qtyTotal;
    }

    public void setQtyTotal(int qtyTotal) {
        this.qtyTotal = qtyTotal;
    }

    public int getQtyAvailable() {
        return qtyAvailable;
    }

    public void setQtyAvailable(int qtyAvailable) {
        this.qtyAvailable = qtyAvailable;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public double getAverageStars() {
        return averageStars;
    }

    public void setAverageStars(double averageStars) {
        this.averageStars = averageStars;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(int reviewCount) {
        this.reviewCount = reviewCount;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}

