package dev.codesgy.mundansok.domain;

import dev.codesgy.mundansok.common.ApiException;
import dev.codesgy.mundansok.net.DnsResolver;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DomainService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DomainRepository domains;
    private final DomainClaimRepository claims;
    private final DnsResolver dns;
    private final Clock clock;

    public DomainService(DomainRepository domains, DomainClaimRepository claims, DnsResolver dns, Clock clock) {
        this.domains = domains;
        this.claims = claims;
        this.dns = dns;
        this.clock = clock;
    }

    /** 등록 결과. ownerToken 평문은 이때 한 번만 돌려주고 저장하지 않는다 */
    public record Registration(DomainClaim claim, String ownerToken) {}

    /** 점검 자리를 받은 기록. 점검을 넣지 못하면 {@link #releaseScanSlot}로 되돌린다 */
    public record ScanSlot(Long domainId, Instant taken, Instant previous) {}

    /**
     * 등록할 때마다 새 등록(claim)과 소유자 토큰을 만든다.
     * 같은 도메인이 이미 등록돼 있어도 기존 등록의 토큰 · 인증 상태는 돌려주지 않는다 — 각자 자기 TXT 값으로 인증해야 한다.
     */
    @Transactional
    public Registration register(String input) {
        String name = DomainNames.normalize(input);
        Instant now = now();
        Domain domain = domains.findByName(name).orElseGet(() -> domains.save(new Domain(name, now)));
        String ownerToken = OwnerTokens.generate();
        DomainClaim claim = claims.save(
                new DomainClaim(UUID.randomUUID(), domain, OwnerTokens.hash(ownerToken), newVerificationToken(), now));
        return new Registration(claim, ownerToken);
    }

    /** 소유자 토큰이 맞는 등록만 돌려준다. 없는 id와 틀린 토큰은 구분하지 않고 404 */
    @Transactional(readOnly = true)
    public DomainClaim authenticate(UUID publicId, String ownerToken) {
        requireToken(ownerToken);
        return claims.findByPublicId(publicId)
                .filter(claim -> claim.ownedBy(ownerToken))
                .orElseThrow(DomainService::notFound);
    }

    /** 점검 결과처럼 내부 id로 등록을 찾을 때. 토큰이 맞지 않으면 notFound를 던진다 */
    @Transactional(readOnly = true)
    public DomainClaim authenticate(Long claimId, String ownerToken, ApiException notFound) {
        requireToken(ownerToken);
        if (claimId == null) throw notFound;
        return claims.findById(claimId).filter(claim -> claim.ownedBy(ownerToken)).orElseThrow(() -> notFound);
    }

    /** _mundansok-verify.<도메인> TXT 레코드에 이 등록의 값이 있으면 소유를 인증한다 */
    @Transactional
    public DomainClaim verify(UUID publicId, String ownerToken) {
        DomainClaim claim = authenticate(publicId, ownerToken);
        if (!recordPresent(claim)) {
            throw new ApiException(HttpStatus.CONFLICT, "VERIFICATION_RECORD_NOT_FOUND",
                    claim.verificationRecordName() + " TXT 레코드에서 인증 값을 찾지 못했습니다. DNS 반영에는 몇 분이 걸릴 수 있어요");
        }
        claim.markVerified(now());
        return claim;
    }

    /**
     * 점검 직전 확인. 한 번 인증했어도 도메인 주인이 바뀌었거나 레코드를 지웠을 수 있으므로 TXT 레코드를 매번 다시 본다.
     */
    @Transactional
    public DomainClaim requireVerifiedNow(UUID publicId, String ownerToken) {
        DomainClaim claim = authenticate(publicId, ownerToken);
        if (!claim.isVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DOMAIN_NOT_VERIFIED", "도메인 소유 인증을 먼저 마쳐 주세요");
        }
        if (!recordPresent(claim)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "VERIFICATION_EXPIRED",
                    claim.verificationRecordName() + " TXT 레코드에서 인증 값을 찾지 못했습니다. 점검할 때마다 소유를 다시 확인하니 레코드를 남겨 두세요");
        }
        claim.markVerified(now());
        return claim;
    }

    /** 같은 도메인은 cooldown에 한 번만 점검한다. 확인과 기록을 조건부 UPDATE 한 번으로 해 동시 요청에도 하나만 통과한다 */
    @Transactional
    public ScanSlot acquireScanSlot(Domain domain, Duration cooldown) {
        Instant now = now();
        Instant previous = domain.getLastScanRequestedAt();
        if (domains.acquireScanSlot(domain.getId(), now, now.minus(cooldown)) == 1) {
            return new ScanSlot(domain.getId(), now, previous);
        }
        Instant last = domains.findLastScanRequestedAt(domain.getId()).orElse(now);
        Duration since = Duration.between(last, now);
        long wait = Math.max(1, cooldown.minus(since).toMinutes() + 1);
        throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "SCAN_COOLDOWN", "같은 도메인은 " + wait + "분 뒤에 다시 점검할 수 있어요");
    }

    @Transactional
    public void releaseScanSlot(ScanSlot slot) {
        domains.releaseScanSlot(slot.domainId(), slot.taken(), slot.previous());
    }

    private boolean recordPresent(DomainClaim claim) {
        try {
            return dns.txt(claim.verificationRecordName()).stream()
                    .anyMatch(value -> value.trim().equals(claim.verificationRecordValue()));
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DNS_UNAVAILABLE", "DNS를 조회하지 못했습니다. 잠시 후 다시 시도해 주세요");
        }
    }

    // DB 정밀도(마이크로초)보다 잘게 저장되지 않게 밀리초로 자른다 — releaseScanSlot이 값 비교로 동작한다
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static void requireToken(String ownerToken) {
        if (ownerToken == null || ownerToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "OWNER_TOKEN_REQUIRED",
                    "등록할 때 받은 소유자 토큰을 Authorization: Bearer 헤더로 보내 주세요");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "DOMAIN_NOT_FOUND", "등록되지 않은 도메인입니다");
    }

    private static String newVerificationToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
