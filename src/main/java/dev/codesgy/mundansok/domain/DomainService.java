package dev.codesgy.mundansok.domain;

import dev.codesgy.mundansok.common.ApiException;
import dev.codesgy.mundansok.net.DnsResolver;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DomainService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DomainRepository domains;
    private final DnsResolver dns;
    private final Clock clock;

    public DomainService(DomainRepository domains, DnsResolver dns, Clock clock) {
        this.domains = domains;
        this.dns = dns;
        this.clock = clock;
    }

    /** 같은 도메인을 다시 등록하면 기존 도메인과 토큰을 그대로 돌려준다 */
    @Transactional
    public Domain register(String input) {
        String name = DomainNames.normalize(input);
        return domains.findByName(name).orElseGet(() -> domains.save(new Domain(name, newToken(), clock.instant())));
    }

    /** _mundansok-verify.<도메인> TXT 레코드에 토큰이 있으면 소유를 인증한다 */
    @Transactional
    public Domain verify(Long id) {
        Domain domain = get(id);
        if (domain.isVerified()) return domain;

        boolean found;
        try {
            found = dns.txt(domain.verificationRecordName()).stream()
                    .anyMatch(value -> value.trim().equals(domain.verificationRecordValue()));
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DNS_UNAVAILABLE", "DNS를 조회하지 못했습니다. 잠시 후 다시 시도해 주세요");
        }
        if (!found) {
            throw new ApiException(HttpStatus.CONFLICT, "VERIFICATION_RECORD_NOT_FOUND",
                    domain.verificationRecordName() + " TXT 레코드에서 인증 값을 찾지 못했습니다. DNS 반영에는 몇 분이 걸릴 수 있어요");
        }
        domain.markVerified(clock.instant());
        return domain;
    }

    @Transactional(readOnly = true)
    public Domain get(Long id) {
        return domains.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOMAIN_NOT_FOUND", "등록되지 않은 도메인입니다"));
    }

    private static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
