package projet;

import com.advisora.Model.projet.Project;
import com.advisora.Services.projet.NewsService;
import com.advisora.Services.projet.NewsService.NewsErrorType;
import com.advisora.Services.projet.NewsService.NewsServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class test_news_service {

    @Test
    void mapping_known_type_and_unknown_fallback() throws Exception {
        NewsService service = new NewsService();
        Method mappingMethod = NewsService.class.getDeclaredMethod("mappingForType", String.class);
        mappingMethod.setAccessible(true);

        Object fintech = mappingMethod.invoke(service, "fintech payments");
        Method apiCategory = fintech.getClass().getDeclaredMethod("apiCategory");
        Method mappedTerms = fintech.getClass().getDeclaredMethod("mappedTerms");

        assertEquals("business", apiCategory.invoke(fintech));
        assertTrue(((String) mappedTerms.invoke(fintech)).contains("fintech"));

        Object unknown = mappingMethod.invoke(service, "construction");
        assertEquals("business", apiCategory.invoke(unknown));
        assertEquals("", mappedTerms.invoke(unknown));
    }

    @Test
    void query_contains_title_and_mapped_terms() throws Exception {
        NewsService service = new NewsService();
        Method buildQuery = NewsService.class.getDeclaredMethod("buildQuery", Project.class);
        buildQuery.setAccessible(true);

        Project project = project(7, "NeoBank Platform", "FinTech");
        Object query = buildQuery.invoke(service, project);
        Method category = query.getClass().getDeclaredMethod("category");
        Method keywords = query.getClass().getDeclaredMethod("keywords");

        assertEquals("business", category.invoke(query));
        String keywordValue = (String) keywords.invoke(query);
        assertTrue(keywordValue.contains("NeoBank Platform"));
        assertTrue(keywordValue.toLowerCase().contains("fintech"));
    }

    @Test
    void cache_hit_then_expiration_triggers_new_call() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-02-23T10:00:00Z"));
        AtomicInteger sendCalls = new AtomicInteger(0);
        FakeHttpClient client = new FakeHttpClient(() -> {
            sendCalls.incrementAndGet();
            return FakeHttpResponse.of(200, "{\"articles\":[{\"title\":\"A\",\"description\":\"D\",\"url\":\"https://a\",\"author\":\"S\",\"publishedAt\":\"2026-02-20T10:00:00Z\",\"source\":{\"name\":\"Example\"}}]}");
        });

        NewsService service = createService(client, () -> "key", clock);
        Project project = project(2, "Solar Grid", "Energy");

        service.fetchNews(project);
        service.fetchNews(project);
        assertEquals(1, sendCalls.get(), "Second call must come from cache.");

        clock.plus(Duration.ofMinutes(11));
        service.fetchNews(project);
        assertEquals(2, sendCalls.get(), "Expired cache must trigger API call.");
    }

    @Test
    void maps_429_to_rate_limit() throws Exception {
        NewsService service = createService(
                new FakeHttpClient(() -> FakeHttpResponse.of(429, "{}")),
                () -> "key",
                new MutableClock(Instant.parse("2026-02-23T10:00:00Z"))
        );

        NewsServiceException ex = assertThrows(NewsServiceException.class, () -> service.fetchNews(project(3, "Fin Product", "FinTech")));
        assertEquals(NewsErrorType.RATE_LIMIT, ex.getType());
    }

    @Test
    void maps_401_to_unauthorized() throws Exception {
        NewsService service = createService(
                new FakeHttpClient(() -> FakeHttpResponse.of(401, "{}")),
                () -> "key",
                new MutableClock(Instant.parse("2026-02-23T10:00:00Z"))
        );

        NewsServiceException ex = assertThrows(NewsServiceException.class, () -> service.fetchNews(project(4, "Health App", "Healthcare")));
        assertEquals(NewsErrorType.UNAUTHORIZED, ex.getType());
    }

    @Test
    void maps_io_to_network_and_empty_news_to_no_results() throws Exception {
        NewsService networkService = createService(
                new FakeHttpClient(() -> {
                    throw new IOException("down");
                }),
                () -> "key",
                new MutableClock(Instant.parse("2026-02-23T10:00:00Z"))
        );
        NewsServiceException networkEx = assertThrows(NewsServiceException.class, () -> networkService.fetchNews(project(5, "IT Hub", "IT")));
        assertEquals(NewsErrorType.NETWORK, networkEx.getType());

        NewsService noResultService = createService(
                new FakeHttpClient(() -> FakeHttpResponse.of(200, "{\"articles\":[]}")),
                () -> "key",
                new MutableClock(Instant.parse("2026-02-23T10:00:00Z"))
        );
        NewsServiceException noResultsEx = assertThrows(NewsServiceException.class, () -> noResultService.fetchNews(project(6, "Unknown", "Other")));
        assertEquals(NewsErrorType.NO_RESULTS, noResultsEx.getType());
    }

    private NewsService createService(HttpClient client, Supplier<String> apiKey, Clock clock) throws Exception {
        Constructor<NewsService> ctor = NewsService.class.getDeclaredConstructor(HttpClient.class, ObjectMapper.class, Supplier.class, Clock.class);
        ctor.setAccessible(true);
        return ctor.newInstance(client, new ObjectMapper(), apiKey, clock);
    }

    private Project project(int id, String title, String type) {
        Project p = new Project();
        p.setIdProj(id);
        p.setTitleProj(title);
        p.setTypeProj(type);
        return p;
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant initial) {
            this.current = initial;
        }

        void plus(Duration duration) {
            current = current.plus(duration);
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
            return current;
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
            throw new UnsupportedOperationException("Not used by test.");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException("Not used by test.");
        }
    }

    private interface ResponseFactory {
        HttpResponse<String> get() throws IOException;
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
            return HttpHeaders.of(Map.of(), (s1, s2) -> true);
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
