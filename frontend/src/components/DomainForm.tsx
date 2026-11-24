"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError, domainKey, registerAndSave, savedClaim } from "@/lib/api";

/**
 * 도메인을 등록하고 인증 화면으로 넘긴다.
 * 이 브라우저에서 이미 등록한 도메인이면 새로 등록하지 않고 보관한 등록을 이어서 쓴다 (인증 화면에서 불러온다)
 */
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
      const key = domainKey(name);
      const target = savedClaim(key) ? key : (await registerAndSave(name.trim())).name;
      router.push(`/verify?domain=${encodeURIComponent(target)}`);
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
