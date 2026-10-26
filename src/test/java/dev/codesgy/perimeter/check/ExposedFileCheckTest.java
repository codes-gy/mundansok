package dev.codesgy.perimeter.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codesgy.perimeter.support.FakeHttpProbe;
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
        for (String path : new String[] {"/.git/HEAD", "/.env", "/actuator/env", "/server-status", "/phpinfo.php"}) {
            probe.respond("https://example.com" + path, 200, Map.of(), spa);
        }
        assertThat(check.run("example.com")).isEmpty();
    }

    @Test
    void Actuator_env가_열려_있으면_높음() {
        probe.respond("https://example.com/actuator/env", 200, Map.of(), "{\"activeProfiles\":[],\"propertySources\":[]}");
        assertThat(check.run("example.com")).extracting(CheckFinding::severity).containsExactly(Severity.HIGH);
    }
}
