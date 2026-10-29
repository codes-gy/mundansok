"use client";

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiError, getScan, type ScanResponse } from "@/lib/api";
import { CHECKS } from "@/lib/checks";
import { AlertIcon } from "./Icons";
import { Report } from "./Report";
import { SiteHeader } from "./SiteHeader";
import { Stepper } from "./Stepper";

const POLL_MS = 2000;

/** 점검이 끝날 때까지 2초마다 결과를 다시 불러온다 */
export function ScanView() {
  const { id } = useParams<{ id: string }>();
  const domain = useSearchParams().get("domain");
  const [scan, setScan] = useState<ScanResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    async function load() {
      try {
        const result = await getScan(id);
        if (cancelled) return;
        setScan(result);
        setError(null);
        if (result.status === "QUEUED" || result.status === "RUNNING") {
          timer = setTimeout(load, POLL_MS);
        }
      } catch (e) {
        if (cancelled) return;
        setError(e instanceof ApiError ? e.message : "결과를 불러오지 못했어요");
        // 찾을 수 없는 점검은 다시 불러와도 같다
        if (!(e instanceof ApiError && e.status === 404)) {
          timer = setTimeout(load, POLL_MS * 2);
        }
      }
    }

    load();
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [id]);

  const done = scan?.status === "DONE";

  return (
    <>
      <SiteHeader domain={domain} verified={!!domain} />
      <main className={done ? "container page" : "container container--narrow page"}>
        {done && scan ? (
          <Report scan={scan} domain={domain} />
        ) : (
          <>
            <Stepper current={2} />
            {error && !scan ? (
              <section role="alert" className="card status-card status-card--failed">
                <AlertIcon size={48} />
                <h1 className="title">{error}</h1>
                <Link href="/" className="button">
                  처음으로
                </Link>
              </section>
            ) : scan?.status === "FAILED" ? (
              <section role="alert" className="card status-card status-card--failed">
                <span style={{ color: "#b42318" }}>
                  <AlertIcon size={48} />
                </span>
                <h1 className="title">점검을 끝내지 못했어요</h1>
                <p className="lead">{scan.error ?? "잠시 후 다시 시도해 주세요."}</p>
                <Link href="/" className="button">
                  다시 시작하기
                </Link>
              </section>
            ) : (
              <Progress queued={scan?.status === "QUEUED"} domain={domain} />
            )}
            <section style={{ display: "flex", flexDirection: "column", gap: 16 }}>
              <h2 className="section-title">확인하는 항목</h2>
              <ul className="check-list">
                {CHECKS.map((check) => (
                  <li key={check.id}>
                    <strong>{check.label}</strong>
                    <span>{check.summary}</span>
                  </li>
                ))}
              </ul>
              <p className="field__hint">
                공격하지 않고 일반 요청과 DNS 조회만 해요. 이 화면은 점검이 끝나면 결과로 바뀌어요.
              </p>
            </section>
          </>
        )}
      </main>
    </>
  );
}

function Progress({ queued, domain }: { queued: boolean; domain: string | null }) {
  return (
    <section aria-live="polite" className="card status-card">
      <svg aria-hidden="true" className="spinner" viewBox="0 0 50 50">
        <circle cx="25" cy="25" r="20" fill="none" stroke="#eaecf0" strokeWidth="5" />
        <path d="M25 5a20 20 0 0 1 20 20" fill="none" stroke="#0e6b58" strokeWidth="5" strokeLinecap="round" />
      </svg>
      <h1 className="title">
        {queued ? "점검 순서를 기다리고 있어요" : `${domain ?? "도메인"}을 점검하고 있어요`}
      </h1>
      <p className="lead">
        {queued
          ? "앞선 점검이 끝나면 바로 시작해요. 창을 닫아도 점검은 계속돼요."
          : "다섯 가지 항목을 확인하는 중이에요. 창을 닫아도 점검은 계속돼요."}
      </p>
    </section>
  );
}
