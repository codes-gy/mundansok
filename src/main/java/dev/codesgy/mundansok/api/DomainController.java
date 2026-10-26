package dev.codesgy.mundansok.api;

import dev.codesgy.mundansok.domain.Domain;
import dev.codesgy.mundansok.domain.DomainService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/domains")
public class DomainController {

    private final DomainService domains;

    public DomainController(DomainService domains) {
        this.domains = domains;
    }

    public record RegisterRequest(@NotBlank @Size(max = 300) String name) {}

    /** 소유 인증 전에는 TXT 레코드 안내를 함께 준다 */
    public record DomainResponse(Long id, String name, boolean verified, Instant verifiedAt, VerificationRecord verification) {

        static DomainResponse of(Domain domain) {
            VerificationRecord record = domain.isVerified()
                    ? null
                    : new VerificationRecord("TXT", domain.verificationRecordName(), domain.verificationRecordValue());
            return new DomainResponse(domain.getId(), domain.getName(), domain.isVerified(), domain.getVerifiedAt(), record);
        }
    }

    public record VerificationRecord(String type, String name, String value) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DomainResponse register(@Valid @RequestBody RegisterRequest request) {
        return DomainResponse.of(domains.register(request.name()));
    }

    @PostMapping("/{id}/verify")
    public DomainResponse verify(@PathVariable Long id) {
        return DomainResponse.of(domains.verify(id));
    }
}
