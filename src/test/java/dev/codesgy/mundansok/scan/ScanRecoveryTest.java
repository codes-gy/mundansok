package dev.codesgy.mundansok.scan;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codesgy.mundansok.support.FakeNetwork;
import dev.codesgy.mundansok.support.TestRows;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 서버 시작(ApplicationReadyEvent) 때 재시작으로 멈춘 점검을 FAILED로 정리하는지 */
@SpringBootTest
@Import(FakeNetwork.class)
class ScanRecoveryTest {

    @Autowired
    ConfigurableApplicationContext context;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MeterRegistry registry;

    @Test
    void 오래된_QUEUED_RUNNING만_FAILED로_바꾼다() {
        Instant now = Instant.now();
        Long domainId = TestRows.verifiedDomain(jdbc, "restart.example.com");
        Long oldRunning = TestRows.scan(jdbc, domainId, "RUNNING", now.minus(Duration.ofHours(2)));
        Long oldQueued = TestRows.scan(jdbc, domainId, "QUEUED", now.minus(Duration.ofMinutes(16)));
        Long recentRunning = TestRows.scan(jdbc, domainId, "RUNNING", now.minus(Duration.ofMinutes(1)));
        Long recentQueued = TestRows.scan(jdbc, domainId, "QUEUED", now.minus(Duration.ofMinutes(14)));
        Long oldDone = TestRows.scan(jdbc, domainId, "DONE", now.minus(Duration.ofHours(3)));
        double cleanedBefore = registry.get("mundansok.scan.restart.cleaned").counter().count();

        // 실제 시작 때와 같은 이벤트를 다시 보낸다
        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));

        for (Long id : new Long[] {oldRunning, oldQueued}) {
            Map<String, Object> row = row(id);
            assertThat(row.get("status")).isEqualTo("FAILED");
            assertThat(row.get("error")).isEqualTo("서버 재시작으로 점검이 중단됨");
            assertThat(row.get("finished_at")).isNotNull();
        }
        assertThat(row(recentRunning).get("status")).isEqualTo("RUNNING");
        assertThat(row(recentQueued).get("status")).isEqualTo("QUEUED");
        assertThat(row(recentRunning).get("finished_at")).isNull();
        assertThat(row(oldDone).get("status")).isEqualTo("DONE");
        assertThat(row(oldDone).get("error")).isNull();
        assertThat(registry.get("mundansok.scan.restart.cleaned").counter().count()).isEqualTo(cleanedBefore + 2);
    }

    private Map<String, Object> row(Long id) {
        return jdbc.queryForMap("SELECT status, error, finished_at FROM scan WHERE id = ?", id);
    }
}
