package dev.codesgy.mundansok.check;

import dev.codesgy.mundansok.net.HttpProbe;
import dev.codesgy.mundansok.net.HttpResult;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 배포 실수로 공개된 파일. 상태 코드만 보지 않고 본문 서명까지 맞아야 노출로 본다
 * (모든 주소에 200과 안내 페이지를 주는 사이트에서 오탐을 막기 위해). 본문 내용은 저장하지 않는다.
 * 같은 원인을 가리키는 경로(.git/HEAD · .git/config 등)는 제목을 같게 두고 한 번만 알린다.
 */
@Component
public class ExposedFileCheck implements Check {

    private static final Pattern ENV_LINE = Pattern.compile("(?m)^[A-Z][A-Z0-9_]*=");

    private record Target(String path, Severity severity, String title, Predicate<String> signature, String recommendation) {}

    private static final String GIT_FIX =
            "웹 루트에서 .git 폴더를 지우거나 웹 서버에서 /.git 경로 접근을 막아 주세요. 소스 코드와 커밋 기록 전체를 내려받을 수 있습니다";
    private static final String API_DOCS_FIX =
            "의도한 공개가 아니라면 운영 환경에서 API 문서를 끄거나 (springdoc.api-docs.enabled=false) 인증 뒤로 옮겨 주세요";

    private static final List<Target> TARGETS = List.of(
            new Target("/.git/HEAD", Severity.HIGH, "Git 저장소 노출",
                    body -> body.startsWith("ref: refs/") || body.matches("(?s)^[0-9a-f]{40}\\s*$"), GIT_FIX),
            new Target("/.git/config", Severity.HIGH, "Git 저장소 노출",
                    body -> body.contains("[core]") && body.contains("repositoryformatversion"), GIT_FIX),
            new Target("/.svn/wc.db", Severity.HIGH, "SVN 저장소 노출",
                    body -> body.startsWith("SQLite format 3"),
                    "웹 루트에서 .svn 폴더를 지우거나 /.svn 경로 접근을 막아 주세요. 파일 목록과 소스 코드를 내려받을 수 있습니다"),
            new Target("/.DS_Store", Severity.LOW, "폴더 정보 파일(.DS_Store) 노출",
                    body -> body.startsWith("\u0000\u0000\u0000\u0001Bud1"),
                    ".DS_Store 파일을 지우고 배포에서 빼 주세요. 폴더 안의 파일 이름(백업 파일 등)이 드러납니다"),
            new Target("/.env", Severity.HIGH, "환경 변수 파일(.env) 노출",
                    body -> ENV_LINE.matcher(body).find() && !body.contains("<html"),
                    ".env 파일을 웹 루트 밖으로 옮기고, 안에 있던 비밀번호 · API 키는 모두 교체해 주세요"),
            // 힙 덤프 자체는 요청하지 않는다 (서버가 덤프를 만드느라 멈출 수 있음). 목록에 링크가 있는지만 본다
            new Target("/actuator", Severity.HIGH, "Spring Actuator 힙 덤프 노출",
                    body -> body.contains("\"_links\"") && body.contains("\"heapdump\""),
                    "management.endpoints.web.exposure.include에서 heapdump를 빼 주세요. 메모리에 든 비밀번호 · 세션까지 내려받을 수 있습니다"),
            new Target("/actuator/env", Severity.HIGH, "Spring Actuator 환경 정보 노출",
                    body -> body.contains("\"propertySources\""),
                    "management.endpoints.web.exposure.include에서 env를 빼고 Actuator는 인증 뒤나 내부망에서만 열어 주세요"),
            new Target("/server-status", Severity.MEDIUM, "Apache 서버 상태 페이지 노출",
                    body -> body.contains("Apache Server Status"),
                    "mod_status 접근을 내부 IP로 제한(Require ip)해 주세요"),
            new Target("/phpinfo.php", Severity.MEDIUM, "phpinfo 페이지 노출",
                    body -> body.contains("phpinfo()") || body.contains("PHP Version"),
                    "phpinfo.php 파일을 삭제해 주세요. 서버 경로 · 설정 · 확장 모듈 정보가 드러납니다"),
            new Target("/v3/api-docs", Severity.LOW, "API 문서(OpenAPI) 공개",
                    body -> body.contains("\"openapi\""), API_DOCS_FIX),
            new Target("/swagger-ui/index.html", Severity.LOW, "API 문서(OpenAPI) 공개",
                    body -> body.contains("Swagger UI"), API_DOCS_FIX));

    private final HttpProbe probe;

    public ExposedFileCheck(HttpProbe probe) {
        this.probe = probe;
    }

    @Override
    public String id() {
        return "exposed-files";
    }

    @Override
    public List<CheckFinding> run(String host) {
        List<CheckFinding> findings = new ArrayList<>();
        Set<String> found = new HashSet<>();
        for (Target target : TARGETS) {
            if (found.contains(target.title())) continue;
            String url = "https://" + host + target.path();
            HttpResult result;
            try {
                result = probe.get(url);
            } catch (UncheckedIOException e) {
                continue;
            }
            if (result.status() == 200 && target.signature().test(result.bodyPrefix())) {
                found.add(target.title());
                findings.add(new CheckFinding(id(), target.severity(), target.title(),
                        url + " 가 200으로 응답하고 내용이 실제 파일 형식과 일치합니다", target.recommendation()));
            }
        }
        return findings;
    }
}
