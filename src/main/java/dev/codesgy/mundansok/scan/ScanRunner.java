package dev.codesgy.mundansok.scan;

import dev.codesgy.mundansok.check.Check;
import dev.codesgy.mundansok.check.CheckFinding;
import dev.codesgy.mundansok.check.Severity;
import dev.codesgy.mundansok.net.AddressGuard.BlockedAddressException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 점검 하나를 실제로 돌린다. 점검 하나가 실패해도 나머지는 계속 돈다 */
@Component
public class ScanRunner {

    private static final Logger log = LoggerFactory.getLogger(ScanRunner.class);

    private final List<Check> checks;
    private final ScanRepository scans;
    private final FindingRepository findings;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ScanMetrics metrics;

    public ScanRunner(List<Check> checks, ScanRepository scans, FindingRepository findings, TransactionTemplate tx, Clock clock,
            ScanMetrics metrics) {
        this.checks = checks;
        this.scans = scans;
        this.findings = findings;
        this.tx = tx;
        this.clock = clock;
        this.metrics = metrics;
    }

    public void run(Long scanId, String host) {
        tx.executeWithoutResult(status -> scans.findById(scanId).orElseThrow().start());

        List<CheckFinding> results = new ArrayList<>();
        try {
            for (Check check : checks) {
                results.addAll(runOne(check, host));
            }
        } catch (BlockedAddressException e) {
            // 인증 이후 DNS가 내부 주소로 바뀐 경우 — 점검 전체를 멈춘다
            tx.executeWithoutResult(status -> scans.findById(scanId).orElseThrow().fail(e.getMessage(), clock.instant()));
            return;
        }

        tx.executeWithoutResult(status -> {
            results.forEach(result -> findings.save(new Finding(scanId, result)));
            scans.findById(scanId).orElseThrow().finish(Scores.of(results), clock.instant());
        });
    }

    /** 점검 하나를 돌리고 걸린 시간을 결과(ok · failed)별로 남긴다. 내부 주소 차단도 failed로 센다 */
    private List<CheckFinding> runOne(Check check, String host) {
        long started = System.nanoTime();
        boolean ok = false;
        try {
            List<CheckFinding> result = check.run(host);
            ok = true;
            return result;
        } catch (BlockedAddressException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("점검 {} 실패: {}", check.id(), host, e);
            return List.of(new CheckFinding(check.id(), Severity.INFO, "점검을 끝내지 못함",
                    e.getClass().getSimpleName() + ": " + e.getMessage(), "잠시 후 다시 점검해 주세요"));
        } finally {
            metrics.recordCheck(check.id(), ok, Duration.ofNanos(System.nanoTime() - started));
        }
    }
}
