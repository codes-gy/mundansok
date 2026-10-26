package dev.codesgy.perimeter.scan;

import dev.codesgy.perimeter.common.ApiException;
import dev.codesgy.perimeter.config.ScanProperties;
import dev.codesgy.perimeter.domain.Domain;
import dev.codesgy.perimeter.domain.DomainService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ScanService {

    private final DomainService domains;
    private final ScanRepository scans;
    private final FindingRepository findings;
    private final ScanRunner runner;
    private final Executor executor;
    private final ScanProperties properties;
    private final Clock clock;

    public ScanService(DomainService domains, ScanRepository scans, FindingRepository findings, ScanRunner runner,
            @Qualifier("scanExecutor") Executor executor, ScanProperties properties, Clock clock) {
        this.domains = domains;
        this.scans = scans;
        this.findings = findings;
        this.runner = runner;
        this.executor = executor;
        this.properties = properties;
        this.clock = clock;
    }

    /** 소유 인증된 도메인만, 쿨다운이 지났을 때만 점검을 큐에 넣는다. 실제 점검은 커밋 뒤 별도 스레드에서 돈다 */
    @Transactional
    public Scan request(Long domainId) {
        Domain domain = domains.get(domainId);
        if (!domain.isVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DOMAIN_NOT_VERIFIED", "도메인 소유 인증을 먼저 마쳐 주세요");
        }
        scans.findFirstByDomainIdOrderByCreatedAtDesc(domainId).ifPresent(last -> {
            Duration since = Duration.between(last.getCreatedAt(), clock.instant());
            if (since.compareTo(properties.cooldown()) < 0) {
                long wait = properties.cooldown().minus(since).toMinutes() + 1;
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "SCAN_COOLDOWN", "같은 도메인은 " + wait + "분 뒤에 다시 점검할 수 있어요");
            }
        });

        Scan scan = scans.save(new Scan(domainId, clock.instant()));
        Long scanId = scan.getId();
        String host = domain.getName();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executor.execute(() -> runner.run(scanId, host));
            }
        });
        return scan;
    }

    @Transactional(readOnly = true)
    public Scan get(Long scanId) {
        return scans.findById(scanId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCAN_NOT_FOUND", "점검 기록을 찾을 수 없습니다"));
    }

    @Transactional(readOnly = true)
    public List<Finding> findingsOf(Long scanId) {
        return findings.findByScanId(scanId);
    }
}
