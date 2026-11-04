package dev.codesgy.mundansok.scan;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 점검 운영 지표. 이름은 모두 mundansok.로 시작한다 (Prometheus에서는 점이 밑줄로 바뀐다).
 * 점검 풀(scanExecutor)의 active · queued는 Spring Boot가 executor.* 지표로 따로 붙인다.
 */
@Component
public class ScanMetrics {

    static final String CHECK_DURATION = "mundansok.check.duration";
    static final String SCAN_REJECTED = "mundansok.scan.rejected";
    static final String RESTART_CLEANED = "mundansok.scan.restart.cleaned";

    private final MeterRegistry registry;
    private final Counter rejected;
    private final Counter restartCleaned;

    public ScanMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.rejected = Counter.builder(SCAN_REJECTED)
                .description("점검 풀이 가득 차 받지 못한 점검 요청 수")
                .register(registry);
        this.restartCleaned = Counter.builder(RESTART_CLEANED)
                .description("서버 재시작으로 중단돼 시작 시 FAILED로 정리한 점검 수")
                .register(registry);
    }

    /** 점검 하나(check)에 걸린 시간. outcome은 ok(정상 종료) · failed(예외로 끝나지 못함) */
    void recordCheck(String checkId, boolean ok, Duration took) {
        Timer.builder(CHECK_DURATION)
                .description("점검 항목 하나를 실행하는 데 걸린 시간")
                .tag("check", checkId)
                .tag("outcome", ok ? "ok" : "failed")
                .register(registry)
                .record(took);
    }

    void scanRejected() {
        rejected.increment();
    }

    void restartCleaned(int count) {
        restartCleaned.increment(count);
    }
}
