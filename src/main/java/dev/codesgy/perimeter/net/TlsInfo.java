package dev.codesgy.perimeter.net;

import java.time.Instant;

/**
 * TLS 연결 결과.
 *
 * @param protocol      협상된 버전 (예: TLSv1.3)
 * @param notAfter      서버 인증서 만료 시각
 * @param hostnameValid 인증서가 호스트 이름과 맞고 신뢰할 수 있는 체인인지
 * @param error         검증 실패 사유 (성공이면 null)
 */
public record TlsInfo(String protocol, Instant notAfter, boolean hostnameValid, String error) {}
