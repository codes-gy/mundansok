package dev.codesgy.perimeter.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codesgy.perimeter.support.FakeHttpProbe;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecurityHeaderCheckTest {

    private final FakeHttpProbe probe = new FakeHttpProbe();
    private final SecurityHeaderCheck check = new SecurityHeaderCheck(probe);

    @Test
    void 헤더를_모두_갖추면_문제가_없다() {
        probe.respond("https://example.com/", 200, Map.of(
                "Strict-Transport-Security", "max-age=31536000",
                "Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'",
                "X-Content-Type-Options", "nosniff",
                "Referrer-Policy", "strict-origin-when-cross-origin",
                "Server", "nginx"), "<html>");
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
        probe.respond("https://example.com/", 200, Map.of(
                "Strict-Transport-Security", "max-age=31536000",
                "Content-Security-Policy", "default-src 'self'",
                "X-Frame-Options", "DENY",
                "X-Content-Type-Options", "nosniff",
                "Referrer-Policy", "no-referrer",
                "Server", "Apache/2.4.41 (Ubuntu)"), "<html>");
        assertThat(check.run("example.com")).singleElement()
                .satisfies(f -> {
                    assertThat(f.title()).isEqualTo("서버 버전 노출");
                    assertThat(f.severity()).isEqualTo(Severity.LOW);
                });
    }
}
