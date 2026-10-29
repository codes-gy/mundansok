"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError, registerDomain, requestScan, scanPath } from "@/lib/api";

/** 도메인을 등록하고, 이미 인증된 도메인이면 바로 점검을 시작한다 */
export function DomainForm() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!name.trim()) {
      setError("도메인을 입력해 주세요");
      return;
    }
    setPending(true);
    setError(null);
    try {
      const domain = await registerDomain(name.trim());
      if (domain.verified) {
        const scan = await requestScan(domain.id);
        router.push(scanPath(scan.id, domain.name));
        return;
      }
      router.push(`/verify?domain=${encodeURIComponent(domain.name)}`);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "요청을 처리하지 못했어요");
      setPending(false);
    }
  }

  return (
    <form className="field" onSubmit={onSubmit} noValidate>
      <label htmlFor="domain" className="field__label">
        점검할 도메인
      </label>
      <div className="field__row">
        <input
          id="domain"
          name="domain"
          type="text"
          inputMode="url"
          autoComplete="off"
          spellCheck={false}
          placeholder="example.co.kr"
          className="field__input"
          value={name}
          onChange={(event) => setName(event.target.value)}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? "domain-error" : "domain-hint"}
        />
        <button type="submit" className="button" disabled={pending}>
          {pending ? "확인하는 중…" : "점검 시작하기"}
        </button>
      </div>
      {error ? (
        <p id="domain-error" role="alert" className="field__error">
          {error}
        </p>
      ) : (
        <p id="domain-hint" className="field__hint">
          내가 관리하는 도메인만 점검할 수 있어요. 다음 단계에서 DNS로 소유를 확인해요.
        </p>
      )}
    </form>
  );
}
