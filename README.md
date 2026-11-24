# 문단속 (Mundansok)

> 회사 도메인을 넣으면, 공격자가 밖에서 먼저 보게 될 약점을 찾아 쉬운 말로 알려 주는 **외부 노출 점검 서비스**

AI 도구로 공격 정찰이 자동화되면서, 노출된 설정 파일 · 만료된 인증서 · 빠진 보안 헤더 · 위조 가능한 메일 도메인이 빠르게 찾아집니다.
문단속은 같은 시선으로, **공격 없이** 내 도메인을 점검하고 무엇부터 고칠지 알려 줍니다.

## 원칙
- **내 도메인만** — DNS TXT 레코드로 소유를 인증한 도메인만 점검합니다. 점검할 때마다 레코드를 다시 확인합니다
- **결과는 등록한 사람만** — 등록할 때 받은 소유자 토큰이 있어야 인증 · 점검 · 결과 조회를 할 수 있습니다
- **공격하지 않고 확인만** — 일반 GET · TLS 연결 · DNS 조회만 합니다
- **내부망 차단** — 사설 · 루프백 · 링크 로컬 · NAT64 · 6to4 등 내부로 이어질 수 있는 주소로 풀리는 호스트는 요청하지 않고,
  검사한 IP로만 연결합니다(DNS 리바인딩 차단). 자세한 내용: [docs/threat-model.md](docs/threat-model.md)
- **비밀은 저장하지 않음** — 노출 여부와 근거 요약만 남깁니다

## 점검 항목
| 점검 | 내용 |
|---|---|
| TLS | 인증서 만료 · 호스트 이름 · TLS 버전 · CAA 레코드 |
| HTTPS 전환 | `http://` 접속이 `https://`로 넘어가는지 |
| 보안 헤더 | HSTS(유지 기간 포함) · CSP(인라인 스크립트 허용 포함) · nosniff · 클릭재킹 방지 · Referrer-Policy · 쿠키 Secure · HttpOnly · SameSite · 서버 버전 · X-Powered-By 노출 |
| 민감 파일 | `.git` · `.svn` · `.DS_Store` · `.env` · Actuator env · heapdump(목록만 확인) · server-status · phpinfo · OpenAPI 문서 (본문 서명으로 판정) |
| CORS | 아무 출처나 그대로 허용하는지, 로그인 정보(쿠키)까지 허용하는지 |
| 메일 위조 방지 | SPF · DMARC |

## 실행
```bash
./gradlew bootRun   # 기본은 메모리 H2
./gradlew test
```
PostgreSQL: `DATABASE_URL=jdbc:postgresql://... DATABASE_USERNAME=... DATABASE_PASSWORD=... ./gradlew bootRun`

## API
등록 응답의 `ownerToken`을 이후 모든 요청에 `Authorization: Bearer <ownerToken>`으로 보냅니다. 토큰은 등록 응답에서 **한 번만** 받을 수 있습니다.
`{id}`는 모두 UUID입니다.

| 메서드 | 경로 | 토큰 | 설명 |
|---|---|---|---|
| POST | `/api/domains` | | 도메인 등록 → 등록 id · 소유자 토큰 · 인증용 TXT 레코드 안내 (등록마다 새로 발급) |
| GET | `/api/domains/{id}` | 필요 | 등록 상태 · TXT 레코드 안내 |
| POST | `/api/domains/{id}/verify` | 필요 | 소유 인증 |
| POST | `/api/domains/{id}/scans` | 필요 | 점검 요청 (TXT 레코드 재확인, 같은 도메인 10분에 한 번) |
| GET | `/api/scans/{id}` | 필요 | 점수 · 위험도순 결과 (점검을 요청한 등록의 토큰으로만) |

```bash
# 1) 등록 — ownerToken은 다시 받을 수 없으니 보관한다
curl -s -X POST localhost:8080/api/domains -H 'Content-Type: application/json' -d '{"name":"example.com"}'
# 2) 안내받은 TXT 레코드를 DNS에 넣은 뒤 인증
curl -s -X POST localhost:8080/api/domains/$ID/verify -H "Authorization: Bearer $TOKEN"
# 3) 점검 요청 → 4) 결과
curl -s -X POST localhost:8080/api/domains/$ID/scans -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/api/scans/$SCAN_ID -H "Authorization: Bearer $TOKEN"
```

등록 · 인증 · 점검 요청은 IP별로 횟수를 제한합니다(`mundansok.rate-limit`, 넘치면 `429 RATE_LIMITED`). 메모리 기반이라 **서버 한 대 기준**입니다.

자세한 명세: [docs/planning/07-api-spec.md](docs/planning/07-api-spec.md)

## 운영 · 관측성
- **지표**: `GET /actuator/prometheus` (actuator는 `health` · `prometheus`만 연다)
  | 지표 | 뜻 |
  |---|---|
  | `mundansok_check_duration_seconds{check, outcome}` | 점검 항목별 실행 시간. `outcome`은 `ok` · `failed`(예외로 끝남 · 내부 주소 차단) |
  | `mundansok_scan_rejected_total` | 점검 풀이 가득 차 거절한 점검 요청 수 |
  | `mundansok_scan_restart_cleaned_total` | 시작 시 재시작 정리로 FAILED 처리한 점검 수 |
  | `executor_active_threads` · `executor_queued_tasks` · `executor_queue_remaining_tasks` `{name="scanExecutor"}` | 점검 풀(스레드 4 · 대기열 100) 상태. Spring Boot가 자동으로 붙인다 |
- **재시작 정리**: 점검은 커밋 뒤 메모리 스레드 풀에서 돌기 때문에, 서버가 재시작되면 돌던 점검이 QUEUED · RUNNING으로 남는다.
  시작할 때(`ApplicationReadyEvent`) `mundansok.scan.stale-after`(기본 15분)보다 오래된 미완료 점검을
  `FAILED`(error: `서버 재시작으로 점검이 중단됨`)로 한 번에 바꾸고 WARN 로그를 남긴다. 기준보다 최근 것은 다른 서버가 돌리고 있을 수 있어 건드리지 않는다.
- **풀 포화**: 스레드와 대기열이 모두 차면 점검 요청은 `503 SCAN_QUEUE_FULL`로 거절하고, 방금 만든 QUEUED 기록은 지운다(쿨다운에 걸리지 않고 바로 다시 요청 가능).
- **알림 규칙**: [monitoring/prometheus-alerts.yml](monitoring/prometheus-alerts.yml) — 점검별 실패율 · 대기열 80% · 거절 발생 · 재시작 정리(정보) · 점검 멈춤.
  `promtool check rules monitoring/prometheus-alerts.yml`로 검증한다.

## 기획 산출물
팀 프로젝트 방식(이슈 · PR · 리뷰)으로 진행하기 위해 5인 팀 기준으로 작성했습니다 → [docs/planning](docs/planning/README.md)

## 기술
Java 21 · Spring Boot 4.1 · Spring Data JPA · Flyway · PostgreSQL / Next.js (2단계)
