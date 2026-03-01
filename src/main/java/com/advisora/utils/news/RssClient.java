package com.advisora.utils.news;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class RssClient {
    public static class RssResult {
        public int count;
        public List<String> titles = new ArrayList<>();
    }

    public RssResult fetchMosaiqueLatest() throws Exception {
        String feedUrl = "http://www.mosaiquefm.net/fr/rss"; // :contentReference[oaicite:8]{index=8}
        SyndFeed feed = new SyndFeedInput().build(new XmlReader(new URL(feedUrl)));

        RssResult r = new RssResult();
        for (SyndEntry e : feed.getEntries()) {
            if (e.getTitle() != null && !e.getTitle().isBlank()) {
                r.titles.add(e.getTitle());
            }
        }
        r.count = r.titles.size();
        if (r.titles.size() > 5) r.titles = r.titles.subList(0, 5);
        return r;
    }
}
