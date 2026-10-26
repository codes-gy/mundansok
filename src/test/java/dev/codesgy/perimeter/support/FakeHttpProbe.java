package dev.codesgy.perimeter.support;

import dev.codesgy.perimeter.net.HttpProbe;
import dev.codesgy.perimeter.net.HttpResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** URL별로 미리 정한 응답을 돌려준다. 정하지 않은 URL은 404, unreachable로 표시한 URL은 연결 실패 */
public class FakeHttpProbe implements HttpProbe {

    private final Map<String, HttpResult> responses = new ConcurrentHashMap<>();
    private final Map<String, Boolean> unreachable = new ConcurrentHashMap<>();

    public FakeHttpProbe respond(String url, int status, Map<String, String> headers, String body) {
        Map<String, List<String>> lower = new HashMap<>();
        headers.forEach((name, value) -> lower.put(name.toLowerCase(), List.of(value)));
        responses.put(url, new HttpResult(status, lower, body));
        return this;
    }

    public FakeHttpProbe unreachable(String url) {
        unreachable.put(url, true);
        return this;
    }

    public void clear() {
        responses.clear();
        unreachable.clear();
    }

    @Override
    public HttpResult get(String url) {
        if (unreachable.containsKey(url)) throw new UncheckedIOException(new IOException("Connection refused"));
        return responses.getOrDefault(url, new HttpResult(404, Map.of(), "Not Found"));
    }
}
