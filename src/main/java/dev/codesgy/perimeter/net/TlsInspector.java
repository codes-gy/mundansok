package dev.codesgy.perimeter.net;

/** 443 포트로 TLS 연결을 맺고 인증서 정보를 돌려준다. 연결 자체가 안 되면 UncheckedIOException */
public interface TlsInspector {

    TlsInfo inspect(String host);
}
