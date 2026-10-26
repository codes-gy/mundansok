# 05. 시스템 아키텍처

## 구성도
```mermaid
flowchart LR
    U[사용자 브라우저] -->|HTTPS| FE[Next.js 화면]
    FE -->|REST /api| API[Spring Boot API]
    API --> DS[DomainService<br/>정규화 · 소유 인증]
    API --> SS[ScanService<br/>점검 요청 · 10분 제한]
    SS -->|커밋 후 비동기| RUN[ScanRunner<br/>점검 실행 · 점수]
    RUN --> C1[TLS] & C2[HTTPS 전환] & C3[보안 헤더] & C4[민감 파일] & C5[SPF/DMARC]
    C1 & C2 & C3 & C4 --> G{AddressGuard<br/>내부 IP 차단}
    G -->|공개 IP만| T[(점검 대상 사이트)]
    C5 & DS --> DNS[(DNS)]
    API & RUN --> DB[(PostgreSQL)]
    RUN -.4단계.-> AI[LLM 결과 설명]
```

## 점검 흐름
```mermaid
sequenceDiagram
    actor 사용자
    participant API
    participant DB
    participant Runner as ScanRunner(비동기)
    participant Site as 점검 대상
    사용자->>API: POST /domains/{id}/scans
    API->>DB: 인증 여부 · 최근 점검 시각 확인
    API->>DB: scan(QUEUED) 저장
    API-->>사용자: 202 Accepted (scanId)
    API->>Runner: 커밋 후 실행
    Runner->>DB: RUNNING
    loop 점검 5종
        Runner->>Site: GET / TLS 연결 / DNS 조회 (내부 IP면 중단)
    end
    Runner->>DB: finding 저장 · 점수 · DONE
    사용자->>API: GET /scans/{id} (폴링)
    API-->>사용자: 점수 · 위험도순 결과
```

## 설계 결정 (ADR 요약)
| # | 결정 | 이유 |
|---|---|---|
| ADR-1 | 점검 하나 = `Check` 인터페이스 하나 | 점검을 늘려도 실행 · 점수 코드는 그대로. 담당자(BE-1)가 독립적으로 추가 |
| ADR-2 | 외부 접근(HTTP · TLS · DNS)은 인터페이스 뒤로 | 테스트를 가짜 구현으로 네트워크 없이 돌림. 실제 구현에서만 SSRF 가드 통과 |
| ADR-3 | 점검은 트랜잭션 커밋 후 별도 스레드 풀에서 | API 응답을 점검 시간과 분리. 커밋 전에 실행돼 scan을 못 찾는 문제 방지 |
| ADR-4 | 판정은 규칙, AI는 설명만 | 같은 입력에 같은 판정(NFR-05). AI 응답이 달라도 점수가 흔들리지 않음 |
| ADR-5 | 상태 코드가 아니라 본문 서명으로 노출 판정 | 모든 주소에 200을 주는 SPA에서 오탐 방지 |
| ADR-6 | 노출 파일 내용은 저장하지 않음 | 우리 DB가 비밀 값 저장소가 되는 걸 막음(NFR-03) |

## 기술 스택
| 영역 | 선택 |
|---|---|
| 백엔드 | Java 21 · Spring Boot 4.1 (WebMVC · Data JPA · Validation) · Flyway |
| DB | PostgreSQL (로컬 · 테스트는 H2 PostgreSQL 모드) |
| 프론트엔드 | Next.js · TypeScript |
| 외부 접근 | JDK HttpClient · SSLSocket · JNDI DNS (추가 의존성 없음) |
| 테스트 · CI | JUnit 5 · AssertJ · MockMvc, GitHub Actions |
