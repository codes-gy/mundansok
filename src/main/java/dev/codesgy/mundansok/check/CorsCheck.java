package dev.codesgy.mundansok.check;

import dev.codesgy.mundansok.net.HttpProbe;
import dev.codesgy.mundansok.net.HttpResult;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 다른 사이트의 스크립트가 이 사이트 응답을 읽을 수 있는지. 있을 수 없는 출처를 Origin 으로 보내고,
 * 그 출처를 그대로 허용하면 아무 사이트나 허용하는 설정으로 본다. 요청은 일반 GET 하나다.
 */
@Component
public class CorsCheck implements Check {

    /** 실제로 존재할 수 없는 출처 (.invalid 는 예약된 최상위 도메인) */
    public static final String PROBE_ORIGIN = "https://mundansok-cors-check.invalid";

    private final HttpProbe probe;

    public CorsCheck(HttpProbe probe) {
        this.probe = probe;
    }

    @Override
    public String id() {
        return "cors";
    }

    @Override
    public List<CheckFinding> run(String host) {
        HttpResult page;
        try {
            page = probe.get("https://" + host + "/", Map.of("Origin", PROBE_ORIGIN));
        } catch (UncheckedIOException e) {
            return List.of(); // HTTPS 연결 문제는 TLS 점검에서 알린다
        }
        if (!page.header("access-control-allow-origin").map(PROBE_ORIGIN::equals).orElse(false)) {
            return List.of();
        }
        boolean credentials = page.header("access-control-allow-credentials").map("true"::equalsIgnoreCase).orElse(false);
        if (credentials) {
            return List.of(new CheckFinding(id(), Severity.HIGH, "CORS가 모든 출처에 로그인 정보까지 허용",
                    "Origin: " + PROBE_ORIGIN + " 요청에 그 출처를 그대로 허용하고 Access-Control-Allow-Credentials: true를 돌려줍니다. "
                            + "아무 사이트나 방문자의 로그인 상태로 이 사이트의 응답을 읽을 수 있어요",
                    "요청의 Origin을 그대로 돌려주지 말고, 허용할 출처 목록과 비교해 맞을 때만 Access-Control-Allow-Origin에 넣어 주세요"));
        }
        return List.of(new CheckFinding(id(), Severity.LOW, "CORS가 모든 출처를 허용",
                "Origin: " + PROBE_ORIGIN + " 요청에 그 출처를 그대로 허용합니다. 로그인 정보 없이 읽을 수 있는 응답은 아무 사이트나 가져갈 수 있어요",
                "공개 API가 아니라면 허용할 출처 목록과 비교해 맞을 때만 허용해 주세요"));
    }
}
