# 06. ERD

```mermaid
erDiagram
    DOMAIN ||--o{ SCAN : "점검"
    SCAN ||--o{ FINDING : "찾은 문제"
    DOMAIN {
        bigint id PK
        varchar name UK "정규화한 호스트 이름"
        varchar verification_token "TXT 인증 값"
        timestamptz verified_at "null이면 미인증"
        timestamptz created_at
    }
    SCAN {
        bigint id PK
        bigint domain_id FK
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
| `domain(name)` UNIQUE | 같은 도메인 재등록 시 기존 토큰 반환 |
| `scan(domain_id, created_at DESC)` | 10분 제한 확인(도메인별 최근 점검 1건), 점검 이력 |
| `finding(scan_id)` | 결과 조회 |

## 3단계 추가 예정
- `member`, `team`, `team_member` — 팀 단위 도메인 관리 (FR-12)
- `domain.team_id` FK, `schedule`(정기 점검 주기 · 다음 실행 시각), `notification`(발송 기록)
