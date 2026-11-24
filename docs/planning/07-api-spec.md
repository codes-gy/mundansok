# 07. API 명세

공통: JSON, 오류는 RFC 9457 Problem Details + `code`

**인증**: 등록 응답의 `ownerToken`을 등록 이후 모든 요청에 `Authorization: Bearer <ownerToken>`으로 보낸다.
토큰은 등록 응답에서만 한 번 받을 수 있고(서버는 해시만 저장), 없으면 `401 OWNER_TOKEN_REQUIRED`, 틀리면 없는 리소스와 똑같이 `404`.
`{id}`는 모두 UUID다(순번 id는 내보내지 않는다). 위협 모델: [../threat-model.md](../threat-model.md)

**요청 제한**: 등록 · 인증 · 점검 요청은 IP별 토큰 버킷(기본: 등록 20 · 인증 30 · 점검 30 / 시간). 넘치면 `429 RATE_LIMITED` + `Retry-After`.

## POST /api/domains — 도메인 등록
같은 도메인을 다시 등록하면 **새 등록 · 새 토큰 · 새 TXT 값**을 준다. 기존 등록의 토큰이나 인증 상태는 넘겨주지 않는다.
```json
// 요청
{ "name": "https://Shop.Example.com/" }
// 201 Created
{
  "id": "6f1c0a52-…", "name": "shop.example.com", "verified": false, "verifiedAt": null,
  "verification": { "type": "TXT", "name": "_mundansok-verify.shop.example.com", "value": "mundansok-verify=3f9c…" },
  "ownerToken": "q3J…(43자)"
}
```

## GET /api/domains/{id} — 등록 상태 (토큰 필요)
- 200: 등록 응답과 같은 모양, `ownerToken: null`

## POST /api/domains/{id}/verify — 소유 인증 (토큰 필요)
- 200: `verified: true` (TXT 안내는 계속 준다 — 점검할 때마다 다시 확인하므로 레코드를 남겨 둬야 한다)
- 409 `VERIFICATION_RECORD_NOT_FOUND` · 502 `DNS_UNAVAILABLE`

## POST /api/domains/{id}/scans — 점검 요청 (토큰 필요)
- 요청마다 TXT 레코드를 다시 확인한 뒤, 도메인별 쿨다운을 원자적으로(조건부 UPDATE) 확인 · 기록한다
- 202: `{ "id": "9b2e…", "domainId": "6f1c…", "domain": "shop.example.com", "status": "QUEUED", ... }`
- 403 `DOMAIN_NOT_VERIFIED` · 403 `VERIFICATION_EXPIRED` · 429 `SCAN_COOLDOWN` · 503 `SCAN_QUEUE_FULL`

## GET /api/scans/{id} — 점검 결과 (점검을 요청한 등록의 토큰 필요)
```json
{
  "id": "9b2e…", "domainId": "6f1c…", "domain": "shop.example.com", "status": "DONE", "score": 50,
  "createdAt": "2026-11-03T01:00:00Z", "finishedAt": "2026-11-03T01:00:09Z",
  "findings": [
    { "check": "exposed-files", "severity": "HIGH", "title": "환경 변수 파일(.env) 노출",
      "detail": "https://shop.example.com/.env 가 200으로 응답하고 내용이 실제 파일 형식과 일치합니다",
      "recommendation": ".env 파일을 웹 루트 밖으로 옮기고, 안에 있던 비밀번호 · API 키는 모두 교체해 주세요" }
  ]
}
```

## 오류 코드
| code | 상태 | 의미 |
|---|---|---|
| INVALID_DOMAIN | 400 | 형식 오류 · IP · 내부용 도메인 |
| INVALID_REQUEST | 400 | 필수 값 누락 · UUID가 아닌 id |
| OWNER_TOKEN_REQUIRED | 401 | Authorization: Bearer 소유자 토큰 없음 |
| DOMAIN_NOT_FOUND / SCAN_NOT_FOUND | 404 | 없는 리소스, 또는 토큰이 맞지 않음(구분하지 않는다) |
| DOMAIN_NOT_VERIFIED | 403 | 소유 인증 전 점검 요청 |
| VERIFICATION_EXPIRED | 403 | 인증했지만 점검 요청 시점에 TXT 레코드가 없음 |
| VERIFICATION_RECORD_NOT_FOUND | 409 | TXT 레코드에 인증 값 없음 |
| CONFLICT | 409 | 같은 도메인을 동시에 처음 등록하는 등 — 다시 보내면 된다 |
| SCAN_COOLDOWN | 429 | 10분 안 재점검 |
| RATE_LIMITED | 429 | IP별 요청 한도 초과 (`Retry-After` 참고) |
| SCAN_QUEUE_FULL | 503 | 점검 풀이 가득 참 — 기록을 남기지 않으니 잠시 후 바로 다시 요청 가능 |
| DNS_UNAVAILABLE | 502 | DNS 조회 실패 |
