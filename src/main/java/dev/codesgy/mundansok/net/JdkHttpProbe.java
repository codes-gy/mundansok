package dev.codesgy.mundansok.net;

import dev.codesgy.mundansok.config.ScanProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class JdkHttpProbe implements HttpProbe {

    private static final int BODY_LIMIT = 2048;

    private final HttpClient client;
    private final ScanProperties properties;
    private final AddressGuard guard;

    public JdkHttpProbe(ScanProperties properties, AddressGuard guard) {
        this.properties = properties;
        this.guard = guard;
        this.client = HttpClient.newBuilder()
                .connectTimeout(properties.httpTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public HttpResult get(String url, Map<String, String> headers) {
        URI uri = URI.create(url);
        guard.requirePublic(uri.getHost());
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(properties.httpTimeout())
                .header("User-Agent", properties.userAgent())
                .GET();
        headers.forEach(builder::header);
        HttpRequest request = builder.build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String body;
            try (InputStream in = response.body()) {
                body = new String(in.readNBytes(BODY_LIMIT), StandardCharsets.UTF_8);
            }
            return new HttpResult(response.statusCode(), lowerCase(response.headers().map()), body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("점검 요청이 중단됐습니다", e));
        }
    }

    private static Map<String, List<String>> lowerCase(Map<String, List<String>> headers) {
        Map<String, List<String>> result = new TreeMap<>();
        headers.forEach((name, values) -> result.put(name.toLowerCase(Locale.ROOT), values));
        return result;
    }
}
