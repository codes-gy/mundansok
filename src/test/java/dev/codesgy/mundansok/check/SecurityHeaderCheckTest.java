package dev.codesgy.mundansok.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codesgy.mundansok.support.FakeHttpProbe;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecurityHeaderCheckTest {

    private final FakeHttpProbe probe = new FakeHttpProbe();
    private final SecurityHeaderCheck check = new SecurityHeaderCheck(probe);

    /** 문제가 하나도 없는 응답 헤더. 테스트마다 하나씩 바꿔 쓴다 */
    private static Map<String, String> goodHeaders() {
        return new HashMap<>(Map.of(
                "Strict-Transport-Security", "max-age=31536000",
                "Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'",
                "X-Content-Type-Options", "nosniff",
                "Referrer-Policy", "strict-origin-when-cross-origin",
                "Server", "nginx"));
    }

    @Test
    void 헤더를_모두_갖추면_문제가_없다() {
        probe.respond("https://example.com/", 200, goodHeaders(), "<html>");
        assertThat(check.run("example.com")).isEmpty();
    }

    @Test
    void 헤더가_하나도_없으면_다섯_가지를_알린다() {
        probe.respond("https://example.com/", 200, Map.of(), "<html>");
        assertThat(check.run("example.com")).extracting(CheckFinding::title).containsExactly(
                "Strict-Transport-Security 헤더 없음",
                "Content-Security-Policy 헤더 없음",
                "X-Content-Type-Options 헤더 없음",
                "X-Frame-Options 헤더 없음",
                "Referrer-Policy 헤더 없음");
    }

    @Test
    void 서버_버전이_보이면_낮음() {
        Map<String, String> headers = goodHeaders();
        headers.put("Server", "Apache/2.4.41 (Ubuntu)");
        probe.respond("https://example.com/", 200, headers, "<html>");
        assertThat(check.run("example.com")).singleElement()
                .satisfies(f -> {
                    assertThat(f.title()).isEqualTo("서버 버전 노출");
                    assertThat(f.severity()).isEqualTo(Severity.LOW);
                });
    }

    @Test
    void X_Powered_By가_있으면_낮음() {
        Map<String, String> headers = goodHeaders();
        headers.put("X-Powered-By", "Express");
        probe.respond("https://example.com/", 200, headers, "<html>");
        assertThat(check.run("example.com")).singleElement()
                .satisfies(f -> {
                    assertThat(f.title()).isEqualTo("기술 스택 노출 (X-Powered-By)");
                    assertThat(f.severity()).isEqualTo(Severity.LOW);
                    assertThat(f.detail()).contains("Express");
                });
    }

    @Test
    void HSTS_유지_기간이_180일보다_짧으면_낮음() {
        Map<String, String> headers = goodHeaders();
        headers.put("Strict-Transport-Security", "max-age=86400; includeSubDomains");
        probe.respond("https://example.com/", 200, headers, "<html>");
        assertThat(check.run("example.com")).singleElement()
                .satisfies(f -> {
                    assertThat(f.title()).isEqualTo("HSTS 유지 기간이 짧음");
                    assertThat(f.severity()).isEqualTo(Severity.LOW);
                });
    }

    @Test
    void CSP가_인라인_스크립트를_허용하면_낮음() {
        Map<String, String> headers = goodHeaders();
        headers.put("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; frame-ancestors 'none'");
        probe.respond("https://example.com/", 200, headers, "<html>");
        assertThat(check.run("example.com")).extracting(CheckFinding::title)
                .containsExactly("CSP가 인라인 스크립트를 허용");
    }

    @Test
    void script_src가_없으면_default_src의_unsafe_inline을_본다() {
        Map<String, String> headers = goodHeaders();
        headers.put("Content-Security-Policy", "default-src 'self' 'unsafe-inline'; frame-ancestors 'none'");
        probe.respond("https://example.com/", 200, headers, "<html>");
        assertThat(check.run("example.com")).extracting(CheckFinding::title)
                .containsExactly("CSP가 인라인 스크립트를 허용");
    }

    @Test
    void nonce나_hash가_있으면_unsafe_inline은_무시되므로_알리지_않는다() {
        Map<String, String> headers = goodHeaders();
        headers.put("Content-Security-Policy", "script-src 'self' 'unsafe-inline' 'nonce-abc123'; frame-ancestors 'none'");
        probe.respond("https://example.com/", 200, headers, "<html>");
        assertThat(check.run("example.com")).isEmpty();
    }

    @Test
    void 쿠키에_빠진_보안_속성을_쿠키_이름과_함께_알린다() {
        Map<String, List<String>> headers = new HashMap<>();
        goodHeaders().forEach((name, value) -> headers.put(name, List.of(value)));
        headers.put("Set-Cookie", List.of(
                "SESSION=abc123secret; Path=/",
                "theme=dark; Path=/; Secure; HttpOnly; SameSite=Lax"));
        probe.respondMulti("https://example.com/", 200, headers, "<html>");

        List<CheckFinding> findings = check.run("example.com");

        assertThat(findings).extracting(CheckFinding::title).containsExactly(
                "쿠키에 Secure 속성 없음",
                "쿠키에 HttpOnly 속성 없음",
                "쿠키에 SameSite 속성 없음");
        assertThat(findings).extracting(CheckFinding::severity)
                .containsExactly(Severity.MEDIUM, Severity.LOW, Severity.LOW);
        assertThat(findings).allSatisfy(f -> {
            assertThat(f.detail()).contains("SESSION");
            assertThat(f.detail()).doesNotContain("abc123secret").doesNotContain("theme");
        });
    }

    @Test
    void 속성_이름은_대소문자를_가리지_않는다() {
        Map<String, List<String>> headers = new HashMap<>();
        goodHeaders().forEach((name, value) -> headers.put(name, List.of(value)));
        headers.put("Set-Cookie", List.of("sid=1; secure; httponly; samesite=Strict"));
        probe.respondMulti("https://example.com/", 200, headers, "<html>");
        assertThat(check.run("example.com")).isEmpty();
    }
}
