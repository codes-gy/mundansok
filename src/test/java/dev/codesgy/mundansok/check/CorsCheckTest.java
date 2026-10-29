package dev.codesgy.mundansok.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codesgy.mundansok.support.FakeHttpProbe;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CorsCheckTest {

    private final FakeHttpProbe probe = new FakeHttpProbe();
    private final CorsCheck check = new CorsCheck(probe);

    @Test
    void 확인용_출처를_Origin_헤더로_보낸다() {
        check.run("example.com");
        assertThat(probe.lastRequestHeaders("https://example.com/")).containsEntry("Origin", CorsCheck.PROBE_ORIGIN);
    }

    @Test
    void 아무_출처나_그대로_허용하고_인증_정보까지_허용하면_높음() {
        probe.respond("https://example.com/", 200, Map.of(
                "Access-Control-Allow-Origin", CorsCheck.PROBE_ORIGIN,
                "Access-Control-Allow-Credentials", "true"), "<html>");
        assertThat(check.run("example.com")).singleElement().satisfies(f -> {
            assertThat(f.title()).isEqualTo("CORS가 모든 출처에 로그인 정보까지 허용");
            assertThat(f.severity()).isEqualTo(Severity.HIGH);
        });
    }

    @Test
    void 아무_출처나_허용하지만_인증_정보는_막으면_낮음() {
        probe.respond("https://example.com/", 200, Map.of("Access-Control-Allow-Origin", CorsCheck.PROBE_ORIGIN), "<html>");
        assertThat(check.run("example.com")).singleElement().satisfies(f -> {
            assertThat(f.title()).isEqualTo("CORS가 모든 출처를 허용");
            assertThat(f.severity()).isEqualTo(Severity.LOW);
        });
    }

    @Test
    void 별표는_브라우저가_인증_정보와_함께_쓰지_않으므로_알리지_않는다() {
        probe.respond("https://example.com/", 200, Map.of(
                "Access-Control-Allow-Origin", "*",
                "Access-Control-Allow-Credentials", "true"), "<html>");
        assertThat(check.run("example.com")).isEmpty();
    }

    @Test
    void CORS_헤더가_없거나_연결이_안_되면_알리지_않는다() {
        probe.respond("https://example.com/", 200, Map.of(), "<html>");
        assertThat(check.run("example.com")).isEmpty();

        probe.unreachable("https://example.com/");
        assertThat(check.run("example.com")).isEmpty();
    }
}
