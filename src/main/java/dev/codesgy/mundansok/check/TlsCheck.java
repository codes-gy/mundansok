package dev.codesgy.mundansok.check;

import dev.codesgy.mundansok.net.DnsResolver;
import dev.codesgy.mundansok.net.TlsInfo;
import dev.codesgy.mundansok.net.TlsInspector;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 인증서 만료 · 호스트 이름 일치 · 협상된 TLS 버전 · 인증서 발급 기관 제한(CAA) */
@Component
public class TlsCheck implements Check {

    private static final Set<String> MODERN = Set.of("TLSv1.2", "TLSv1.3");

    private final TlsInspector inspector;
    private final DnsResolver dns;
    private final Clock clock;

    public TlsCheck(TlsInspector inspector, DnsResolver dns, Clock clock) {
        this.inspector = inspector;
        this.dns = dns;
        this.clock = clock;
    }

    @Override
    public String id() {
        return "tls";
    }

    @Override
    public List<CheckFinding> run(String host) {
        TlsInfo tls;
        try {
            tls = inspector.inspect(host);
        } catch (UncheckedIOException e) {
            return List.of(new CheckFinding(id(), Severity.HIGH, "HTTPS(443)에 연결할 수 없음",
                    host + ":443 연결 실패 — " + e.getCause().getMessage(),
                    "웹 서버에 TLS 인증서를 설치하고 443 포트를 열어 주세요. 무료 인증서는 Let's Encrypt로 발급할 수 있어요"));
        }

        List<CheckFinding> findings = new ArrayList<>();
        long daysLeft = Duration.between(clock.instant(), tls.notAfter()).toDays();
        if (tls.notAfter().isBefore(clock.instant())) {
            findings.add(new CheckFinding(id(), Severity.HIGH, "TLS 인증서 만료",
                    "인증서가 " + tls.notAfter() + "에 만료됐습니다",
                    "인증서를 즉시 갱신하고, 자동 갱신(certbot renew 등)을 설정해 주세요"));
        } else if (daysLeft < 14) {
            findings.add(new CheckFinding(id(), Severity.MEDIUM, "TLS 인증서 곧 만료",
                    "인증서가 " + daysLeft + "일 뒤 만료됩니다", "만료 전에 갱신하고 자동 갱신을 설정해 주세요"));
        } else if (daysLeft < 30) {
            findings.add(new CheckFinding(id(), Severity.LOW, "TLS 인증서 만료 30일 이내",
                    "인증서가 " + daysLeft + "일 뒤 만료됩니다", "자동 갱신이 설정돼 있는지 확인해 주세요"));
        }
        if (!tls.hostnameValid()) {
            findings.add(new CheckFinding(id(), Severity.HIGH, "신뢰할 수 없는 인증서",
                    "인증서 검증 실패 — " + tls.error(),
                    "도메인 이름이 들어간, 공인 인증 기관이 발급한 인증서로 바꾸고 중간 인증서까지 함께 설치해 주세요"));
        }
        if (!MODERN.contains(tls.protocol())) {
            findings.add(new CheckFinding(id(), Severity.HIGH, "오래된 TLS 버전",
                    "협상된 버전: " + tls.protocol(), "TLS 1.2 이상만 허용하도록 웹 서버 설정을 바꿔 주세요"));
        }
        if (lacksCaa(host)) {
            findings.add(new CheckFinding(id(), Severity.LOW, "CAA 레코드 없음",
                    host + "와 상위 도메인에 CAA 레코드가 없어 어느 인증 기관이든 이 도메인의 인증서를 발급할 수 있어요",
                    "쓰는 인증 기관만 허용하는 CAA 레코드를 추가해 주세요 (예: example.com. CAA 0 issue \"letsencrypt.org\")"));
        }
        return findings;
    }

    // CAA 는 가장 가까운 상위 도메인의 레코드가 적용된다 (RFC 8659). 최상위 도메인(kr, com)은 묻지 않는다.
    // DNS 조회가 실패하면 판단할 수 없으므로 알리지 않는다
    private boolean lacksCaa(String host) {
        try {
            for (String name = host; name.indexOf('.') > 0; name = name.substring(name.indexOf('.') + 1)) {
                if (!dns.caa(name).isEmpty()) return false;
            }
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
