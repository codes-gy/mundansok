package dev.codesgy.mundansok.check;

import dev.codesgy.mundansok.net.DnsResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 회사 도메인으로 위조 메일을 보내지 못하게 하는 SPF · DMARC 설정 */
@Component
public class EmailAuthCheck implements Check {

    private final DnsResolver dns;

    public EmailAuthCheck(DnsResolver dns) {
        this.dns = dns;
    }

    @Override
    public String id() {
        return "email-auth";
    }

    @Override
    public List<CheckFinding> run(String host) {
        List<String> rootTxt;
        List<String> dmarcTxt;
        try {
            rootTxt = dns.txt(host);
            dmarcTxt = dns.txt("_dmarc." + host);
        } catch (IllegalStateException e) {
            // 조회 실패를 "레코드 없음"으로 판정하면 오탐이 되므로 따로 알린다
            return List.of(new CheckFinding(id(), Severity.INFO, "메일 보안 레코드를 확인하지 못함",
                    "DNS 조회 실패 — " + e.getMessage(), "잠시 후 다시 점검해 주세요"));
        }
        List<CheckFinding> findings = new ArrayList<>();

        Optional<String> spf = rootTxt.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith("v=spf1")).findFirst();
        if (spf.isEmpty()) {
            findings.add(new CheckFinding(id(), Severity.MEDIUM, "SPF 레코드 없음",
                    host + " TXT 레코드에 v=spf1 이 없습니다",
                    "메일을 보내는 서버만 허용하는 SPF를 추가해 주세요. 예: v=spf1 include:_spf.google.com -all"));
        } else if (spf.get().contains("+all")) {
            findings.add(new CheckFinding(id(), Severity.HIGH, "SPF가 모든 발신 서버를 허용",
                    "SPF: " + spf.get(), "+all 을 -all(또는 ~all)로 바꿔 허용한 서버만 메일을 보낼 수 있게 해 주세요"));
        }

        Optional<String> dmarc = dmarcTxt.stream()
                .filter(v -> v.toUpperCase(Locale.ROOT).startsWith("V=DMARC1")).findFirst();
        if (dmarc.isEmpty()) {
            findings.add(new CheckFinding(id(), Severity.MEDIUM, "DMARC 레코드 없음",
                    "_dmarc." + host + " TXT 레코드가 없습니다",
                    "SPF · DKIM 검사에 실패한 위조 메일을 어떻게 처리할지 정하는 DMARC를 추가해 주세요. 예: v=DMARC1; p=quarantine; rua=mailto:dmarc@" + host));
        } else if (dmarc.get().replace(" ", "").toLowerCase(Locale.ROOT).contains("p=none")) {
            findings.add(new CheckFinding(id(), Severity.LOW, "DMARC가 감시만 하고 막지 않음",
                    "DMARC: " + dmarc.get(), "보고서로 정상 발신처를 확인한 뒤 p=quarantine 또는 p=reject 로 올려 주세요"));
        }
        return findings;
    }
}
