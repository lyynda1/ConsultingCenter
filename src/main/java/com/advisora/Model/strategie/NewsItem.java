package com.advisora.Model.strategie;


public class NewsItem {
    public String title;
    public String url;
    public String source;
    public String date; // optional string

    public NewsItem() {}

    public NewsItem(String title, String url, String source, String date) {
        this.title = title;
        this.url = url;
        this.source = source;
        this.date = date;
    }
}
