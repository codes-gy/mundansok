package dev.codesgy.mundansok.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codesgy.mundansok.support.FakeHttpProbe;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExposedFileCheckTest {

    private final FakeHttpProbe probe = new FakeHttpProbe();
    private final ExposedFileCheck check = new ExposedFileCheck(probe);

    @Test
    void 노출된_git과_env를_찾는다() {
        probe.respond("https://example.com/.git/HEAD", 200, Map.of(), "ref: refs/heads/main\n");
        probe.respond("https://example.com/.env", 200, Map.of(), "DB_PASSWORD=secret\nAPI_KEY=abc\n");
        assertThat(check.run("example.com")).extracting(CheckFinding::title)
                .containsExactly("Git 저장소 노출", "환경 변수 파일(.env) 노출");
    }

    @Test
    void 결과에_비밀_값을_담지_않는다() {
        probe.respond("https://example.com/.env", 200, Map.of(), "DB_PASSWORD=secret\n");
        assertThat(check.run("example.com")).allSatisfy(f -> assertThat(f.detail()).doesNotContain("secret"));
    }

    @Test
    void 모든_주소에_200과_안내_페이지를_주는_사이트는_오탐하지_않는다() {
        String spa = "<!doctype html><html><body>Welcome</body></html>";
        for (String path : new String[] {"/.git/HEAD", "/.git/config", "/.svn/wc.db", "/.DS_Store", "/.env",
                "/actuator", "/actuator/env", "/server-status", "/phpinfo.php", "/v3/api-docs", "/swagger-ui/index.html"}) {
            probe.respond("https://example.com" + path, 200, Map.of(), spa);
        }
        assertThat(check.run("example.com")).isEmpty();
    }

    @Test
    void Actuator_env가_열려_있으면_높음() {
        probe.respond("https://example.com/actuator/env", 200, Map.of(), "{\"activeProfiles\":[],\"propertySources\":[]}");
        assertThat(check.run("example.com")).extracting(CheckFinding::severity).containsExactly(Severity.HIGH);
    }

    @Test
    void git_config만_열려_있어도_Git_저장소_노출로_본다() {
        probe.respond("https://example.com/.git/config", 200, Map.of(),
                "[core]\n\trepositoryformatversion = 0\n\tfilemode = true\n");
        assertThat(check.run("example.com")).extracting(CheckFinding::title).containsExactly("Git 저장소 노출");
    }

    @Test
    void 같은_원인은_한_번만_알린다() {
        probe.respond("https://example.com/.git/HEAD", 200, Map.of(), "ref: refs/heads/main\n");
        probe.respond("https://example.com/.git/config", 200, Map.of(), "[core]\n\trepositoryformatversion = 0\n");
        assertThat(check.run("example.com")).extracting(CheckFinding::title).containsExactly("Git 저장소 노출");
    }

    @Test
    void SVN_작업_사본_DB가_열려_있으면_높음() {
        probe.respond("https://example.com/.svn/wc.db", 200, Map.of(), "SQLite format 3\u0000\u0010\u0000");
        assertThat(check.run("example.com")).singleElement().satisfies(f -> {
            assertThat(f.title()).isEqualTo("SVN 저장소 노출");
            assertThat(f.severity()).isEqualTo(Severity.HIGH);
        });
    }

    @Test
    void DS_Store가_열려_있으면_낮음() {
        probe.respond("https://example.com/.DS_Store", 200, Map.of(), "\u0000\u0000\u0000\u0001Bud1\u0000\u0000");
        assertThat(check.run("example.com")).singleElement().satisfies(f -> {
            assertThat(f.title()).isEqualTo("폴더 정보 파일(.DS_Store) 노출");
            assertThat(f.severity()).isEqualTo(Severity.LOW);
        });
    }

    @Test
    void Actuator_목록에_heapdump가_있으면_높음_힙_덤프_자체는_요청하지_않는다() {
        probe.respond("https://example.com/actuator", 200, Map.of(),
                "{\"_links\":{\"self\":{\"href\":\"https://example.com/actuator\"},"
                        + "\"heapdump\":{\"href\":\"https://example.com/actuator/heapdump\"}}}");
        assertThat(check.run("example.com")).singleElement().satisfies(f -> {
            assertThat(f.title()).isEqualTo("Spring Actuator 힙 덤프 노출");
            assertThat(f.severity()).isEqualTo(Severity.HIGH);
        });
        assertThat(probe.requested("https://example.com/actuator/heapdump")).isFalse();
    }

    @Test
    void Actuator_목록에_heapdump가_없으면_알리지_않는다() {
        probe.respond("https://example.com/actuator", 200, Map.of(),
                "{\"_links\":{\"self\":{\"href\":\"https://example.com/actuator\"},"
                        + "\"health\":{\"href\":\"https://example.com/actuator/health\"}}}");
        assertThat(check.run("example.com")).isEmpty();
    }

    @Test
    void API_문서가_열려_있으면_낮음_한_번만() {
        probe.respond("https://example.com/v3/api-docs", 200, Map.of(), "{\"openapi\":\"3.0.1\",\"info\":{}}");
        probe.respond("https://example.com/swagger-ui/index.html", 200, Map.of(),
                "<!DOCTYPE html><html><head><title>Swagger UI</title></head>");
        assertThat(check.run("example.com")).singleElement().satisfies(f -> {
            assertThat(f.title()).isEqualTo("API 문서(OpenAPI) 공개");
            assertThat(f.severity()).isEqualTo(Severity.LOW);
        });
    }
}
