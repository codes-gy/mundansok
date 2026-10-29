# 문단속 (Mundansok)

> 회사 도메인을 넣으면, 공격자가 밖에서 먼저 보게 될 약점을 찾아 쉬운 말로 알려 주는 **외부 노출 점검 서비스**

AI 도구로 공격 정찰이 자동화되면서, 노출된 설정 파일 · 만료된 인증서 · 빠진 보안 헤더 · 위조 가능한 메일 도메인이 빠르게 찾아집니다.
문단속은 같은 시선으로, **공격 없이** 내 도메인을 점검하고 무엇부터 고칠지 알려 줍니다.

## 원칙
- **내 도메인만** — DNS TXT 레코드로 소유를 인증한 도메인만 점검합니다
- **공격하지 않고 확인만** — 일반 GET · TLS 연결 · DNS 조회만 합니다
- **내부망 차단** — 사설 · 루프백 · 링크 로컬 주소로 풀리는 호스트는 요청하지 않습니다
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
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/domains` | 도메인 등록, 인증용 TXT 레코드 안내 |
| POST | `/api/domains/{id}/verify` | 소유 인증 |
| POST | `/api/domains/{id}/scans` | 점검 요청 (인증된 도메인, 10분에 한 번) |
| GET | `/api/scans/{id}` | 점수 · 위험도순 결과 |

자세한 명세: [docs/planning/07-api-spec.md](docs/planning/07-api-spec.md)

## 기획 산출물
팀 프로젝트 방식(이슈 · PR · 리뷰)으로 진행하기 위해 5인 팀 기준으로 작성했습니다 → [docs/planning](docs/planning/README.md)

## 기술
Java 21 · Spring Boot 4.1 · Spring Data JPA · Flyway · PostgreSQL / Next.js (2단계)
