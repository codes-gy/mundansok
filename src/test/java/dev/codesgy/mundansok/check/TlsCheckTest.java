package dev.codesgy.mundansok.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codesgy.mundansok.net.TlsInfo;
import dev.codesgy.mundansok.support.FakeDnsResolver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TlsCheckTest {

    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");
    private static final TlsInfo GOOD = new TlsInfo("TLSv1.3", NOW.plus(Duration.ofDays(80)), true, null);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final FakeDnsResolver dns = new FakeDnsResolver();

    @BeforeEach
    void caa가_있는_도메인() {
        dns.caa("example.com", "0 issue \"letsencrypt.org\"");
    }

    private TlsCheck check(TlsInfo info) {
        return new TlsCheck(host -> info, dns, clock);
    }

    @Test
    void 정상_인증서는_문제가_없다() {
        assertThat(check(GOOD).run("example.com")).isEmpty();
    }

    @Test
    void 만료일에_따라_위험도가_달라진다() {
        assertThat(check(new TlsInfo("TLSv1.3", NOW.minus(Duration.ofDays(1)), true, null)).run("example.com"))
                .extracting(CheckFinding::severity).containsExactly(Severity.HIGH);
        assertThat(check(new TlsInfo("TLSv1.3", NOW.plus(Duration.ofDays(10)), true, null)).run("example.com"))
                .extracting(CheckFinding::severity).containsExactly(Severity.MEDIUM);
        assertThat(check(new TlsInfo("TLSv1.3", NOW.plus(Duration.ofDays(20)), true, null)).run("example.com"))
                .extracting(CheckFinding::severity).containsExactly(Severity.LOW);
    }

    @Test
    void 이름_불일치와_오래된_버전은_높음() {
        assertThat(check(new TlsInfo("TLSv1.1", NOW.plus(Duration.ofDays(80)), false, "No subject alternative DNS name")).run("example.com"))
                .extracting(CheckFinding::title).containsExactly("신뢰할 수 없는 인증서", "오래된 TLS 버전");
    }

    @Test
    void 포트_443에_연결할_수_없으면_높음() {
        TlsCheck check = new TlsCheck(host -> {
            throw new UncheckedIOException(new IOException("Connection refused"));
        }, dns, clock);
        assertThat(check.run("example.com")).extracting(CheckFinding::severity).containsExactly(Severity.HIGH);
    }

    @Test
    void CAA_레코드가_어디에도_없으면_낮음() {
        dns.clear();
        assertThat(check(GOOD).run("shop.example.com")).singleElement().satisfies(f -> {
            assertThat(f.title()).isEqualTo("CAA 레코드 없음");
            assertThat(f.severity()).isEqualTo(Severity.LOW);
        });
    }

    @Test
    void 상위_도메인의_CAA_레코드가_하위_도메인에도_적용된다() {
        assertThat(check(GOOD).run("shop.example.com")).isEmpty();
    }

    @Test
    void DNS_조회에_실패하면_CAA는_판정하지_않는다() {
        dns.failing(true);
        assertThat(check(GOOD).run("example.com")).isEmpty();
    }
}
