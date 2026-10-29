// 백엔드 API (docs/planning/07-api-spec.md) 호출과 응답 타입

export type Severity = "HIGH" | "MEDIUM" | "LOW" | "INFO";
export type ScanStatus = "QUEUED" | "RUNNING" | "DONE" | "FAILED";

export type VerificationRecord = { type: string; name: string; value: string };

export type DomainResponse = {
  id: number;
  name: string;
  verified: boolean;
  verifiedAt: string | null;
  verification: VerificationRecord | null;
};

export type Finding = {
  check: string;
  severity: Severity;
  title: string;
  detail: string;
  recommendation: string;
};

export type ScanResponse = {
  id: number;
  domainId: number;
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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(path, {
      ...init,
      headers: { "Content-Type": "application/json", ...init?.headers },
    });
  } catch {
    throw new ApiError(0, "NETWORK", "서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요");
  }
  if (!res.ok) {
    const problem = await res.json().catch(() => null);
    // 백엔드는 운영 시간(평일 11:30~19:00)에만 켜져 있어, 꺼져 있으면 Problem Details 없이 503 이 온다
    if (!problem && (res.status === 502 || res.status === 503)) {
      throw new ApiError(
        res.status,
        "BACKEND_OFF",
        "지금은 점검 서버가 쉬고 있어요. 평일 11:30~19:00에 다시 시도해 주세요",
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

/** 같은 도메인을 다시 등록하면 기존 도메인과 인증 값을 그대로 돌려준다 */
export function registerDomain(name: string) {
  return request<DomainResponse>("/api/domains", { method: "POST", body: JSON.stringify({ name }) });
}

export function verifyDomain(id: number) {
  return request<DomainResponse>(`/api/domains/${id}/verify`, { method: "POST" });
}

export function requestScan(domainId: number) {
  return request<ScanResponse>(`/api/domains/${domainId}/scans`, { method: "POST" });
}

export function getScan(id: string | number) {
  return request<ScanResponse>(`/api/scans/${id}`, { cache: "no-store" });
}

export function scanPath(scanId: number, domain: string) {
  return `/scans/${scanId}?domain=${encodeURIComponent(domain)}`;
}
