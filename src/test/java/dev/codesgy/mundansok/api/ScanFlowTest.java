package dev.codesgy.mundansok.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.codesgy.mundansok.net.DnsResolver;
import dev.codesgy.mundansok.net.HttpProbe;
import dev.codesgy.mundansok.net.TlsInfo;
import dev.codesgy.mundansok.net.TlsInspector;
import dev.codesgy.mundansok.support.FakeDnsResolver;
import dev.codesgy.mundansok.support.FakeHttpProbe;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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

    @Test
    void 등록_인증_점검_결과까지() throws Exception {
        String registered = register("https://Shop.Example.com/");
        Integer id = JsonPath.read(registered, "$.id");
        assertThat((String) JsonPath.read(registered, "$.name")).isEqualTo("shop.example.com");
        assertThat((String) JsonPath.read(registered, "$.verification.name")).isEqualTo("_mundansok-verify.shop.example.com");

        // TXT 레코드를 넣기 전에는 인증 실패
        mvc.perform(post("/api/domains/{id}/verify", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERIFICATION_RECORD_NOT_FOUND"));

        dns.txt("_mundansok-verify.shop.example.com", JsonPath.<String>read(registered, "$.verification.value"));
        mvc.perform(post("/api/domains/{id}/verify", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        // 점검 대상: HTTPS 전환 O, 보안 헤더 일부 없음, .env 노출, SPF만 있고 DMARC 없음, CAA는 상위 도메인에 있음
        probe.respond("http://shop.example.com/", 301, Map.of("Location", "https://shop.example.com/"), "");
        probe.respond("https://shop.example.com/", 200, Map.of(
                "Strict-Transport-Security", "max-age=31536000",
                "X-Content-Type-Options", "nosniff",
                "Referrer-Policy", "no-referrer"), "<html>");
        probe.respond("https://shop.example.com/.env", 200, Map.of(), "SECRET_KEY=abc\n");
        dns.txt("shop.example.com", "v=spf1 -all");
        dns.caa("example.com", "0 issue \"letsencrypt.org\"");

        String requested = mvc.perform(post("/api/domains/{id}/scans", id))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        Integer scanId = JsonPath.read(requested, "$.id");

        String result = waitUntilFinished(scanId);
        assertThat((String) JsonPath.read(result, "$.status")).isEqualTo("DONE");
        // 높음(.env) 20 + 보통(CSP · X-Frame-Options · DMARC) 30 = 50점 감점
        assertThat((Integer) JsonPath.read(result, "$.score")).isEqualTo(50);
        List<String> titles = JsonPath.read(result, "$.findings[*].title");
        assertThat(titles).first().isEqualTo("환경 변수 파일(.env) 노출");
        assertThat(titles).contains("Content-Security-Policy 헤더 없음", "X-Frame-Options 헤더 없음", "DMARC 레코드 없음");

        // 10분 안에 다시 점검하면 거절
        mvc.perform(post("/api/domains/{id}/scans", id))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SCAN_COOLDOWN"));
    }

    @Test
    void 인증하지_않은_도메인은_점검하지_않는다() throws Exception {
        Integer id = JsonPath.read(register("unverified.example.com"), "$.id");
        mvc.perform(post("/api/domains/{id}/scans", id))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DOMAIN_NOT_VERIFIED"));
    }

    @Test
    void 같은_도메인을_다시_등록하면_같은_토큰을_준다() throws Exception {
        String first = register("same.example.com");
        String second = register("SAME.example.com");
        assertThat((Object) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        assertThat((String) JsonPath.read(second, "$.verification.value")).isEqualTo(JsonPath.read(first, "$.verification.value"));
    }

    @Test
    void 내부_주소는_등록할_수_없다() throws Exception {
        mvc.perform(post("/api/domains").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"http://127.0.0.1/\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOMAIN"));
    }

    private String waitUntilFinished(Integer scanId) throws Exception {
        for (int i = 0; i < 100; i++) {
            String body = mvc.perform(get("/api/scans/{id}", scanId)).andReturn().getResponse().getContentAsString();
            String status = JsonPath.read(body, "$.status");
            if (status.equals("DONE") || status.equals("FAILED")) return body;
            Thread.sleep(50);
        }
        throw new AssertionError("점검이 5초 안에 끝나지 않았습니다");
    }
}
