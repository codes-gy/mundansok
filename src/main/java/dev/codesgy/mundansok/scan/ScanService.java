package dev.codesgy.mundansok.scan;

import dev.codesgy.mundansok.common.ApiException;
import dev.codesgy.mundansok.config.ScanProperties;
import dev.codesgy.mundansok.domain.Domain;
import dev.codesgy.mundansok.domain.DomainService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
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
                try {
                    executor.execute(() -> runner.run(scanId, host));
                } catch (RejectedExecutionException e) {
                    rejected(scanId, domainId);
                }
            }
        });
        return scan;
    }

    /**
     * 점검 풀(스레드 + 대기열)이 가득 차 점검을 넣지 못한 경우.
     * 이미 커밋된 QUEUED 기록은 아무도 돌리지 않으니 지우고, 503으로 돌려보낸다.
     * FAILED로 남기지 않는 이유: 응답이 오류라 화면은 점검 id를 받지 못하고, 남겨 두면 10분 쿨다운에 걸려 "잠시 후 다시"가 거짓말이 된다.
     * 대상에 요청을 하나도 보내지 않았으니 남길 근거도 없다. 대신 로그와 mundansok.scan.rejected 지표로 남긴다.
     */
    private void rejected(Long scanId, Long domainId) {
        log.warn("점검 풀이 가득 차 점검을 받지 못했습니다: scanId={}, domainId={}", scanId, domainId);
        metrics.scanRejected();
        try {
            newTx.executeWithoutResult(status -> scans.deleteById(scanId));
        } catch (RuntimeException e) {
            // 지우지 못해도 응답은 503으로 — 남은 QUEUED 기록은 다음 시작 때 재시작 정리가 FAILED로 바꾼다
            log.error("거절된 점검 기록을 지우지 못했습니다: scanId={}", scanId, e);
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SCAN_QUEUE_FULL", "지금은 점검 요청이 많아요. 잠시 후 다시 요청해 주세요");
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
