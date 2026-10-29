package dev.codesgy.mundansok.check;

import dev.codesgy.mundansok.net.HttpProbe;
import dev.codesgy.mundansok.net.HttpResult;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** http:// 로 들어온 사용자를 https:// 로 보내는지 */
@Component
public class HttpsRedirectCheck implements Check {

    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);

    private final HttpProbe probe;

    public HttpsRedirectCheck(HttpProbe probe) {
        this.probe = probe;
    }

    @Override
    public String id() {
        return "https-redirect";
    }

    @Override
    public List<CheckFinding> run(String host) {
        HttpResult result;
        try {
            result = probe.get("http://" + host + "/");
        } catch (UncheckedIOException e) {
            return List.of(); // 80 포트를 아예 열지 않은 경우 — 평문 접속 자체가 불가능하므로 문제 아님
        }
        boolean toHttps = REDIRECTS.contains(result.status())
                && result.header("location").map(location -> location.startsWith("https://")).orElse(false);
        if (toHttps) return List.of();
        return List.of(new CheckFinding(id(), Severity.MEDIUM, "HTTP 접속이 HTTPS로 전환되지 않음",
                "http://" + host + "/ 응답 " + result.status() + result.header("location").map(l -> " → " + l).orElse(""),
                "80 포트 요청을 https:// 로 301 리다이렉트하도록 웹 서버를 설정해 주세요"));
    }
}
