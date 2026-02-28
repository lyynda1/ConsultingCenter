package com.advisora.Model.projet;

import java.util.ArrayList;
import java.util.List;

public class Top10Response {
    private String category;
    private List<Top10CompanyItem> top10 = new ArrayList<>();
    private String disclaimer;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<Top10CompanyItem> getTop10() {
        return top10;
    }

    public void setTop10(List<Top10CompanyItem> top10) {
        this.top10 = top10;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }
}
