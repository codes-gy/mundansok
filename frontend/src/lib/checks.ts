import type { Severity } from "./api";

/** 백엔드 Check.id() 와 화면 이름 */
export const CHECKS = [
  { id: "tls", label: "TLS 인증서", summary: "만료일, 호스트 이름, 오래된 TLS 버전" },
  { id: "https-redirect", label: "HTTPS 전환", summary: "http:// 접속이 https://로 넘어가는지" },
  { id: "security-headers", label: "보안 헤더", summary: "HSTS, CSP, 클릭재킹 방지, 서버 버전 노출" },
  { id: "exposed-files", label: "민감 파일", summary: ".git, .env, Actuator env, phpinfo 노출" },
  { id: "email-auth", label: "메일 위조 방지", summary: "SPF, DMARC 레코드 설정" },
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
