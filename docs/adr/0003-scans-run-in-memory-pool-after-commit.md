# 점검은 커밋 뒤 메모리 스레드 풀에서 돌리고, 내구성 있는 작업 큐는 두지 않는다

점검은 외부 응답을 기다리는 일이 대부분이라 API 응답과 분리해야 하지만, 메시지 브로커나 DB 기반 작업 큐는 이 규모에 비해 무겁다. 그래서 점검 기록(QUEUED)을 커밋한 뒤 `afterCommit`에서 작은 메모리 풀(`scanExecutor`, 스레드 4 · 대기열 100)에 넘긴다. 그 대가로 생기는 두 구멍은 코드로 메운다: 풀이 가득 차면 방금 만든 점검을 지우고 쿨다운을 되돌린 뒤 `503 SCAN_QUEUE_FULL`, 재시작으로 멈춘 점검은 기동 시 `stale-after`(기본 15분)보다 오래된 것만 FAILED로 정리(`ScanRecovery`).

근거: [docs/planning/05-architecture.md](../planning/05-architecture.md) 설계 결정 ADR-3, 커밋 aac1b3b (PR #27). 운영 지표 · 알림은 README "운영 · 관측성".

## Consequences

- 재시작하면 돌던 점검은 재시도되지 않고 실패로 끝난다.
- 여러 대로 띄우면 풀 · IP별 요청 제한이 대수만큼 늘어난다(서버 한 대 기준 설계). `stale-after`를 넉넉히 두는 이유도 다른 서버가 돌리는 점검을 건드리지 않기 위해서다.
