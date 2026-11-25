# 점검 요청은 검사한 IP로 직접 소켓을 열어 보낸다 (HttpClient를 쓰지 않는다)

`java.net.http.HttpClient`는 send 할 때 호스트 이름을 다시 풀기 때문에, `AddressGuard`가 검사한 IP와 실제 연결 IP가 달라지는 DNS 리바인딩(TOCTOU)에 뚫린다. 그래서 HTTP · TLS 점검은 이름을 한 번만 풀어 검사한 뒤 `SocketConnector`로 그 IP에 직접 연결하고, 도메인 이름은 Host 헤더 · SNI · 인증서 호스트 이름 검증에만 쓰는 손수 만든 HTTP/1.1 클라이언트(`net/JdkHttpProbe`, `net/JdkTlsInspector`)를 쓴다.

근거: 커밋 87d6b15, PR #28. 공격 시나리오와 차단 대역은 [docs/threat-model.md](../threat-model.md) §1.

## Consequences

- HTTP/2 · 아웃바운드 프록시 · 리다이렉트 추적은 지원하지 않는다. 필요해지면 "연결 IP 고정"을 지키는 방식으로 직접 넣어야 한다.
- 표준 클라이언트로 "단순화"하면 SSRF 방어가 조용히 사라진다. `JdkHttpProbeTest` · `JdkTlsInspectorTest`의 리바인딩 테스트(첫 조회 공인 IP, 두 번째 조회 내부 IP)가 이를 막는다.
- [docs/planning/05-architecture.md](../planning/05-architecture.md)의 기술 스택 표("JDK HttpClient")는 이 결정 이전 내용이다.
