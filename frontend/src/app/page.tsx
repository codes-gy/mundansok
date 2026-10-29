import { DomainForm } from "@/components/DomainForm";
import { CheckTypeIcon } from "@/components/Icons";
import { SiteHeader } from "@/components/SiteHeader";
import { CHECKS } from "@/lib/checks";

const PRINCIPLES = [
  { title: "내 도메인만", body: "DNS TXT 레코드로 소유를 확인한 도메인만 점검해요." },
  { title: "공격하지 않고 확인만", body: "일반 GET 요청, TLS 연결, DNS 조회만 해요." },
  { title: "내부망 차단", body: "사설 · 루프백 주소로 풀리는 호스트는 요청하지 않아요." },
  { title: "비밀은 남기지 않음", body: "노출 여부와 근거 요약만 저장해요. 값은 저장하지 않아요." },
];

export default function Home() {
  return (
    <>
      <SiteHeader>
        <nav aria-label="주요 메뉴" className="nav">
          <a href="#checks">점검 항목</a>
          <a href="#principles">원칙</a>
          <a href="https://github.com/codes-gy/mundansok">GitHub</a>
        </nav>
      </SiteHeader>

      <main>
        <section className="container hero">
          <div className="hero__copy">
            <p className="pill">공격 없이 확인만 하는 외부 노출 점검</p>
            <h1 className="hero__title">
              공격자가 먼저 보기 전에,
              <br />
              우리 도메인 문단속
            </h1>
            <p className="hero__lead">
              도메인을 넣으면 밖에서 보이는 약점을 찾아 쉬운 말로 알려 드려요. 무엇부터 고치면 되는지도 함께요.
            </p>
            <DomainForm />
          </div>

          <aside aria-label="결과 예시" className="card sample">
            <div className="sample__top">
              <span className="mono" style={{ fontSize: 15, color: "var(--ink-2)" }}>
                example.co.kr
              </span>
              <span>결과 예시</span>
            </div>
            <div className="score">
              <span className="score__value">51</span>
              <span className="score__max">/ 100점</span>
            </div>
            <div className="bar">
              <div className="bar__fill bar__fill--mid" style={{ width: "51%" }} />
            </div>
            <ul className="counts counts--compact">
              <li className="count sev-HIGH">
                <span>높음</span>
                <span>1</span>
              </li>
              <li className="count sev-MEDIUM">
                <span>보통</span>
                <span>2</span>
              </li>
              <li className="count sev-LOW">
                <span>낮음</span>
                <span>3</span>
              </li>
              <li className="count sev-INFO">
                <span>참고</span>
                <span>0</span>
              </li>
            </ul>
            <p className="sample__note">
              <strong style={{ color: "var(--high-fg)" }}>먼저 고칠 것</strong> · SPF가 모든 발신 서버를 허용해서 누구나
              이 도메인으로 메일을 보낼 수 있어요.
            </p>
          </aside>
        </section>

        <section id="checks" className="band">
          <div className="container section">
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              <h2 className="section__title">이렇게 다섯 군데를 확인해요</h2>
              <p className="muted">공격자가 자동 정찰로 가장 먼저 찾는 곳들이에요.</p>
            </div>
            <ul className="check-grid">
              {CHECKS.map((check) => (
                <li key={check.id} className="check-card">
                  <CheckTypeIcon check={check.id} size={28} />
                  <h3>{check.label}</h3>
                  <p>{check.summary}</p>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section id="principles" className="container section" style={{ paddingBottom: 80 }}>
          <h2 className="section__title">점검은 이 원칙을 지켜요</h2>
          <ol className="principles">
            {PRINCIPLES.map((principle) => (
              <li key={principle.title}>
                <strong>{principle.title}</strong>
                <span>{principle.body}</span>
              </li>
            ))}
          </ol>
        </section>
      </main>

      <footer className="site-footer">
        <div className="site-footer__inner">
          <span>문단속 · 도메인 외부 노출 점검</span>
          <a href="https://github.com/codes-gy/mundansok">github.com/codes-gy/mundansok</a>
        </div>
      </footer>
    </>
  );
}
