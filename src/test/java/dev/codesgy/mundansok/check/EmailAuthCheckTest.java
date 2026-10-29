package dev.codesgy.mundansok.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codesgy.mundansok.support.FakeDnsResolver;
import org.junit.jupiter.api.Test;

class EmailAuthCheckTest {

    private final FakeDnsResolver dns = new FakeDnsResolver();
    private final EmailAuthCheck check = new EmailAuthCheck(dns);

    @Test
    void SPF와_DMARC가_제대로_있으면_문제가_없다() {
        dns.txt("example.com", "google-site-verification=abc", "v=spf1 include:_spf.google.com -all");
        dns.txt("_dmarc.example.com", "v=DMARC1; p=reject; rua=mailto:dmarc@example.com");
        assertThat(check.run("example.com")).isEmpty();
    }

    @Test
    void 레코드가_없으면_둘_다_알린다() {
        assertThat(check.run("example.com")).extracting(CheckFinding::title).containsExactly("SPF 레코드 없음", "DMARC 레코드 없음");
    }

    @Test
    void 모두_허용하는_SPF는_높음_감시만_하는_DMARC는_낮음() {
        dns.txt("example.com", "v=spf1 +all");
        dns.txt("_dmarc.example.com", "v=DMARC1; p=none");
        assertThat(check.run("example.com")).extracting(CheckFinding::severity).containsExactly(Severity.HIGH, Severity.LOW);
    }

    @Test
    void DNS_조회에_실패하면_없다고_판정하지_않는다() {
        dns.failing(true);
        assertThat(check.run("example.com")).extracting(CheckFinding::severity).containsExactly(Severity.INFO);
    }
}
