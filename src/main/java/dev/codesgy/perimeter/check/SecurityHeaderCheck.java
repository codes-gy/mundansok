package dev.codesgy.perimeter.check;

import dev.codesgy.perimeter.net.HttpProbe;
import dev.codesgy.perimeter.net.HttpResult;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 브라우저 보호 기능을 켜는 응답 헤더와 서버 버전 노출 */
@Component
public class SecurityHeaderCheck implements Check {

    private static final Pattern VERSION = Pattern.compile("/\\d");

    private final HttpProbe probe;

    public SecurityHeaderCheck(HttpProbe probe) {
        this.probe = probe;
    }

    @Override
    public String id() {
        return "security-headers";
    }

    @Override
    public List<CheckFinding> run(String host) {
        HttpResult page;
        try {
            page = probe.get("https://" + host + "/");
        } catch (UncheckedIOException e) {
            return List.of(); // HTTPS 연결 문제는 TLS 점검에서 알린다
        }

        List<CheckFinding> findings = new ArrayList<>();
        String csp = page.header("content-security-policy").orElse("");
        if (page.header("strict-transport-security").isEmpty()) {
            findings.add(missing(Severity.MEDIUM, "Strict-Transport-Security",
                    "브라우저가 이 사이트를 항상 HTTPS로만 열도록 합니다. 중간자 공격으로 HTTP로 끌어내리는 걸 막아요",
                    "Strict-Transport-Security: max-age=31536000; includeSubDomains"));
        }
        if (csp.isEmpty()) {
            findings.add(missing(Severity.MEDIUM, "Content-Security-Policy",
                    "허용한 곳의 스크립트만 실행되게 해 XSS 피해를 줄입니다",
                    "Content-Security-Policy: default-src 'self' (사이트에 맞게 출처를 추가)"));
        }
        if (!page.header("x-content-type-options").map(v -> v.equalsIgnoreCase("nosniff")).orElse(false)) {
            findings.add(missing(Severity.LOW, "X-Content-Type-Options",
                    "파일 형식을 브라우저가 멋대로 추측해 스크립트로 실행하는 걸 막습니다", "X-Content-Type-Options: nosniff"));
        }
        if (page.header("x-frame-options").isEmpty() && !csp.toLowerCase(Locale.ROOT).contains("frame-ancestors")) {
            findings.add(missing(Severity.MEDIUM, "X-Frame-Options",
                    "다른 사이트가 이 페이지를 몰래 겹쳐 띄워 클릭을 가로채는 공격(클릭재킹)을 막습니다",
                    "X-Frame-Options: DENY 또는 CSP frame-ancestors 'none'"));
        }
        if (page.header("referrer-policy").isEmpty()) {
            findings.add(missing(Severity.LOW, "Referrer-Policy",
                    "다른 사이트로 이동할 때 주소에 담긴 정보가 새는 걸 줄입니다", "Referrer-Policy: strict-origin-when-cross-origin"));
        }
        page.header("server").filter(server -> VERSION.matcher(server).find()).ifPresent(server ->
                findings.add(new CheckFinding(id(), Severity.LOW, "서버 버전 노출",
                        "Server 헤더: " + server,
                        "공격자가 버전별 알려진 취약점을 고르지 못하게 Server 헤더에서 버전을 숨겨 주세요 (nginx: server_tokens off)")));
        return findings;
    }

    private CheckFinding missing(Severity severity, String header, String why, String example) {
        return new CheckFinding(id(), severity, header + " 헤더 없음", why, "응답에 추가: " + example);
    }
}
