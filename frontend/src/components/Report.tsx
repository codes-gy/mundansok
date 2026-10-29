"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError, requestScan, type ScanResponse, type Severity, scanPath } from "@/lib/api";
import { CHECKS, checkLabel, SCAN_COOLDOWN_MINUTES, SEVERITIES, SEVERITY_INFO } from "@/lib/checks";
import { CheckIcon } from "./Icons";

type Filter = "ALL" | Severity;

const dateFormat = new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" });

export function Report({ scan, domain }: { scan: ScanResponse; domain: string | null }) {
  const router = useRouter();
  const [filter, setFilter] = useState<Filter>("ALL");
  const [rescanError, setRescanError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  // 처음 그릴 때의 시각으로 재점검 가능 여부를 정한다 (그리는 동안 Date.now() 를 부르지 않는다)
  const [openedAt] = useState(() => Date.now());

  const findings = scan.findings;
  const score = scan.score ?? 0;
  const counts = Object.fromEntries(
    SEVERITIES.map((severity) => [severity, findings.filter((f) => f.severity === severity).length]),
  ) as Record<Severity, number>;
  const problems = findings.filter((f) => f.severity !== "INFO").length;
  const passed = CHECKS.filter((check) => !findings.some((f) => f.check === check.id));
  const shown = filter === "ALL" ? findings : findings.filter((f) => f.severity === filter);

  const minutesSince = (openedAt - new Date(scan.createdAt).getTime()) / 60000;
  const coolingDown = minutesSince < SCAN_COOLDOWN_MINUTES;

  async function rescan() {
    setPending(true);
    setRescanError(null);
    try {
      const next = await requestScan(scan.domainId);
      router.push(scanPath(next.id, domain ?? ""));
    } catch (e) {
      setRescanError(e instanceof ApiError ? e.message : "다시 점검하지 못했어요");
      setPending(false);
    }
  }

  return (
    <>
      <div className="report-head">
        <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
          <p className="report-head__meta">
            점검 결과 · {dateFormat.format(new Date(scan.finishedAt ?? scan.createdAt))}
          </p>
          <h1 className="title">{problems > 0 ? `고칠 곳이 ${problems}군데 있어요` : "고칠 곳을 찾지 못했어요"}</h1>
        </div>
        <div className="rescan">
          <button type="button" className="button" onClick={rescan} disabled={pending || coolingDown}>
            {pending ? "요청하는 중…" : "다시 점검하기"}
          </button>
          <span className="rescan__note" role={rescanError ? "alert" : undefined}>
            {rescanError ?? `같은 도메인은 ${SCAN_COOLDOWN_MINUTES}분 뒤에 다시 점검할 수 있어요`}
          </span>
        </div>
      </div>

      <section aria-label="요약" className="summary">
        <div className="card summary__score">
          <span className="summary__heading">점수</span>
          <div className="score">
            <span className="score__value">{score}</span>
            <span className="score__max">/ 100</span>
          </div>
          <div className="bar">
            <div className={`bar__fill ${barTone(score)}`} style={{ width: `${score}%` }} />
          </div>
          <p className="field__hint">100점에서 위험도별로 빼요. 높음 20 · 보통 10 · 낮음 3 · 참고 0</p>
        </div>
        <div className="card summary__counts">
          <span className="summary__heading">위험도별</span>
          <ul className="counts">
            {SEVERITIES.map((severity) => (
              <li key={severity} className={`count sev-${severity}`}>
                <span className="count__label">{SEVERITY_INFO[severity].label}</span>
                <span className="count__value">{counts[severity]}</span>
              </li>
            ))}
          </ul>
          {passed.length > 0 && (
            <p className="passed">
              <CheckIcon size={18} className="text-accent" />
              문제 없음: <strong>{passed.map((check) => check.label).join(", ")}</strong>
            </p>
          )}
        </div>
      </section>

      <section style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        <div className="findings-head">
          <h2 className="section-title" style={{ fontSize: 22 }}>
            위험한 것부터 고쳐요
          </h2>
          <div role="group" aria-label="위험도로 거르기" className="filters">
            {(["ALL", ...SEVERITIES] as Filter[]).map((key) => (
              <button
                key={key}
                type="button"
                className="filter"
                aria-pressed={filter === key}
                onClick={() => setFilter(key)}
              >
                {key === "ALL" ? "전체" : SEVERITY_INFO[key].label} {key === "ALL" ? findings.length : counts[key]}
              </button>
            ))}
          </div>
        </div>

        {shown.length === 0 ? (
          <p className="card empty">이 위험도에 해당하는 항목이 없어요.</p>
        ) : (
          <ul className="findings">
            {shown.map((finding, index) => (
              <li key={`${finding.check}-${index}`} className="card finding">
                <div className="finding__meta">
                  <span className={`badge sev-${finding.severity}`}>{SEVERITY_INFO[finding.severity].label}</span>
                  <span>{checkLabel(finding.check)}</span>
                </div>
                <h3 className="finding__title">{finding.title}</h3>
                <p className="finding__detail">{finding.detail}</p>
                <div className="fix">
                  <span className="fix__label">이렇게 고쳐요</span>
                  <span className="fix__body">{finding.recommendation}</span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}

function barTone(score: number) {
  if (score >= 80) return "bar__fill--good";
  if (score >= 50) return "bar__fill--mid";
  return "bar__fill--bad";
}
