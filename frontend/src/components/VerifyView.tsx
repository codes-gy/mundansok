"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiError, type DomainResponse, registerDomain, requestScan, scanPath, verifyDomain } from "@/lib/api";
import { AlertIcon } from "./Icons";
import { SiteHeader } from "./SiteHeader";
import { Stepper } from "./Stepper";

export function VerifyView() {
  const router = useRouter();
  const name = useSearchParams().get("domain") ?? "";
  const [domain, setDomain] = useState<DomainResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [error, setError] = useState<{ title: string; body: string } | null>(null);
  const [pending, setPending] = useState(false);

  // 등록은 같은 도메인이면 기존 인증 값을 돌려주므로, 새로고침해도 같은 레코드가 나온다
  useEffect(() => {
    if (!name) return;
    let cancelled = false;
    registerDomain(name)
      .then((result) => {
        if (!cancelled) setDomain(result);
      })
      .catch((e) => {
        if (!cancelled) setLoadError(e instanceof ApiError ? e.message : "도메인 정보를 불러오지 못했어요");
      });
    return () => {
      cancelled = true;
    };
  }, [name]);

  async function onVerify() {
    if (!domain) return;
    setPending(true);
    setError(null);
    try {
      const verified = domain.verified ? domain : await verifyDomain(domain.id);
      setDomain(verified);
      const scan = await requestScan(verified.id);
      router.push(scanPath(scan.id, verified.name));
    } catch (e) {
      if (e instanceof ApiError && e.code === "VERIFICATION_RECORD_NOT_FOUND") {
        setError({
          title: "아직 레코드를 찾지 못했어요",
          body: "DNS에 반영되기까지 몇 분에서 최대 1시간쯤 걸릴 수 있어요. 이름과 값을 다시 확인하고 잠시 뒤에 눌러 주세요.",
        });
      } else {
        setError({
          title: "진행하지 못했어요",
          body: e instanceof ApiError ? e.message : "잠시 후 다시 시도해 주세요",
        });
      }
      setPending(false);
    }
  }

  if (!name || loadError) {
    return (
      <>
        <SiteHeader />
        <main className="container container--narrow page">
          <h1 className="title">{loadError ?? "점검할 도메인을 먼저 입력해 주세요"}</h1>
          <Link href="/" className="button" style={{ alignSelf: "flex-start" }}>
            도메인 입력하기
          </Link>
        </main>
      </>
    );
  }

  return (
    <>
      <SiteHeader domain={domain?.name ?? name} verified={domain?.verified} />
      <main className="container container--narrow page">
        <Stepper current={1} />

        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <h1 className="title">
            {domain?.verified ? "이미 소유 인증을 마친 도메인이에요" : "이 도메인의 주인인지 확인할게요"}
          </h1>
          <p className="lead">
            {domain?.verified
              ? "바로 점검을 시작할 수 있어요."
              : "DNS 관리 화면에서 아래 TXT 레코드를 하나 추가해 주세요. 점검이 끝나면 지워도 돼요."}
          </p>
        </div>

        {error && (
          <div role="alert" className="alert">
            <AlertIcon size={22} />
            <div className="alert__body">
              <strong>{error.title}</strong>
              <span>{error.body}</span>
            </div>
          </div>
        )}

        {!domain && <p className="muted">인증 정보를 불러오는 중이에요…</p>}

        {domain?.verification && (
          <>
            <section aria-label="추가할 TXT 레코드" className="card" style={{ overflow: "hidden" }}>
              <div className="record-row">
                <span className="record-row__label">유형</span>
                <span className="mono" style={{ fontWeight: 500 }}>
                  {domain.verification.type}
                </span>
              </div>
              <RecordRow label="이름" value={domain.verification.name} />
              <RecordRow label="값" value={domain.verification.value} />
            </section>

            <section style={{ display: "flex", flexDirection: "column", gap: 16 }}>
              <h2 className="section-title">추가하는 방법</h2>
              <ol className="howto">
                <li>도메인을 산 곳(가비아, Cloudflare, Route 53 등)의 DNS 관리 화면을 열어요.</li>
                <li>
                  레코드 추가에서 유형을 <strong>TXT</strong>로 고르고, 위의 이름과 값을 붙여넣어요.
                </li>
                <li>
                  저장한 뒤 이 화면으로 돌아와 <strong>인증 확인</strong>을 눌러요.
                </li>
              </ol>
              <p className="field__hint">
                이름 칸에 도메인이 자동으로 붙는 곳도 있어요. 그럴 땐 <code>_mundansok-verify</code>까지만 넣어요.
              </p>
            </section>
          </>
        )}

        <div className="actions">
          <button type="button" className="button" onClick={onVerify} disabled={!domain || pending}>
            {pending ? "확인하는 중…" : domain?.verified ? "점검 시작하기" : "인증 확인"}
          </button>
          <Link href="/" className="link-button">
            다른 도메인 넣기
          </Link>
        </div>
      </main>
    </>
  );
}

function RecordRow({ label, value }: { label: string; value: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  return (
    <div className="record-row">
      <span className="record-row__label">{label}</span>
      <code className="record-row__value">{value}</code>
      <button type="button" className="button button--outline" onClick={copy} aria-label={`${label} 복사`}>
        {copied ? "복사됨" : "복사"}
      </button>
      <span className="sr-only" aria-live="polite">
        {copied ? `${label}을 복사했어요` : ""}
      </span>
    </div>
  );
}
