package dev.codesgy.mundansok.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.codesgy.mundansok.common.ApiException;
import dev.codesgy.mundansok.net.TlsInfo;
import dev.codesgy.mundansok.net.TlsInspector;
import dev.codesgy.mundansok.scan.ScanService;
import dev.codesgy.mundansok.support.FakeDnsResolver;
import dev.codesgy.mundansok.support.FakeHttpProbe;
import dev.codesgy.mundansok.support.TestRows;
import dev.codesgy.mundansok.support.TestRows.TestClaim;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 실제 스프링 컨텍스트 + H2 + Flyway로 등록 → 인증 → 점검 → 결과 흐름을 돌린다. 네트워크는 가짜 구현 */
@SpringBootTest
class ScanFlowTest {

    @TestConfiguration
    static class Fakes {

        @Bean
        @Primary
        FakeDnsResolver fakeDns() {
            return new FakeDnsResolver();
        }

        @Bean
        @Primary
        FakeHttpProbe fakeProbe() {
            return new FakeHttpProbe();
        }

        @Bean
        @Primary
        TlsInspector fakeTls() {
            return host -> new TlsInfo("TLSv1.3", Instant.now().plus(Duration.ofDays(90)), true, null);
        }
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    FakeDnsResolver dns;

    @Autowired
    FakeHttpProbe probe;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ScanService scanService;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        dns.clear();
        probe.clear();
    }

