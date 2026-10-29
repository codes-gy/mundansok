package dev.codesgy.mundansok.net;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 점검 요청 하나의 결과. 본문은 앞부분만 담는다.
 *
 * @param headers 헤더 이름은 소문자
 */
public record HttpResult(int status, Map<String, List<String>> headers, String bodyPrefix) {

    public Optional<String> header(String name) {
        List<String> values = headers.get(name.toLowerCase());
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    /** 같은 이름으로 여러 번 온 헤더(Set-Cookie 등)의 값 전부 */
    public List<String> values(String name) {
        return headers.getOrDefault(name.toLowerCase(), List.of());
    }
}
