# 07. API 명세

공통: JSON, 오류는 RFC 9457 Problem Details + `code`

## POST /api/domains — 도메인 등록
```json
// 요청
{ "name": "https://Shop.Example.com/" }
// 201 Created
{
  "id": 1, "name": "shop.example.com", "verified": false, "verifiedAt": null,
  "verification": { "type": "TXT", "name": "_mundansok-verify.shop.example.com", "value": "mundansok-verify=3f9c…" }
}
```

## POST /api/domains/{id}/verify — 소유 인증
- 200: `verified: true`, `verification: null`
- 409 `VERIFICATION_RECORD_NOT_FOUND` · 502 `DNS_UNAVAILABLE`

## POST /api/domains/{id}/scans — 점검 요청
- 202: `{ "id": 7, "status": "QUEUED", ... }`
- 403 `DOMAIN_NOT_VERIFIED` · 429 `SCAN_COOLDOWN`

## GET /api/scans/{id} — 점검 결과
```json
{
  "id": 7, "domainId": 1, "status": "DONE", "score": 50,
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
| INVALID_REQUEST | 400 | 필수 값 누락 |
| DOMAIN_NOT_FOUND / SCAN_NOT_FOUND | 404 | 없는 리소스 |
| DOMAIN_NOT_VERIFIED | 403 | 소유 인증 전 점검 요청 |
| VERIFICATION_RECORD_NOT_FOUND | 409 | TXT 레코드에 인증 값 없음 |
| SCAN_COOLDOWN | 429 | 10분 안 재점검 |
| DNS_UNAVAILABLE | 502 | DNS 조회 실패 |
