package dev.codesgy.mundansok.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.codesgy.mundansok.support.FakeNetwork;
import dev.codesgy.mundansok.support.TestRows;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 점검 풀(스레드 4 + 대기열 100)이 가득 찼을 때 점검 요청이 QUEUED로 버려지지 않는지 확인한다 */
@SpringBootTest
@Import(FakeNetwork.class)
class ScanRejectionTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    @Qualifier("scanExecutor")
    ThreadPoolTaskExecutor executor;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MeterRegistry registry;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void 대기열이_가득_차면_503을_주고_점검을_남기지_않는다() throws Exception {
        Long domainId = TestRows.verifiedDomain(jdbc, "busy.example.com");
        double rejectedBefore = rejectedCount();

        CountDownLatch release = new CountDownLatch(1);
        int capacity = executor.getMaxPoolSize() + executor.getQueueCapacity();
        try {
            for (int i = 0; i < capacity; i++) {
                executor.execute(() -> {
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            mvc.perform(post("/api/domains/{id}/scans", domainId))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SCAN_QUEUE_FULL"));

            // 대기열에 못 들어간 점검은 QUEUED로 남지 않는다
            Integer left = jdbc.queryForObject("SELECT COUNT(*) FROM scan WHERE domain_id = ?", Integer.class, domainId);
            assertThat(left).isZero();
            assertThat(rejectedCount()).isEqualTo(rejectedBefore + 1);
        } finally {
            release.countDown();
        }

        // 풀이 비면 쿨다운에 걸리지 않고 바로 다시 요청할 수 있다
        waitUntilIdle();
        mvc.perform(post("/api/domains/{id}/scans", domainId))
                .andExpect(status().isAccepted());
    }

    private double rejectedCount() {
        var counter = registry.find("mundansok.scan.rejected").counter();
        return counter == null ? 0 : counter.count();
    }

    private void waitUntilIdle() throws InterruptedException {
        for (int i = 0; i < 200 && (executor.getActiveCount() > 0 || executor.getQueueSize() > 0); i++) {
            Thread.sleep(25);
        }
    }
}
