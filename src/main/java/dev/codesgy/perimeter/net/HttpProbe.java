package dev.codesgy.perimeter.net;

/** 점검용 GET 요청. 리다이렉트는 따라가지 않는다. 테스트에서는 가짜 구현으로 바꾼다 */
public interface HttpProbe {

    /** 연결 자체가 안 되면 {@link java.io.UncheckedIOException} */
    HttpResult get(String url);
}
