# 문단속 프론트엔드

Next.js 16 (App Router). 화면은 도메인 입력 → 소유 인증 → 점검 진행 → 결과 리포트 순서다.

| 경로 | 화면 | 부르는 API |
|---|---|---|
| `/` | 랜딩 · 도메인 입력 | `POST /api/domains` (인증된 도메인이면 `POST /api/domains/{id}/scans`까지) |
| `/verify?domain=` | 소유 인증 (TXT 레코드 안내) | `POST /api/domains` · `POST /api/domains/{id}/verify` · `POST /api/domains/{id}/scans` |
| `/scans/{id}?domain=` | 점검 진행 · 결과 | `GET /api/scans/{id}` (끝날 때까지 2초마다) · 다시 점검 `POST /api/domains/{id}/scans` |

브라우저는 같은 출처의 `/api/*`를 부르고, `next.config.ts`의 rewrites가 `API_BASE_URL`로 넘긴다. 그래서 백엔드에 CORS 설정이 필요 없다.

## 실행

```bash
npm install
API_BASE_URL=http://localhost:8080 npm run dev   # 백엔드 ./gradlew bootRun 과 함께
npm run lint
npm run build
```

`API_BASE_URL`을 비우면 `http://localhost:8080`을 쓴다. 배포 환경에서는 백엔드 주소를 넣는다.
