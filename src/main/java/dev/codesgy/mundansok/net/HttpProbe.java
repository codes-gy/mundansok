package dev.codesgy.mundansok.net;

import java.util.Map;

/** 점검용 GET 요청. 리다이렉트는 따라가지 않는다. 테스트에서는 가짜 구현으로 바꾼다 */
public interface HttpProbe {

    /** 연결 자체가 안 되면 {@link java.io.UncheckedIOException} */
    default HttpResult get(String url) {
        return get(url, Map.of());
    }

    /** 요청 헤더를 더해 보낸다 (CORS 확인용 Origin 등) */
    HttpResult get(String url, Map<String, String> headers);
}
