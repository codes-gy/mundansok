import type { Severity } from "./api";

/** 백엔드 Check.id() 와 화면 이름 */
export const CHECKS = [
  { id: "tls", label: "TLS 인증서", summary: "인증서가 유효하고 최신인지" },
  { id: "https-redirect", label: "HTTPS 전환", summary: "http로 와도 https로 보내는지" },
  { id: "security-headers", label: "보안 헤더 · 쿠키", summary: "브라우저 보호 설정이 켜져 있는지" },
  { id: "exposed-files", label: "민감 파일", summary: "설정·소스 파일이 열려 있는지" },
  { id: "email-auth", label: "메일 위조 방지", summary: "내 도메인 사칭 메일을 막는지" },
  { id: "cors", label: "CORS 설정", summary: "다른 사이트가 응답을 읽을 수 있는지" },
] as const;

export function checkLabel(id: string) {
  return CHECKS.find((check) => check.id === id)?.label ?? id;
}

export const SEVERITIES: Severity[] = ["HIGH", "MEDIUM", "LOW", "INFO"];

/** 감점은 백엔드 Severity.penalty 와 같다 */
export const SEVERITY_INFO: Record<Severity, { label: string; penalty: number }> = {
  HIGH: { label: "높음", penalty: 20 },
  MEDIUM: { label: "보통", penalty: 10 },
  LOW: { label: "낮음", penalty: 3 },
  INFO: { label: "참고", penalty: 0 },
};

/** 백엔드 mundansok.scan.cooldown */
export const SCAN_COOLDOWN_MINUTES = 10;
