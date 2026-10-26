package dev.codesgy.perimeter.domain;

import dev.codesgy.perimeter.common.ApiException;
import java.net.IDN;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/** 사용자가 넣은 값("https://Example.com/path" 등)을 점검할 호스트 이름("example.com")으로 정리한다 */
public final class DomainNames {

    private static final Pattern LABEL = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private DomainNames() {}

    public static String normalize(String input) {
        if (input == null || input.isBlank()) throw invalid("도메인을 입력해 주세요");
        String host = input.trim().toLowerCase(Locale.ROOT);
        host = host.replaceFirst("^[a-z][a-z0-9+.-]*://", ""); // 스킴
        host = host.replaceFirst("[/?#].*$", ""); // 경로 · 쿼리
        host = host.replaceFirst("^[^@]*@", ""); // 사용자 정보
        host = host.replaceFirst(":\\d+$", ""); // 포트
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.contains(":") || IPV4.matcher(host).matches()) throw invalid("IP 주소가 아닌 도메인을 입력해 주세요");

        String ascii;
        try {
            ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
        } catch (IllegalArgumentException e) {
            throw invalid("도메인 형식이 올바르지 않습니다");
        }
        String[] labels = ascii.split("\\.");
        if (ascii.length() > 253 || labels.length < 2) throw invalid("example.com 같은 전체 도메인을 입력해 주세요");
        for (String label : labels) {
            if (!LABEL.matcher(label).matches()) throw invalid("도메인 형식이 올바르지 않습니다");
        }
        if (labels[labels.length - 1].matches("\\d+")) throw invalid("도메인 형식이 올바르지 않습니다");
        if (ascii.equals("localhost") || ascii.endsWith(".localhost") || ascii.endsWith(".local") || ascii.endsWith(".internal")) {
            throw invalid("내부용 도메인은 점검할 수 없습니다");
        }
        return ascii;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DOMAIN", message);
    }
}
