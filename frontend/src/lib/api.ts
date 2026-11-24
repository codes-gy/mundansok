// 백엔드 API (docs/planning/07-api-spec.md) 호출과 응답 타입

export type Severity = "HIGH" | "MEDIUM" | "LOW" | "INFO";
export type ScanStatus = "QUEUED" | "RUNNING" | "DONE" | "FAILED";

export type VerificationRecord = { type: string; name: string; value: string };

/** id 는 등록 한 건의 UUID. ownerToken 은 등록 응답에만 오고, 이후 모든 요청에 Bearer 로 보낸다 */
export type DomainResponse = {
  id: string;
  name: string;
  verified: boolean;
  verifiedAt: string | null;
  verification: VerificationRecord | null;
  ownerToken: string | null;
};

export type Finding = {
  check: string;
  severity: Severity;
  title: string;
  detail: string;
  recommendation: string;
};

export type ScanResponse = {
  id: string;
  domainId: string;
  domain: string;
  status: ScanStatus;
  score: number | null;
  error: string | null;
  createdAt: string;
  finishedAt: string | null;
  findings: Finding[];
};

/** 백엔드가 Problem Details 로 돌려준 오류. code 로 화면을 나눈다 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit, ownerToken?: string): Promise<T> {
  let res: Response;
  try {
    res = await fetch(path, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...(ownerToken ? { Authorization: `Bearer ${ownerToken}` } : {}),
        ...init?.headers,
      },
    });
  } catch {
    throw new ApiError(0, "NETWORK", "서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요");
  }
  if (!res.ok) {
    const problem = await res.json().catch(() => null);
    // 백엔드는 운영 시간(평일 11:30~23:00)에만 켜져 있어, 꺼져 있으면 Problem Details 없이 503 이 온다
    if (!problem && (res.status === 502 || res.status === 503)) {
      throw new ApiError(
        res.status,
        "BACKEND_OFF",
        "지금은 점검 서버가 쉬고 있어요. 평일 11:30~23:00에 다시 시도해 주세요",
      );
    }
    throw new ApiError(
      res.status,
      problem?.code ?? "UNKNOWN",
      problem?.detail ?? "요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요",
    );
  }
  return res.json() as Promise<T>;
}

/** 등록할 때마다 새 등록과 소유자 토큰을 만든다. 토큰은 이 응답에서만 받을 수 있으니 saveClaim 으로 보관한다 */
export function registerDomain(name: string) {
  return request<DomainResponse>("/api/domains", { method: "POST", body: JSON.stringify({ name }) });
}

export function getDomain(id: string, ownerToken: string) {
  return request<DomainResponse>(`/api/domains/${encodeURIComponent(id)}`, { cache: "no-store" }, ownerToken);
}

export function verifyDomain(id: string, ownerToken: string) {
  return request<DomainResponse>(`/api/domains/${encodeURIComponent(id)}/verify`, { method: "POST" }, ownerToken);
}

export function requestScan(domainId: string, ownerToken: string) {
  return request<ScanResponse>(`/api/domains/${encodeURIComponent(domainId)}/scans`, { method: "POST" }, ownerToken);
}

export function getScan(id: string, ownerToken: string) {
  return request<ScanResponse>(`/api/scans/${encodeURIComponent(id)}`, { cache: "no-store" }, ownerToken);
}

export function scanPath(scanId: string, domain: string) {
  return `/scans/${encodeURIComponent(scanId)}?domain=${encodeURIComponent(domain)}`;
}

// 이 브라우저에서 등록한 도메인의 등록 id 와 소유자 토큰. 토큰이 있어야 인증 · 점검 · 결과 조회를 할 수 있다
export type SavedClaim = { id: string; token: string };

const CLAIMS_KEY = "mundansok.claims";

function readClaims(): Record<string, SavedClaim> {
  try {
    const parsed = JSON.parse(window.localStorage.getItem(CLAIMS_KEY) ?? "{}");
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

function writeClaims(claims: Record<string, SavedClaim>) {
  try {
    window.localStorage.setItem(CLAIMS_KEY, JSON.stringify(claims));
  } catch {
    // 저장소를 못 쓰면(사생활 보호 모드 등) 이번 화면에서만 쓴다
  }
}

/** 사용자가 넣은 값을 서버와 비슷하게 정리한다 ("https://Shop.Example.com/" → "shop.example.com"). 저장 키로만 쓴다 */
export function domainKey(input: string) {
  return input
    .trim()
    .toLowerCase()
    .replace(/^[a-z][a-z0-9+.-]*:\/\//, "")
    .replace(/[/?#].*$/, "")
    .replace(/^[^@]*@/, "")
    .replace(/:\d+$/, "")
    .replace(/\.$/, "");
}

export function savedClaim(domain: string): SavedClaim | null {
  return readClaims()[domainKey(domain)] ?? null;
}

export function saveClaim(domain: string, claim: SavedClaim) {
  writeClaims({ ...readClaims(), [domainKey(domain)]: claim });
}

export function forgetClaim(domain: string) {
  const claims = readClaims();
  delete claims[domainKey(domain)];
  writeClaims(claims);
}

/** 등록하고 토큰을 보관한다 */
export async function registerAndSave(name: string) {
  const domain = await registerDomain(name);
  if (domain.ownerToken) {
    const claim = { id: domain.id, token: domain.ownerToken };
    saveClaim(domain.name, claim);
    if (domainKey(name) !== domain.name) saveClaim(name, claim);
  }
  return domain;
}
