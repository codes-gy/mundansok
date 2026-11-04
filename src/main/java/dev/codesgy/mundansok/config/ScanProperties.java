package dev.codesgy.mundansok.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 점검 동작 설정.
 *
 * @param cooldown    같은 도메인을 다시 점검하려면 기다려야 하는 시간
 * @param httpTimeout 점검 요청 하나의 연결 · 응답 제한 시간
 * @param userAgent   점검 요청에 붙이는 User-Agent (점검받는 쪽이 로그에서 알아볼 수 있게)
 * @param staleAfter  서버 시작 시 이보다 오래된 QUEUED · RUNNING 점검은 재시작으로 중단된 것으로 보고 FAILED로 정리한다
 */
@ConfigurationProperties("mundansok.scan")
public record ScanProperties(Duration cooldown, Duration httpTimeout, String userAgent,
        @DefaultValue("15m") Duration staleAfter) {}
