# 문단속

## 먼저 읽을 것
- **용어** → [GLOSSARY.md](GLOSSARY.md): 클래스 · API 필드 · 화면 문구에 이름을 붙일 때. 특히 점검(한 번의 실행)과 점검 항목(그 안의 확인 하나)을 구분한다.
- **결정** → [docs/adr/](docs/adr/): `net/` 연결 방식, 소유자 토큰 · 등록 모델, 점검 실행(스레드 풀 · 재시작 정리)을 바꾸려 할 때.
- **위협 모델** → [docs/threat-model.md](docs/threat-model.md): `net/` · `domain/` · `ratelimit/`를 고치거나 밖으로 나가는 요청을 새로 만들 때.

## 지켜야 할 불변식
- 점검 대상으로 나가는 연결은 전부 `AddressGuard.resolvePublic`이 돌려준 **검사한 IP**로 `SocketConnector`를 통해 연다. 이름은 한 번만 풀고, Host 헤더 · SNI · 인증서 검증에만 쓴다. 새 점검 항목은 HTTP면 `HttpProbe`, TLS면 `TlsInspector`, DNS 레코드면 `DnsResolver`를 주입받아 쓴다. 이름을 다시 푸는 클라이언트(`HttpClient`, `URL.openConnection`, RestClient 등)는 이 경로에 들이지 않는다(ADR 0001).
- 리다이렉트는 따라가지 않고 `Location` 값만 읽는다.
- `BlockedAddressException`은 점검 항목 안에서 잡지 않고 올려 보낸다. `ScanRunner`가 점검 전체를 멈추는 신호다.
- 발견 항목의 `detail`에는 근거 요약만 넣는다. 노출된 비밀 값 · 파일 본문은 DB와 로그 어디에도 남기지 않는다.
- 노출 판정은 상태 코드가 아니라 본문 서명으로 한다(모든 경로에 200을 주는 SPA 오탐 방지).
- API 밖으로 나가는 식별자는 `publicId`(UUID)다. 등록 · 점검 결과 조회에서 없는 것과 남의 것은 같은 404로 답한다.
- 쿨다운은 `DomainRepository.acquireScanSlot`의 조건부 UPDATE 한 문장으로만 확인 · 기록한다. `releaseScanSlot`이 시각 값을 비교하므로 `DomainService.now()`의 밀리초 절삭을 유지한다.

## 테스트
- `./gradlew test`는 JDK 21이 필요하다(Gradle 툴체인, 자동 내려받기 설정 없음). Docker는 필요 없다: H2 PostgreSQL 모드 + Flyway로 돈다.
- 테스트는 실제 네트워크를 쓰지 않는다. 스프링 컨텍스트 테스트는 `support/FakeNetwork`를 `@Import`하고, `net/` 단위 테스트는 `HostResolver` · `SocketConnector`를 가짜로 넣는다.
- 마이그레이션 SQL은 H2(PostgreSQL 모드)와 PostgreSQL 양쪽에서 돌아야 한다. `ddl-auto: validate`라 엔티티를 바꾸면 Flyway 마이그레이션을 새로 추가한다.

## 함정
- API 경로 `/api/domains/{id}`와 응답 필드 `domainId`의 id는 도메인이 아니라 **등록**(`DomainClaim.publicId`)이다.
- 백엔드 값 일부를 프론트가 복사해 들고 있다: `frontend/src/lib/checks.ts`의 점검 항목 id · 위험도 감점 · 쿨다운 분. 점검 항목을 추가하거나 값을 바꾸면 함께 고치고 README 점검 항목 표도 갱신한다.
- `docs/planning/`의 일부는 V2 이전 내용이다(예: 03-requirements FR-02 "재등록하면 같은 값", 05-architecture 기술 스택 "JDK HttpClient"). 코드와 `docs/threat-model.md`가 기준이다.
- 프론트엔드 작업은 [frontend/AGENTS.md](frontend/AGENTS.md)를 따른다(학습 데이터와 다른 Next.js 버전).
- 요청 제한 · 점검 풀은 메모리 기반, 서버 한 대 기준이다.

## 작업 흐름
- 브랜치: `feature/{이니셜}/{작업}` → `develop`. `main`은 `develop`에서 릴리스 PR로만 들어간다.
- 커밋: `type: 한국어 요약`(feat · fix · refactor · test · docs · chore), 본문에 왜 바꿨는지.
- API를 바꾸면 [docs/planning/07-api-spec.md](docs/planning/07-api-spec.md)를 함께 갱신한다. 점검 항목 · 외부 요청을 바꾸면 PR 템플릿의 "점검 윤리 체크"를 채운다.
