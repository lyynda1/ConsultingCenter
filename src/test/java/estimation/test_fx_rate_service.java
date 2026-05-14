package estimation;

import com.advisora.Services.estimation.FxRateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class test_fx_rate_service {

    @Test
    void eur_to_eur_returns_identity_without_http_call() {
        AtomicInteger calls = new AtomicInteger(0);
        FxRateService service = new FxRateService(
                new FakeHttpClient(() -> {
                    calls.incrementAndGet();
                    return FakeHttpResponse.of(200, "{}");
                }),
                new ObjectMapper(),
                new MutableClock(Instant.parse("2026-02-25T10:00:00Z"))
        );

        FxRateService.FxQuote quote = service.getRate("EUR", "EUR");
        assertEquals(1.0, quote.rate());
        assertEquals(0, calls.get());
    }

    @Test
    void caches_rate_for_24h_and_refreshes_after_expiration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-02-25T10:00:00Z"));
        AtomicInteger calls = new AtomicInteger(0);
        FxRateService service = new FxRateService(
                new FakeHttpClient(() -> {
                    calls.incrementAndGet();
                    return FakeHttpResponse.of(200, "{\"date\":\"2026-02-25\",\"rates\":{\"TND\":3.45}}");
                }),
                new ObjectMapper(),
                clock
        );

        service.getRate("EUR", "TND");
        service.getRate("EUR", "TND");
        assertEquals(1, calls.get());

        clock.plus(Duration.ofHours(25));
        service.getRate("EUR", "TND");
        assertEquals(2, calls.get());
    }

    @Test
    void raises_error_on_http_failure() {
        FxRateService service = new FxRateService(
                new FakeHttpClient(() -> FakeHttpResponse.of(503, "{}")),
                new ObjectMapper(),
                Clock.systemUTC()
        );

        assertThrows(RuntimeException.class, () -> service.getRate("EUR", "USD"));
    }

    private interface ResponseFactory {
        HttpResponse<String> get() throws IOException;
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void plus(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class FakeHttpClient extends HttpClient {
        private final ResponseFactory responseFactory;

        private FakeHttpClient(ResponseFactory responseFactory) {
            this.responseFactory = responseFactory;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(5));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.of(ProxySelector.of(new InetSocketAddress("localhost", 8080)));
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) responseFactory.get();
            return response;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeHttpResponse implements HttpResponse<String> {
        private final int statusCode;
        private final String body;

        private FakeHttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        static FakeHttpResponse of(int statusCode, String body) {
            return new FakeHttpResponse(statusCode, body);
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder().uri(URI.create("https://example.org")).build();
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (a, b) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://example.org");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
