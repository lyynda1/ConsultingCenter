package com.advisora.utils.news;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class httpUtil {



    public final class HttpUtil {
        private static final HttpClient CLIENT = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(45))
                .build();

        private HttpUtil() {}

        public static String get(String url) throws Exception {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "Mozilla/5.0 AdvisoraRiskMonitor/1.0")
                    .header("Accept", "application/json,text/plain,*/*")
                    .GET()
                    .build();

            HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());

            int code = res.statusCode();
            String body = res.body() == null ? "" : res.body();

            if (code < 200 || code >= 300) {
                // show a small snippet to debug
                String snippet = body.length() > 200 ? body.substring(0, 200) : body;
                throw new RuntimeException("HTTP " + code + " for " + url + " body: " + snippet);
            }

            // If server returned HTML, don't try to parse it as JSON
            String trimmed = body.trim();
            if (trimmed.startsWith("<!DOCTYPE html") || trimmed.startsWith("<html")) {
                String snippet = trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
                throw new RuntimeException("Non-JSON response (HTML) from " + url + " body: " + snippet);
            }

            return body;
        }
    }


}
