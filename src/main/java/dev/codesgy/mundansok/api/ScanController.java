package dev.codesgy.mundansok.api;

import dev.codesgy.mundansok.check.Severity;
import dev.codesgy.mundansok.scan.Finding;
import dev.codesgy.mundansok.scan.Scan;
import dev.codesgy.mundansok.scan.ScanService;
import dev.codesgy.mundansok.scan.ScanStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    /** 결과는 위험도가 높은 것부터 */
    public record ScanResponse(Long id, Long domainId, ScanStatus status, Integer score, String error, Instant createdAt,
            Instant finishedAt, List<FindingResponse> findings) {}

    @PostMapping("/api/domains/{domainId}/scans")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ScanResponse request(@PathVariable Long domainId) {
        return toResponse(scans.request(domainId), List.of());
    }

    @GetMapping("/api/scans/{id}")
    public ScanResponse get(@PathVariable Long id) {
        Scan scan = scans.get(id);
        List<FindingResponse> findings = scans.findingsOf(id).stream()
                .sorted(Comparator.comparing(Finding::getSeverity))
                .map(FindingResponse::of)
                .toList();
        return toResponse(scan, findings);
    }

    private static ScanResponse toResponse(Scan scan, List<FindingResponse> findings) {
        return new ScanResponse(scan.getId(), scan.getDomainId(), scan.getStatus(), scan.getScore(), scan.getError(),
                scan.getCreatedAt(), scan.getFinishedAt(), findings);
    }
}
