# 06. ERD

```mermaid
erDiagram
    DOMAIN ||--o{ DOMAIN_CLAIM : "등록"
    DOMAIN ||--o{ SCAN : "점검"
    DOMAIN_CLAIM ||--o{ SCAN : "요청"
    SCAN ||--o{ FINDING : "찾은 문제"
    DOMAIN {
        bigint id PK
        varchar name UK "정규화한 호스트 이름"
        timestamptz last_scan_requested_at "쿨다운 — 조건부 UPDATE로만 바꾼다"
        timestamptz created_at
    }
    DOMAIN_CLAIM {
        bigint id PK
        uuid public_id UK "API에 내보내는 id"
        bigint domain_id FK
        varchar owner_secret_hash "소유자 토큰 SHA-256 (평문 저장 안 함)"
        varchar verification_token "TXT 인증 값 (등록마다 다름)"
        timestamptz verified_at "null이면 미인증, 점검마다 갱신"
        timestamptz created_at
    }
    SCAN {
        bigint id PK
        uuid public_id UK "API에 내보내는 id (V2 이전 기록은 null)"
        bigint domain_id FK
        bigint claim_id FK "점검을 요청한 등록 (V2 이전 기록은 null)"
        varchar status "QUEUED RUNNING DONE FAILED"
        int score "0~100"
        varchar error "실패 사유"
        timestamptz created_at
        timestamptz finished_at
    }
    FINDING {
        bigint id PK
        bigint scan_id FK
        varchar check_id "tls, security-headers ..."
        varchar severity "HIGH MEDIUM LOW INFO"
        varchar title
        varchar detail "근거 요약 (비밀 값 제외)"
        varchar recommendation "고치는 방법"
    }
```

## 인덱스
| 인덱스 | 쓰임 |
|---|---|
| `domain(name)` UNIQUE | 도메인 이름당 한 행 (쿨다운 기준) |
| `domain_claim(public_id)` UNIQUE | 등록 조회 |
| `domain_claim(domain_id)` | 도메인별 등록 |
| `scan(public_id)` UNIQUE | 결과 조회 |
| `scan(domain_id, created_at DESC)` | 점검 이력 |
| `finding(scan_id)` | 결과 조회 |

## 3단계 추가 예정
- `member`, `team`, `team_member` — 팀 단위 도메인 관리 (FR-12)
- `domain.team_id` FK, `schedule`(정기 점검 주기 · 다음 실행 시각), `notification`(발송 기록)
