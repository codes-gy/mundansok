package dev.codesgy.mundansok.scan;

import dev.codesgy.mundansok.common.ApiException;
import dev.codesgy.mundansok.config.ScanProperties;
import dev.codesgy.mundansok.domain.Domain;
import dev.codesgy.mundansok.domain.DomainClaim;
import dev.codesgy.mundansok.domain.DomainService;
import dev.codesgy.mundansok.domain.DomainService.ScanSlot;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final DomainService domains;
    private final ScanRepository scans;
    private final FindingRepository findings;
    private final ScanRunner runner;
    private final Executor executor;
    private final ScanProperties properties;
    private final Clock clock;
    private final ScanMetrics metrics;
    private final TransactionTemplate newTx;

    public ScanService(DomainService domains, ScanRepository scans, FindingRepository findings, ScanRunner runner,
            @Qualifier("scanExecutor") Executor executor, ScanProperties properties, Clock clock, ScanMetrics metrics,
            PlatformTransactionManager transactionManager) {
        this.domains = domains;
        this.scans = scans;
        this.findings = findings;
        this.runner = runner;
        this.executor = executor;
        this.properties = properties;
        this.clock = clock;
        this.metrics = metrics;
        // afterCommit 안에서는 끝난 트랜잭션에 묶이지 않도록 새 트랜잭션으로 지운다
        this.newTx = new TransactionTemplate(transactionManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 점검 기록과 그 점검을 요청한 등록. 응답을 만들 때 도메인 이름 · 등록 id가 필요하다 */
    public record OwnedScan(Scan scan, DomainClaim claim, List<Finding> findings) {}

    /**
     * 소유자 토큰이 맞고, 지금도 TXT 레코드로 소유가 확인되며, 쿨다운이 지났을 때만 점검을 큐에 넣는다.
     * 실제 점검은 커밋 뒤 별도 스레드에서 돈다.
     */
    @Transactional
    public OwnedScan request(UUID claimId, String ownerToken) {
        DomainClaim claim = domains.requireVerifiedNow(claimId, ownerToken);
        Domain domain = claim.getDomain();
        ScanSlot slot = domains.acquireScanSlot(domain, properties.cooldown());

        Scan scan = scans.save(new Scan(domain.getId(), claim.getId(), clock.instant()));
        Long scanId = scan.getId();
        String host = domain.getName();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    executor.execute(() -> runner.run(scanId, host));
                } catch (RejectedExecutionException e) {
                    rejected(scanId, slot);
                }
            }
        });
        return new OwnedScan(scan, claim, List.of());
    }

    /**
     * 점검 풀(스레드 + 대기열)이 가득 차 점검을 넣지 못한 경우.
     * 이미 커밋된 QUEUED 기록은 아무도 돌리지 않으니 지우고, 503으로 돌려보낸다.
     * FAILED로 남기지 않는 이유: 응답이 오류라 화면은 점검 id를 받지 못하고, 쿨다운에 걸리면 "잠시 후 다시"가 거짓말이 된다.
     * 그래서 받아 둔 쿨다운 자리도 함께 되돌린다.
     * 대상에 요청을 하나도 보내지 않았으니 남길 근거도 없다. 대신 로그와 mundansok.scan.rejected 지표로 남긴다.
     */
    private void rejected(Long scanId, ScanSlot slot) {
        log.warn("점검 풀이 가득 차 점검을 받지 못했습니다: scanId={}, domainId={}", scanId, slot.domainId());
        metrics.scanRejected();
        try {
            newTx.executeWithoutResult(status -> {
                scans.deleteById(scanId);
                domains.releaseScanSlot(slot);
            });
        } catch (RuntimeException e) {
            // 지우지 못해도 응답은 503으로 — 남은 QUEUED 기록은 다음 시작 때 재시작 정리가 FAILED로 바꾼다
            log.error("거절된 점검 기록을 지우지 못했습니다: scanId={}", scanId, e);
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SCAN_QUEUE_FULL", "지금은 점검 요청이 많아요. 잠시 후 다시 요청해 주세요");
    }

    /** 점검을 요청한 등록의 소유자 토큰이 있어야 결과를 볼 수 있다. 없는 점검과 남의 점검은 구분하지 않고 404 */
    @Transactional(readOnly = true)
    public OwnedScan get(UUID scanId, String ownerToken) {
        ApiException notFound = new ApiException(HttpStatus.NOT_FOUND, "SCAN_NOT_FOUND", "점검 기록을 찾을 수 없습니다");
        Scan scan = scans.findByPublicId(scanId).orElse(null);
        DomainClaim claim = domains.authenticate(scan == null ? null : scan.getClaimId(), ownerToken, notFound);
        return new OwnedScan(scan, claim, findings.findByScanId(scan.getId()));
    }
}
