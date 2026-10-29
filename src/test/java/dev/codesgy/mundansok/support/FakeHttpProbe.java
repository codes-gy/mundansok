package dev.codesgy.mundansok.support;

import dev.codesgy.mundansok.net.HttpProbe;
import dev.codesgy.mundansok.net.HttpResult;
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
    private final Map<String, Map<String, String>> requestHeaders = new ConcurrentHashMap<>();

    public FakeHttpProbe respond(String url, int status, Map<String, String> headers, String body) {
        Map<String, List<String>> multi = new HashMap<>();
        headers.forEach((name, value) -> multi.put(name, List.of(value)));
        return respondMulti(url, status, multi, body);
    }

    /** Set-Cookie처럼 같은 이름의 헤더가 여러 개인 응답 */
    public FakeHttpProbe respondMulti(String url, int status, Map<String, List<String>> headers, String body) {
        Map<String, List<String>> lower = new HashMap<>();
        headers.forEach((name, values) -> lower.put(name.toLowerCase(), List.copyOf(values)));
        responses.put(url, new HttpResult(status, lower, body));
        return this;
    }

    public FakeHttpProbe unreachable(String url) {
        unreachable.put(url, true);
        return this;
    }

    /** 그 URL로 마지막에 보낸 요청 헤더 */
    public Map<String, String> lastRequestHeaders(String url) {
        return requestHeaders.getOrDefault(url, Map.of());
    }

    public boolean requested(String url) {
        return requestHeaders.containsKey(url);
    }

    public void clear() {
        responses.clear();
        unreachable.clear();
        requestHeaders.clear();
    }

    @Override
    public HttpResult get(String url, Map<String, String> headers) {
        requestHeaders.put(url, Map.copyOf(headers));
        if (unreachable.containsKey(url)) throw new UncheckedIOException(new IOException("Connection refused"));
        return responses.getOrDefault(url, new HttpResult(404, Map.of(), "Not Found"));
    }
}
