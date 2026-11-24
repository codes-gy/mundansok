package dev.codesgy.mundansok.ratelimit;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * IP별 요청 제한.
 *
 * @param enabled 끄면 제한하지 않는다
 * @param limits  {@link RateLimited} 키별 제한 (register · verify · scan)
 */
@ConfigurationProperties("mundansok.rate-limit")
public record RateLimitProperties(@DefaultValue("true") boolean enabled, Map<String, Limit> limits) {

    public RateLimitProperties {
        limits = limits == null ? Map.of() : Map.copyOf(limits);
    }

    /**
     * 토큰 버킷 하나. 처음엔 capacity번까지 바로 쓸 수 있고, period 동안 capacity만큼 고르게 다시 찬다.
     *
     * @param capacity 한 번에 몰아 쓸 수 있는 최대 횟수
     * @param period   capacity만큼 다시 차는 데 걸리는 시간
     */
    public record Limit(int capacity, Duration period) {

        public Limit {
            if (capacity <= 0) throw new IllegalArgumentException("capacity는 1 이상이어야 합니다");
            if (period == null || period.isZero() || period.isNegative()) throw new IllegalArgumentException("period는 0보다 커야 합니다");
        }
    }
}