    private String register(String name) throws Exception {
        return mvc.perform(post("/api/domains").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private static MockHttpServletRequestBuilder as(String ownerToken, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + ownerToken);
    }

    /** 등록하고 TXT 레코드를 넣고 인증까지 마친다. [등록 id, 소유자 토큰] */
    private String[] registerAndVerify(String name) throws Exception {
        String registered = register(name);
        String id = JsonPath.read(registered, "$.id");
        String token = JsonPath.read(registered, "$.ownerToken");
        dns.txt(JsonPath.read(registered, "$.verification.name"), JsonPath.<String>read(registered, "$.verification.value"));
        mvc.perform(as(token, post("/api/domains/{id}/verify", id))).andExpect(status().isOk());
        return new String[] {id, token};
    }

    @Test
    void 등록_인증_점검_결과까지() throws Exception {
        String registered = register("https://Shop.Example.com/");
        String id = JsonPath.read(registered, "$.id");
        String token = JsonPath.read(registered, "$.ownerToken");
        assertThat(id).matches("[0-9a-f-]{36}");
        assertThat(token).hasSizeGreaterThanOrEqualTo(43);
        assertThat((String) JsonPath.read(registered, "$.name")).isEqualTo("shop.example.com");
        assertThat((String) JsonPath.read(registered, "$.verification.name")).isEqualTo("_mundansok-verify.shop.example.com");

        // TXT 레코드를 넣기 전에는 인증 실패
        mvc.perform(as(token, post("/api/domains/{id}/verify", id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERIFICATION_RECORD_NOT_FOUND"));

        dns.txt("_mundansok-verify.shop.example.com", JsonPath.<String>read(registered, "$.verification.value"));
        mvc.perform(as(token, post("/api/domains/{id}/verify", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.ownerToken").doesNotExist());

        // 점검 대상: HTTPS 전환 O, 보안 헤더 일부 없음, .env 노출, SPF만 있고 DMARC 없음, CAA는 상위 도메인에 있음
        probe.respond("http://shop.example.com/", 301, Map.of("Location", "https://shop.example.com/"), "");
        probe.respond("https://shop.example.com/", 200, Map.of(
                "Strict-Transport-Security", "max-age=31536000",
                "X-Content-Type-Options", "nosniff",
                "Referrer-Policy", "no-referrer"), "<html>");
        probe.respond("https://shop.example.com/.env", 200, Map.of(), "SECRET_KEY=abc\n");
        dns.txt("shop.example.com", "v=spf1 -all");
        dns.caa("example.com", "0 issue \"letsencrypt.org\"");

        String requested = mvc.perform(as(token, post("/api/domains/{id}/scans", id)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.domainId").value(id))
                .andExpect(jsonPath("$.domain").value("shop.example.com"))
                .andReturn().getResponse().getContentAsString();
        String scanId = JsonPath.read(requested, "$.id");
        assertThat(scanId).matches("[0-9a-f-]{36}");

        String result = waitUntilFinished(scanId, token);
        assertThat((String) JsonPath.read(result, "$.status")).isEqualTo("DONE");
        // 높음(.env) 20 + 보통(CSP · X-Frame-Options · DMARC) 30 = 50점 감점
        assertThat((Integer) JsonPath.read(result, "$.score")).isEqualTo(50);
        List<String> titles = JsonPath.read(result, "$.findings[*].title");
        assertThat(titles).first().isEqualTo("환경 변수 파일(.env) 노출");
        assertThat(titles).contains("Content-Security-Policy 헤더 없음", "X-Frame-Options 헤더 없음", "DMARC 레코드 없음");

        // 10분 안에 다시 점검하면 거절
        mvc.perform(as(token, post("/api/domains/{id}/scans", id)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SCAN_COOLDOWN"));
    }

    @Test
    void 인증하지_않은_도메인은_점검하지_않는다() throws Exception {
        String registered = register("unverified.example.com");
        mvc.perform(as(JsonPath.read(registered, "$.ownerToken"), post("/api/domains/{id}/scans", JsonPath.<String>read(registered, "$.id"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DOMAIN_NOT_VERIFIED"));
    }

    @Test
    void 같은_도메인을_다시_등록해도_기존_토큰과_인증을_넘겨받지_못한다() throws Exception {
        String[] owner = registerAndVerify("same.example.com");
        String ownerTxt = dns.txt("_mundansok-verify.same.example.com").getFirst();

        String other = register("SAME.example.com");
        String otherId = JsonPath.read(other, "$.id");
        String otherToken = JsonPath.read(other, "$.ownerToken");
        assertThat(otherId).isNotEqualTo(owner[0]);
        assertThat(otherToken).isNotEqualTo(owner[1]);
        assertThat((Boolean) JsonPath.read(other, "$.verified")).isFalse();
        assertThat((String) JsonPath.read(other, "$.verification.value")).isNotEqualTo(ownerTxt);

        // 다른 사람의 등록으로는 점검할 수 없고, 주인의 등록에도 접근할 수 없다
        mvc.perform(as(otherToken, post("/api/domains/{id}/scans", otherId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DOMAIN_NOT_VERIFIED"));
        mvc.perform(as(otherToken, post("/api/domains/{id}/scans", owner[0])))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOMAIN_NOT_FOUND"));
        mvc.perform(as(otherToken, get("/api/domains/{id}", owner[0])))
                .andExpect(status().isNotFound());
    }

    @Test
    void 결과는_요청한_등록의_토큰으로만_볼_수_있다() throws Exception {
        String[] owner = registerAndVerify("idor.example.com");
        String scanId = JsonPath.read(mvc.perform(as(owner[1], post("/api/domains/{id}/scans", owner[0])))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/scans/{id}", scanId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OWNER_TOKEN_REQUIRED"));
        String stranger = JsonPath.read(register("stranger.example.com"), "$.ownerToken");
        mvc.perform(as(stranger, get("/api/scans/{id}", scanId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"));
        mvc.perform(as(owner[1], get("/api/scans/{id}", scanId)))
                .andExpect(status().isOk());

        // 예전처럼 순번 id로는 찾을 수 없다
        mvc.perform(as(owner[1], get("/api/scans/{id}", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 토큰_없이는_인증도_조회도_할_수_없다() throws Exception {
        String id = JsonPath.read(register("token.example.com"), "$.id");
        mvc.perform(post("/api/domains/{id}/verify", id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OWNER_TOKEN_REQUIRED"));
        mvc.perform(get("/api/domains/{id}", id))
                .andExpect(status().isUnauthorized());
        mvc.perform(as("wrong-token", post("/api/domains/{id}/verify", id)))
                .andExpect(status().isNotFound());
    }

    @Test
    void TXT_레코드를_지우면_다시_점검할_수_없다() throws Exception {
        String[] owner = registerAndVerify("expired.example.com");
        dns.clear(); // 도메인 주인이 바뀌었거나 레코드를 지움

        mvc.perform(as(owner[1], post("/api/domains/{id}/scans", owner[0])))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
        Integer scans = jdbc.queryForObject(
                "SELECT COUNT(*) FROM scan s JOIN domain d ON d.id = s.domain_id WHERE d.name = 'expired.example.com'", Integer.class);
        assertThat(scans).isZero();
    }

    @Test
    void 동시에_점검을_요청해도_쿨다운_안에서는_하나만_통과한다() throws Exception {
        TestClaim claim = TestRows.verifiedClaim(jdbc, "race.example.com");
        dns.txt(claim.txtName(), claim.txtValue());

        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<String> call = () -> {
                    start.await();
                    try {
                        scanService.request(claim.publicId(), claim.ownerToken());
                        return "OK";
                    } catch (ApiException e) {
                        return e.code();
                    }
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) outcomes.add(result.get());

            assertThat(outcomes).filteredOn("OK"::equals).hasSize(1);
            assertThat(outcomes).filteredOn("SCAN_COOLDOWN"::equals).hasSize(threads - 1);
            Integer scans = jdbc.queryForObject("SELECT COUNT(*) FROM scan WHERE domain_id = ?", Integer.class, claim.domainId());
            assertThat(scans).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 같은_IP에서_등록이_너무_많으면_429() throws Exception {
        String body = "{\"name\":\"flood.example.com\"}";
        int accepted = 0;
        for (int i = 0; i < 50; i++) {
            int status = mvc.perform(post("/api/domains").contentType(MediaType.APPLICATION_JSON).content(body).with(remote("198.51.100.23")))
                    .andReturn().getResponse().getStatus();
            if (status == 429) break;
            assertThat(status).isEqualTo(201);
            accepted++;
        }
        assertThat(accepted).isEqualTo(20); // application.yaml 의 register.capacity

        mvc.perform(post("/api/domains").contentType(MediaType.APPLICATION_JSON).content(body).with(remote("198.51.100.23")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
        // 다른 IP는 영향을 받지 않는다
        mvc.perform(post("/api/domains").contentType(MediaType.APPLICATION_JSON).content(body).with(remote("198.51.100.24")))
                .andExpect(status().isCreated());
    }

    @Test
    void 내부_주소는_등록할_수_없다() throws Exception {
        mvc.perform(post("/api/domains").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"http://127.0.0.1/\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOMAIN"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor remote(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private String waitUntilFinished(String scanId, String token) throws Exception {
        for (int i = 0; i < 100; i++) {
            String body = mvc.perform(as(token, get("/api/scans/{id}", scanId))).andReturn().getResponse().getContentAsString();
            String status = JsonPath.read(body, "$.status");
            if (status.equals("DONE") || status.equals("FAILED")) return body;
            Thread.sleep(50);
        }
        throw new AssertionError("점검이 5초 안에 끝나지 않았습니다");
    }
}
