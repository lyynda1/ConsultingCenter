package com.advisora.Model.projet;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Top10CompanyItem {
    private int rank;
    private String name;
    @JsonProperty("revenue_usd_billions")
    private double revenueUsdBillions;
    private int year;
    private String description;

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getRevenueUsdBillions() {
        return revenueUsdBillions;
    }

    public void setRevenueUsdBillions(double revenueUsdBillions) {
        this.revenueUsdBillions = revenueUsdBillions;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

