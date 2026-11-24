package dev.codesgy.mundansok.api;

import dev.codesgy.mundansok.check.Severity;
import dev.codesgy.mundansok.domain.OwnerTokens;
import dev.codesgy.mundansok.ratelimit.RateLimited;
import dev.codesgy.mundansok.scan.Finding;
import dev.codesgy.mundansok.scan.Scan;
import dev.codesgy.mundansok.scan.ScanService;
import dev.codesgy.mundansok.scan.ScanService.OwnedScan;
import dev.codesgy.mundansok.scan.ScanStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 점검 요청 · 결과 조회. 둘 다 등록할 때 받은 소유자 토큰이 있어야 한다 */
@RestController
public class ScanController {

    private final ScanService scans;

    public ScanController(ScanService scans) {
        this.scans = scans;
    }

    public record FindingResponse(String check, Severity severity, String title, String detail, String recommendation) {

        static FindingResponse of(Finding finding) {
            return new FindingResponse(finding.getCheckId(), finding.getSeverity(), finding.getTitle(), finding.getDetail(),
                    finding.getRecommendation());
        }
    }

    /**
     * 결과는 위험도가 높은 것부터.
     *
     * @param id       점검 UUID
     * @param domainId 점검을 요청한 등록의 UUID (재점검 요청에 쓴다)
     * @param domain   점검한 도메인 이름
     */
    public record ScanResponse(UUID id, UUID domainId, String domain, ScanStatus status, Integer score, String error,
            Instant createdAt, Instant finishedAt, List<FindingResponse> findings) {}

    @PostMapping("/api/domains/{domainId}/scans")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RateLimited("scan")
    public ScanResponse request(@PathVariable UUID domainId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return toResponse(scans.request(domainId, OwnerTokens.fromAuthorization(authorization)));
    }

    @GetMapping("/api/scans/{id}")
    public ScanResponse get(@PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return toResponse(scans.get(id, OwnerTokens.fromAuthorization(authorization)));
    }

    private static ScanResponse toResponse(OwnedScan owned) {
        Scan scan = owned.scan();
        List<FindingResponse> findings = owned.findings().stream()
                .sorted(Comparator.comparing(Finding::getSeverity))
                .map(FindingResponse::of)
                .toList();
        return new ScanResponse(scan.getPublicId(), owned.claim().getPublicId(), owned.claim().getDomain().getName(),
                scan.getStatus(), scan.getScore(), scan.getError(), scan.getCreatedAt(), scan.getFinishedAt(), findings);
    }
}
