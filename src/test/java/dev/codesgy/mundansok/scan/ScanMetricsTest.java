package dev.codesgy.mundansok.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.codesgy.mundansok.check.Check;
import dev.codesgy.mundansok.check.CheckFinding;
import dev.codesgy.mundansok.support.FakeDnsResolver;
import dev.codesgy.mundansok.support.FakeHttpProbe;
import dev.codesgy.mundansok.support.FakeNetwork;
import dev.codesgy.mundansok.support.TestRows;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 점검별 타이머 태그와 /actuator/prometheus 노출을 확인한다 */
@SpringBootTest
@AutoConfigureMetrics
@Import(FakeNetwork.class)
class ScanMetricsTest {

    /** 늘 예외로 끝나는 점검 — outcome=failed 태그 확인용 */
    @TestConfiguration
    static class BrokenCheck {

        @Bean
        Check brokenCheck() {
            return new Check() {
                @Override
                public String id() {
                    return "broken";
                }

                @Override
                public List<CheckFinding> run(String host) {
                    throw new IllegalStateException("일부러 실패");
                }
            };
        }
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    ScanRunner runner;

    @Autowired
    FakeDnsResolver dns;

    @Autowired
    FakeHttpProbe probe;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MeterRegistry registry;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        dns.clear();
        probe.clear();
    }

    @Test
    void 점검마다_check와_outcome_태그로_시간을_잰다() {
        Long domainId = TestRows.verifiedDomain(jdbc, "metrics.example.com");
        Long scanId = TestRows.scan(jdbc, domainId, "QUEUED", Instant.now());
        double brokenFailed = count("broken", "failed");
        double tlsOk = count("tls", "ok");
        double headersOk = count("security-headers", "ok");

        runner.run(scanId, "metrics.example.com");

        assertThat(count("broken", "failed")).isEqualTo(brokenFailed + 1);
        assertThat(count("broken", "ok")).isZero();
        assertThat(count("tls", "ok")).isEqualTo(tlsOk + 1);
        assertThat(count("security-headers", "ok")).isEqualTo(headersOk + 1);
        // 점검 하나가 예외로 끝나도 점검 전체는 DONE
        assertThat(jdbc.queryForObject("SELECT status FROM scan WHERE id = ?", String.class, scanId)).isEqualTo("DONE");
    }

    @Test
    void prometheus로_점검_지표와_점검_풀_지표를_내보낸다() throws Exception {
        Long domainId = TestRows.verifiedDomain(jdbc, "scrape.example.com");
        runner.run(TestRows.scan(jdbc, domainId, "QUEUED", Instant.now()), "scrape.example.com");

        String body = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("mundansok_check_duration_seconds_count{check=\"tls\",outcome=\"ok\"")
                .contains("mundansok_scan_rejected_total")
                .contains("mundansok_scan_restart_cleaned_total")
                .contains("executor_active_threads{name=\"scanExecutor\"")
                .contains("executor_queued_tasks{name=\"scanExecutor\"")
                .contains("executor_queue_remaining_tasks{name=\"scanExecutor\"");
    }

    @Test
    void health만_열고_다른_actuator는_닫는다() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isNotFound());
    }

    private double count(String check, String outcome) {
        Timer timer = registry.find("mundansok.check.duration").tag("check", check).tag("outcome", outcome).timer();
        return timer == null ? 0 : timer.count();
    }
}
