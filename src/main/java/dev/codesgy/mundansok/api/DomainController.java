package dev.codesgy.mundansok.api;

import dev.codesgy.mundansok.domain.DomainClaim;
import dev.codesgy.mundansok.domain.DomainService;
import dev.codesgy.mundansok.domain.DomainService.Registration;
import dev.codesgy.mundansok.domain.OwnerTokens;
import dev.codesgy.mundansok.ratelimit.RateLimited;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 도메인 등록 · 소유 인증. 등록 응답의 ownerToken을 이후 모든 요청에 {@code Authorization: Bearer <ownerToken>}으로 보낸다.
 * id는 등록 한 건(claim)의 UUID다.
 */
@RestController
@RequestMapping("/api/domains")
public class DomainController {

    private final DomainService domains;

    public DomainController(DomainService domains) {
        this.domains = domains;
    }

    public record RegisterRequest(@NotBlank @Size(max = 300) String name) {}

    /**
     * TXT 레코드 안내는 인증 뒤에도 준다 — 점검할 때마다 레코드를 다시 확인하기 때문이다(소유자 본인에게만 보인다).
     * ownerToken은 등록 응답에서만 채우고, 다시 받을 방법은 없다.
     */
    public record DomainResponse(UUID id, String name, boolean verified, Instant verifiedAt, VerificationRecord verification,
            String ownerToken) {

        static DomainResponse of(DomainClaim claim) {
            return of(claim, null);
        }

        static DomainResponse of(DomainClaim claim, String ownerToken) {
            VerificationRecord record = new VerificationRecord("TXT", claim.verificationRecordName(), claim.verificationRecordValue());
            return new DomainResponse(claim.getPublicId(), claim.getDomain().getName(), claim.isVerified(), claim.getVerifiedAt(),
                    record, ownerToken);
        }
    }

    public record VerificationRecord(String type, String name, String value) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimited("register")
    public DomainResponse register(@Valid @RequestBody RegisterRequest request) {
        Registration registration = domains.register(request.name());
        return DomainResponse.of(registration.claim(), registration.ownerToken());
    }

    @GetMapping("/{id}")
    public DomainResponse get(@PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return DomainResponse.of(domains.authenticate(id, OwnerTokens.fromAuthorization(authorization)));
    }

    @PostMapping("/{id}/verify")
    @RateLimited("verify")
    public DomainResponse verify(@PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return DomainResponse.of(domains.verify(id, OwnerTokens.fromAuthorization(authorization)));
    }
}
