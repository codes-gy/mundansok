package dev.codesgy.mundansok.check;

import dev.codesgy.mundansok.net.HttpProbe;
import dev.codesgy.mundansok.net.HttpResult;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 브라우저 보호 기능을 켜는 응답 헤더, 쿠키 보안 속성, 서버 · 기술 스택 노출 */
@Component
public class SecurityHeaderCheck implements Check {

    private static final Pattern VERSION = Pattern.compile("/\\d");
    private static final Pattern MAX_AGE = Pattern.compile("max-age\\s*=\\s*\"?(\\d+)", Pattern.CASE_INSENSITIVE);
    /** HSTS preload 목록이 요구하는 최소 기간의 절반(180일). 이보다 짧으면 만료 사이에 보호가 풀리기 쉽다 */
    private static final long HSTS_MIN_SECONDS = 180L * 24 * 60 * 60;

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
        page.header("x-powered-by").ifPresent(poweredBy ->
                findings.add(new CheckFinding(id(), Severity.LOW, "기술 스택 노출 (X-Powered-By)",
                        "X-Powered-By 헤더: " + poweredBy,
                        "기능상 필요 없는 헤더라 지워 주세요 (Express: app.disable('x-powered-by'), PHP: expose_php = Off)")));
        page.header("strict-transport-security").filter(SecurityHeaderCheck::shortHsts).ifPresent(hsts ->
                findings.add(new CheckFinding(id(), Severity.LOW, "HSTS 유지 기간이 짧음",
                        "Strict-Transport-Security 헤더: " + hsts,
                        "max-age를 180일(15552000초) 이상, 보통 1년(31536000초)으로 늘려 주세요")));
        if (!csp.isEmpty() && allowsInlineScript(csp)) {
            findings.add(new CheckFinding(id(), Severity.LOW, "CSP가 인라인 스크립트를 허용",
                    "Content-Security-Policy의 스크립트 정책에 'unsafe-inline'이 있어 XSS로 심은 스크립트도 실행됩니다",
                    "'unsafe-inline'을 빼고 인라인 스크립트에는 nonce나 hash를 붙여 주세요"));
        }
        findings.addAll(cookieFindings(page.values("set-cookie")));
        return findings;
    }

    private static boolean shortHsts(String hsts) {
        Matcher maxAge = MAX_AGE.matcher(hsts);
        return !maxAge.find() || Long.parseLong(maxAge.group(1)) < HSTS_MIN_SECONDS;
    }

    // script-src 가 없으면 default-src 를 따른다. nonce · hash 가 있으면 브라우저가 unsafe-inline 을 무시한다
    private static boolean allowsInlineScript(String csp) {
        List<String> scriptSources = directive(csp, "script-src");
        List<String> sources = scriptSources != null ? scriptSources : directive(csp, "default-src");
        if (sources == null || !sources.contains("'unsafe-inline'")) return false;
        return sources.stream().noneMatch(source -> source.startsWith("'nonce-") || source.startsWith("'sha"));
    }

    private static List<String> directive(String csp, String name) {
        for (String part : csp.split(";")) {
            List<String> tokens = Arrays.asList(part.trim().toLowerCase(Locale.ROOT).split("\\s+"));
            if (tokens.getFirst().equals(name)) return tokens.subList(1, tokens.size());
        }
        return null;
    }

    // 쿠키 값은 세션 토큰일 수 있어 결과에는 이름만 남긴다. 같은 문제는 쿠키가 여러 개여도 한 번만 알린다
    private List<CheckFinding> cookieFindings(List<String> setCookies) {
        List<CheckFinding> findings = new ArrayList<>();
        addCookieFinding(findings, setCookies, attrs -> !attrs.contains("secure"), Severity.MEDIUM, "Secure",
                "HTTP로 접속했을 때 쿠키가 암호화 없이 전송돼 가로챌 수 있어요", "Set-Cookie에 Secure를 붙여 HTTPS에서만 보내게 해 주세요");
        addCookieFinding(findings, setCookies, attrs -> !attrs.contains("httponly"), Severity.LOW, "HttpOnly",
                "자바스크립트로 쿠키를 읽을 수 있어, XSS가 생기면 세션이 털릴 수 있어요",
                "로그인 · 세션 쿠키에는 HttpOnly를 붙여 주세요");
        addCookieFinding(findings, setCookies, attrs -> !attrs.contains("samesite"), Severity.LOW, "SameSite",
                "다른 사이트에서 보낸 요청에도 쿠키가 따라가 CSRF에 쓰일 수 있어요", "SameSite=Lax(또는 Strict)를 붙여 주세요");
        return findings;
    }

    private void addCookieFinding(List<CheckFinding> findings, List<String> setCookies, Predicate<Set<String>> missing,
            Severity severity, String attribute, String why, String recommendation) {
        Set<String> names = new LinkedHashSet<>();
        for (String cookie : setCookies) {
            String[] parts = cookie.split(";");
            Set<String> attrs = new LinkedHashSet<>();
            for (int i = 1; i < parts.length; i++) attrs.add(parts[i].split("=", 2)[0].trim().toLowerCase(Locale.ROOT));
            if (missing.test(attrs)) names.add(parts[0].split("=", 2)[0].trim());
        }
        if (!names.isEmpty()) {
            findings.add(new CheckFinding(id(), severity, "쿠키에 " + attribute + " 속성 없음",
                    "쿠키 " + String.join(", ", names) + " — " + why, recommendation));
        }
    }

    private CheckFinding missing(Severity severity, String header, String why, String example) {
        return new CheckFinding(id(), severity, header + " 헤더 없음", why, "응답에 추가: " + example);
    }
}
