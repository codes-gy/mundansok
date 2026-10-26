package dev.codesgy.mundansok.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codesgy.mundansok.support.FakeHttpProbe;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpsRedirectCheckTest {

    private final FakeHttpProbe probe = new FakeHttpProbe();
    private final HttpsRedirectCheck check = new HttpsRedirectCheck(probe);

    @Test
    void https로_넘기면_문제가_없다() {
        probe.respond("http://example.com/", 301, Map.of("Location", "https://example.com/"), "");
        assertThat(check.run("example.com")).isEmpty();
    }

    @Test
    void http로_그대로_응답하면_보통() {
        probe.respond("http://example.com/", 200, Map.of(), "<html>");
        assertThat(check.run("example.com")).extracting(CheckFinding::severity).containsExactly(Severity.MEDIUM);
    }

    @Test
    void 다른_http_주소로_넘기면_보통() {
        probe.respond("http://example.com/", 302, Map.of("Location", "http://www.example.com/"), "");
        assertThat(check.run("example.com")).hasSize(1);
    }

    @Test
    void 포트_80이_닫혀_있으면_문제가_아니다() {
        probe.unreachable("http://example.com/");
        assertThat(check.run("example.com")).isEmpty();
    }
}
