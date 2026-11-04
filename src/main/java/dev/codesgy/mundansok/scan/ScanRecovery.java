package dev.codesgy.mundansok.scan;

import dev.codesgy.mundansok.config.ScanProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 점검은 커밋 뒤 메모리 스레드 풀에서 돌기 때문에, 서버가 재시작되면 돌던 점검은 QUEUED · RUNNING으로 영원히 남는다.
 * 시작할 때 오래된 미완료 점검을 FAILED로 정리한다.
 * 기준 시간(stale-after)보다 최근 것은 건드리지 않는다 — 여러 대로 띄우거나 순차 배포할 때 다른 서버가 돌리고 있을 수 있어서.
 */
@Component
public class ScanRecovery {

    static final String RESTART_REASON = "서버 재시작으로 점검이 중단됨";

    private static final Logger log = LoggerFactory.getLogger(ScanRecovery.class);

    private final ScanRepository scans;
    private final ScanMetrics metrics;
    private final ScanProperties properties;
    private final Clock clock;

    public ScanRecovery(ScanRepository scans, ScanMetrics metrics, ScanProperties properties, Clock clock) {
        this.scans = scans;
        this.metrics = metrics;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public int failInterruptedScans() {
        Instant now = clock.instant();
        Instant before = now.minus(properties.staleAfter());
        int count = scans.failUnfinishedBefore(List.of(ScanStatus.QUEUED, ScanStatus.RUNNING), before,
                ScanStatus.FAILED, RESTART_REASON, now);
        if (count > 0) {
            log.warn("서버 재시작으로 중단된 점검 {}건을 FAILED로 정리했습니다 (기준: {} 이전에 만든 QUEUED · RUNNING)", count, before);
            metrics.restartCleaned(count);
        }
        return count;
    }
}
