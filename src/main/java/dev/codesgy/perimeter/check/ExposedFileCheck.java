package dev.codesgy.perimeter.check;

import dev.codesgy.perimeter.net.HttpProbe;
import dev.codesgy.perimeter.net.HttpResult;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 배포 실수로 공개된 파일. 상태 코드만 보지 않고 본문 서명까지 맞아야 노출로 본다
 * (모든 주소에 200과 안내 페이지를 주는 사이트에서 오탐을 막기 위해). 본문 내용은 저장하지 않는다.
 */
@Component
public class ExposedFileCheck implements Check {

    private static final Pattern ENV_LINE = Pattern.compile("(?m)^[A-Z][A-Z0-9_]*=");

    private record Target(String path, Severity severity, String title, Predicate<String> signature, String recommendation) {}

    private static final List<Target> TARGETS = List.of(
            new Target("/.git/HEAD", Severity.HIGH, "Git 저장소 노출",
                    body -> body.startsWith("ref: refs/") || body.matches("(?s)^[0-9a-f]{40}\\s*$"),
                    "웹 루트에서 .git 폴더를 지우거나 웹 서버에서 /.git 경로 접근을 막아 주세요. 소스 코드와 커밋 기록 전체를 내려받을 수 있습니다"),
            new Target("/.env", Severity.HIGH, "환경 변수 파일(.env) 노출",
                    body -> ENV_LINE.matcher(body).find() && !body.contains("<html"),
                    ".env 파일을 웹 루트 밖으로 옮기고, 안에 있던 비밀번호 · API 키는 모두 교체해 주세요"),
            new Target("/actuator/env", Severity.HIGH, "Spring Actuator 환경 정보 노출",
                    body -> body.contains("\"propertySources\""),
                    "management.endpoints.web.exposure.include에서 env를 빼고 Actuator는 인증 뒤나 내부망에서만 열어 주세요"),
            new Target("/server-status", Severity.MEDIUM, "Apache 서버 상태 페이지 노출",
                    body -> body.contains("Apache Server Status"),
                    "mod_status 접근을 내부 IP로 제한(Require ip)해 주세요"),
            new Target("/phpinfo.php", Severity.MEDIUM, "phpinfo 페이지 노출",
                    body -> body.contains("phpinfo()") || body.contains("PHP Version"),
                    "phpinfo.php 파일을 삭제해 주세요. 서버 경로 · 설정 · 확장 모듈 정보가 드러납니다"));

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
        for (Target target : TARGETS) {
            String url = "https://" + host + target.path();
            HttpResult result;
            try {
                result = probe.get(url);
            } catch (UncheckedIOException e) {
                continue;
            }
            if (result.status() == 200 && target.signature().test(result.bodyPrefix())) {
                findings.add(new CheckFinding(id(), target.severity(), target.title(),
                        url + " 가 200으로 응답하고 내용이 실제 파일 형식과 일치합니다", target.recommendation()));
            }
        }
        return findings;
    }
}
