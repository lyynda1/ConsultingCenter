package com.advisora.Model.projet;

public class Top10Request {
    private String rawCategory;

    public Top10Request() {
    }

    public Top10Request(String rawCategory) {
        this.rawCategory = rawCategory;
    }

    public String getRawCategory() {
        return rawCategory;
    }

    public void setRawCategory(String rawCategory) {
        this.rawCategory = rawCategory;
    }
}

